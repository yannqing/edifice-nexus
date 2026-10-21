#!/usr/bin/env python3
"""Isolated streaming 5GiB storage check. Does not create business records."""
import argparse
import concurrent.futures
import hashlib
import json
import os
import secrets
import subprocess
import threading
import time

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError


def client(root=False):
    return boto3.client("s3", endpoint_url=os.environ["MINIO_ENDPOINT"], region_name="us-east-1",
                        aws_access_key_id=os.environ["MINIO_ROOT_USER" if root else "MINIO_ACCESS_KEY"],
                        aws_secret_access_key=os.environ["MINIO_ROOT_PASSWORD" if root else "MINIO_SECRET_KEY"],
                        config=Config(s3={"addressing_style": "path"}, max_pool_connections=4))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--size-mib", type=int, default=5120)
    args = parser.parse_args()
    assert 5 <= args.size_mib <= 5120
    total_size = args.size_mib * 1024 * 1024
    part_count = (total_size + 5 * 1024 * 1024 - 1) // (5 * 1024 * 1024)
    s3, admin = client(), client(True)
    bucket = os.environ["MINIO_BUCKET"]
    key = "_verification/" + secrets.token_hex(16)
    upload = s3.create_multipart_upload(Bucket=bucket, Key=key)["UploadId"]
    samples, done, versions = [], threading.Event(), []
    pid = int(subprocess.check_output(["systemctl", "show", "minio-edifice", "-p", "MainPID", "--value"]))

    def monitor():
        while not done.wait(0.1):
            with open(f"/proc/{pid}/status") as source:
                rss = next(int(line.split()[1]) for line in source if line.startswith("VmRSS:"))
            with open("/proc/meminfo") as source:
                available = next(int(line.split()[1]) for line in source if line.startswith("MemAvailable:"))
            samples.append((rss, available))

    thread = threading.Thread(target=monitor, daemon=True)
    thread.start()
    started = time.monotonic()
    try:
        block = os.urandom(5 * 1024 * 1024)
        expected = hashlib.sha256()
        for number in range(1, part_count + 1):
            expected.update(block[:min(len(block), total_size - (number - 1) * len(block))])

        def put(number):
            data = block[:min(len(block), total_size - (number - 1) * len(block))]
            result = s3.upload_part(Bucket=bucket, Key=key, UploadId=upload, PartNumber=number, Body=data)
            return {"PartNumber": number, "ETag": result["ETag"]}

        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
            parts = list(executor.map(put, range(1, part_count + 1)))
        result = s3.complete_multipart_upload(Bucket=bucket, Key=key, UploadId=upload, MultipartUpload={"Parts": parts})
        versions.append(result.get("VersionId"))
        actual, size = hashlib.sha256(), 0
        with s3.get_object(Bucket=bucket, Key=key)["Body"] as stream:
            while chunk := stream.read(1024 * 1024):
                actual.update(chunk)
                size += len(chunk)
        assert size == total_size and expected.hexdigest() == actual.hexdigest()
        with s3.get_object(Bucket=bucket, Key=key, Range="bytes=11-110")["Body"] as stream:
            assert stream.read() == block[11:111]
        try:
            s3.delete_object(Bucket=bucket, Key=key)
            raise AssertionError("Application must not have DeleteObject privilege")
        except ClientError as error:
            assert error.response["ResponseMetadata"]["HTTPStatusCode"] == 403
        print(json.dumps({"bytes": size, "sha256": actual.hexdigest(), "elapsedSeconds": round(time.monotonic()-started, 2),
                          "peakRssMiB": round(max(x[0] for x in samples) / 1024, 1),
                          "minimumAvailableMiB": round(min(x[1] for x in samples) / 1024, 1),
                          "range": "passed", "applicationDeleteDenied": True}))
    finally:
        done.set(); thread.join()
        if versions:
            for version in versions:
                admin.delete_object(Bucket=bucket, Key=key, **({"VersionId": version} if version else {}))
        else:
            admin.abort_multipart_upload(Bucket=bucket, Key=key, UploadId=upload)


if __name__ == "__main__":
    main()
