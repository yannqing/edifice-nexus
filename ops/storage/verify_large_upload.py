#!/usr/bin/env python3
"""Root-only streaming upload/hash/download verification; cleans up only its own UUID objects.

Load the existing production runtime variables before invoking. No project or
approval is submitted. Defaults to local HTTPS through the real Nginx vhost.
"""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import secrets
import ssl
import subprocess
import threading
import time
import uuid
from urllib.parse import urlsplit
from verify_upload_api import sql, redis, token
from verify_storage import client


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--size-mib', type=int, default=5120)
    parser.add_argument('--public', action='store_true')
    parser.add_argument('--report-dir', type=Path, required=True)
    args = parser.parse_args()
    assert 1 <= args.size_mib <= 5120
    args.report_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    host = 'nxrex-edifice.yannqing.com'
    connection = (lambda: http.client.HTTPSConnection(host, timeout=900)) if args.public else (
        lambda: http.client.HTTPSConnection('127.0.0.1', timeout=900, context=ssl._create_unverified_context()))
    # Local HTTPS deliberately connects to loopback; the public mode validates the actual certificate.
    user = json.loads(sql("SELECT JSON_OBJECT('userId',CAST(user_id AS CHAR),'username',username) FROM sys_user "
                          "WHERE is_delete=0 AND status=1 AND username='admin' LIMIT 1"))
    access = token(user)
    upload_id, file_id = None, None
    started = time.monotonic()
    samples, done = [], threading.Event()
    pids = {service: int(subprocess.check_output(['systemctl','show',service,'-p','MainPID','--value']))
            for service in ['edifice-prod-core','minio-edifice']}
    def monitor():
        while not done.wait(.2):
            rss = {}
            for name, pid in pids.items():
                with open(f'/proc/{pid}/status') as source:
                    rss[name] = next(int(line.split()[1]) for line in source if line.startswith('VmRSS:'))
            with open('/proc/meminfo') as source:
                rss['available'] = next(int(line.split()[1]) for line in source if line.startswith('MemAvailable:'))
            samples.append(rss)
    thread = threading.Thread(target=monitor, daemon=True); thread.start()
    def call(path, body=None, method='GET', headers=None):
        conn = connection()
        conn.request(method, '/api'+path, body=body, headers={'Host':host,'token':access, **(headers or {})})
        result = conn.getresponse()
        return conn, result
    def api(path, body=None, method='GET'):
        conn, response = call(path, None if body is None else json.dumps(body).encode(), method, {'Content-Type':'application/json'})
        try: return json.load(response)
        finally: conn.close()
    def ok(result):
        assert result['code']==200, result.get('msg'); return result['data']
    try:
        assert api('/file/uploads', {'fileName':'oversize.pdf','totalSize':5*1024**3+1,'purpose':'project'}, 'POST')['code'] != 200
        size = args.size_mib * 1024**2
        session=ok(api('/file/uploads', {'fileName':'__5gb_verification_'+secrets.token_hex(10)+'.pdf','totalSize':size,'purpose':'project'}, 'POST'))
        upload_id=session['uploadId']; uuid.UUID(upload_id)
        assert session['totalSize']==size
        block=os.urandom(session['chunkSize']); expected=hashlib.sha256()
        for part in range(1,session['totalParts']+1):
            data=block[:min(len(block),size-(part-1)*len(block))]; expected.update(data)
            boundary='edifice-'+secrets.token_hex(10)
            body=(f'--{boundary}\r\nContent-Disposition: form-data; name="chunk"; filename="part"\r\n'
                  'Content-Type: application/octet-stream\r\n\r\n').encode()+data+f'\r\n--{boundary}--\r\n'.encode()
            conn,response=call(f'/file/uploads/{upload_id}/parts/{part}',body,'POST',{'Content-Type':'multipart/form-data; boundary='+boundary})
            try: ok(json.load(response))
            finally: conn.close()
            if part%128==0: print(json.dumps({'uploadedParts':part,'totalParts':session['totalParts']}),flush=True)
        verify_start=time.monotonic()
        state=ok(api(f'/file/uploads/{upload_id}/complete-async',method='POST'))
        complete_latency=time.monotonic()-verify_start
        assert complete_latency < 20, 'Completion initiation must not hold the gateway request'
        while state['status']!='completed':
            assert state['status'] in ['verifying','uploading']
            assert time.monotonic()-verify_start<900
            time.sleep(1)
            state=ok(api(f'/file/uploads/{upload_id}'))
        file_id=str(state['file']['fileId']); assert file_id.isdigit()
        assert int(state['file']['fileSize'])==size
        conn,response=call('/file/download/'+file_id,method='HEAD')
        try: assert response.status==200 and int(response.getheader('Content-Length'))==size
        finally: conn.close()
        offset=size-100
        conn,response=call('/file/download/'+file_id,headers={'Range':f'bytes={offset}-{size-1}'})
        try:
            assert response.status==206 and response.getheader('Content-Range')==f'bytes {offset}-{size-1}/{size}'
            assert response.read()==(block*2)[offset%len(block):offset%len(block)+100]
        finally: conn.close()
        actual=hashlib.sha256(); received=0
        conn,response=call('/file/download/'+file_id)
        try:
            assert response.status==200
            while data:=response.read(1024*1024): actual.update(data); received+=len(data)
        finally: conn.close()
        assert received==size and actual.digest()==expected.digest()
        row=json.loads(sql(f"SELECT JSON_OBJECT('sha256',file_hash,'size',file_size) FROM files WHERE file_id={file_id}"))
        assert row['sha256']==actual.hexdigest() and row['size']==size
        report={'bytes':size,'parts':session['totalParts'],'path':'public-Cloudflare' if args.public else 'HTTPS-Nginx-Spring-MinIO',
                'sha256':actual.hexdigest(),'completeRequestSeconds':round(complete_latency,3),
                'elapsedSeconds':round(time.monotonic()-started,2),'head':'passed','tailRange':'passed',
                'peakRssMiB':{name:round(max(s[name] for s in samples)/1024,1) for name in pids},
                'minimumAvailableMiB':round(min(s['available'] for s in samples)/1024,1)}
        (args.report_dir/('large-upload-public.json' if args.public else 'large-upload-origin.json')).write_text(json.dumps(report))
        print(json.dumps(report),flush=True)
    finally:
        done.set(); thread.join(); redis('DEL','access_token:'+access)
        if upload_id:
            row=json.loads(sql("SELECT JSON_OBJECT('key',object_key,'bucket',storage_bucket,'id',CAST(file_id AS CHAR)) "
                               f"FROM file_upload_session WHERE upload_id='{upload_id}'"))
            assert upload_id in row['key'] and row['key'].startswith('objects/')
            (args.report_dir/(upload_id+'-test-cleanup.json')).write_text(json.dumps(row))
            admin=client(True)
            try:
                for page in admin.get_paginator('list_object_versions').paginate(Bucket=row['bucket'],Prefix=row['key']):
                    for version in page.get('Versions',[])+page.get('DeleteMarkers',[]):
                        if version['Key']==row['key']: admin.delete_object(Bucket=row['bucket'],Key=row['key'],VersionId=version['VersionId'])
                multipart=sql(f"SELECT multipart_id FROM file_upload_session WHERE upload_id='{upload_id}'")
                try: admin.abort_multipart_upload(Bucket=row['bucket'],Key=row['key'],UploadId=multipart)
                except Exception as error:
                    if getattr(error,'response',{}).get('Error',{}).get('Code')!='NoSuchUpload': raise
                if row['id'] and row['id']!='None':
                    assert str(row['id']).isdigit()
                    assert sql(f"SELECT display_name FROM files WHERE file_id={row['id']}").startswith('__5gb_verification_')
                    sql(f"DELETE FROM files WHERE file_id={row['id']} AND display_name LIKE '__5gb_verification_%'")
                sql(f"DELETE FROM file_upload_part WHERE upload_id='{upload_id}'; DELETE FROM file_upload_session WHERE upload_id='{upload_id}';")
                print('Isolated verification object/session cleaned; no project or approval changed',flush=True)
            except Exception:
                print('Verification cleanup requires inspection: '+upload_id,flush=True); raise

if __name__=='__main__': main()
