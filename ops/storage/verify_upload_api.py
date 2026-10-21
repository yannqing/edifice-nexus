#!/usr/bin/env python3
"""Root-only localhost upload smoke test with short-lived, isolated test tokens.

Creates unlinked verification files only. Business projects/approvals are never
submitted. Test tokens are removed in finally; file IDs are printed for cleanup.
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
import secrets
import subprocess
import time
import urllib.request


def sql(query):
    command = ["mysql", "-N", "-B", "-h", os.environ["MYSQL_HOST"], "-P", os.environ["MYSQL_PORT"],
               "-u", os.environ["MYSQL_USERNAME"], os.environ["MYSQL_DATABASE"], "-e", query]
    env = {**os.environ, "MYSQL_PWD": os.environ["MYSQL_PASSWORD"]}
    return subprocess.check_output(command, env=env, text=True).strip()


def redis(*command):
    args = ["docker", "exec", "-i", "-e", "REDISCLI_AUTH=" + os.environ.get("REDIS_PASSWORD", ""),
            "redis", "redis-cli", "-n", os.environ.get("REDIS_DATABASE", "1"), *command]
    return subprocess.check_output(args, text=True).strip()


def token(user):
    encode = lambda value: base64.urlsafe_b64encode(value).rstrip(b"=").decode()
    header = encode(b'{"alg":"HS256","typ":"JWT"}')
    payload = encode(json.dumps({"userInfo": json.dumps(user), "roles": "[]", "type": "access",
                                "jti": "storage-verification-" + secrets.token_hex(16), "exp": int(time.time()) + 900}).encode())
    data = header + "." + payload
    signature = encode(hmac.new(os.environ["JWT_SECRET"].encode(), data.encode(), hashlib.sha256).digest())
    value = data + "." + signature
    assert redis("SET", "access_token:" + value, '"storage-verification"', "EX", "900") == "OK"
    return value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", choices=["8181", "8281"], default="8181")
    args = parser.parse_args()
    base = "http://127.0.0.1:" + args.port
    users = [json.loads(line) for line in sql("SELECT JSON_OBJECT('userId',CAST(user_id AS CHAR),'username',username) "
            "FROM sys_user WHERE is_delete=0 AND status=1 ORDER BY (username='admin') DESC,user_id LIMIT 2").splitlines()]
    assert len(users) == 2 and users[0]["username"] == "admin", "An existing active admin is required"
    tokens = []
    sessions, test_files = [], []
    try:
        tokens.extend(token(user) for user in users)

        def call(path, body=None, method="GET", headers=None, other=False):
            request = urllib.request.Request(base + path, data=body, method=method,
                      headers={"token": tokens[1 if other else 0], **(headers or {})})
            return urllib.request.urlopen(request, timeout=120)

        def api(path, body=None, method="GET", other=False):
            with call(path, None if body is None else json.dumps(body).encode(), method,
                      {"Content-Type": "application/json"}, other) as response:
                return json.load(response)

        def success(response):
            assert response["code"] == 200, response.get("msg")
            return response["data"]

        assert api("/file/uploads", {"fileName": "too-large.pdf", "totalSize": 5*1024**3+1, "purpose": "project"}, "POST")["code"] != 200
        body = os.urandom(6 * 1024**2 + 7)
        session = success(api("/file/uploads", {"fileName": "__storage_verification_" + secrets.token_hex(8) + ".pdf",
                         "totalSize": len(body), "purpose": "project"}, "POST"))
        upload_id = session["uploadId"]; sessions.append(upload_id)
        assert api("/file/uploads/" + upload_id, other=True)["code"] != 200

        def part(number, data):
            boundary = "edifice-verification-" + secrets.token_hex(12)
            payload = (f'--{boundary}\r\nContent-Disposition: form-data; name="chunk"; filename="part"\r\n'
                       'Content-Type: application/octet-stream\r\n\r\n').encode() + data + f"\r\n--{boundary}--\r\n".encode()
            with call(f"/file/uploads/{upload_id}/parts/{number}", payload, "POST",
                      {"Content-Type": "multipart/form-data; boundary=" + boundary}) as response:
                return json.load(response)

        assert part(1, b"bad-size")["code"] != 200
        first = body[:session["chunkSize"]]
        success(part(1, first)); success(part(1, first))
        resumed = success(api("/file/uploads/" + upload_id))
        assert resumed["uploadedParts"] == [1] and resumed["partHashes"]["1"] == hashlib.sha256(first).hexdigest()
        assert part(1, bytes([first[0] ^ 1]) + first[1:])["code"] != 200
        assert api(f"/file/uploads/{upload_id}/complete", method="POST")["code"] != 200
        success(part(2, body[session["chunkSize"]:]))
        file = success(api(f"/file/uploads/{upload_id}/complete", method="POST"))["file"]
        test_files.append(file["fileId"])
        assert success(api(f"/file/uploads/{upload_id}/complete", method="POST"))["file"]["fileId"] == file["fileId"]
        with call("/file/download/" + file["fileId"], method="HEAD") as response:
            assert int(response.headers["Content-Length"]) == len(body)
        with call("/file/download/" + file["fileId"], headers={"Range": "bytes=7-106"}) as response:
            assert response.status == 206 and response.read() == body[7:107]
        with call("/file/download/" + file["fileId"]) as response:
            actual = hashlib.sha256()
            while chunk := response.read(1024 * 1024):
                actual.update(chunk)
            assert actual.digest() == hashlib.sha256(body).digest()
        cancelled = success(api("/file/uploads", {"fileName": "__storage_verification_cancel.pdf", "totalSize": 1, "purpose": "project"}, "POST"))
        sessions.append(cancelled["uploadId"])
        success(api("/file/uploads/" + cancelled["uploadId"], method="DELETE"))
        assert success(api("/file/uploads/" + cancelled["uploadId"]))["status"] == "cancelled"
        legacy = json.loads(sql("SELECT JSON_OBJECT('fileId',CAST(file_id AS CHAR),'path',file_path) FROM files "
                               "WHERE is_deleted=0 AND file_size>=10 AND display_name NOT LIKE '__storage_verification_%' ORDER BY file_size LIMIT 1"))
        for path in ("/file/download/" + legacy["fileId"], legacy["path"]):
            with call(path, headers={"Range": "bytes=0-9"}) as response:
                assert response.status == 206 and len(response.read()) == 10
        with call(legacy["path"], headers={"token": "", "Cookie": "access_token=" + tokens[0], "Range": "bytes=0-9"}) as response:
            assert response.status == 206 and len(response.read()) == 10
        print(json.dumps({"status": "passed", "port": args.port, "checks": ["5GiB limit", "cross-user ownership",
              "part size", "part hash", "resume", "idempotent part", "idempotent completion", "cancel", "HEAD", "range", "download hash", "legacy paths"],
              "verificationFileIds": test_files, "verificationUploadIds": sessions}))
    finally:
        for value in tokens:
            redis("DEL", "access_token:" + value)
        if test_files:
            print(json.dumps({"verificationFilesForCleanup": test_files, "verificationSessionsForCleanup": sessions}))


if __name__ == "__main__":
    main()
