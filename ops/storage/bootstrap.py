#!/usr/bin/env python3
"""Create runtime-only credentials and a private, least-privilege bucket."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.request
import boto3
from botocore.config import Config


def write_private(path, values):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        return dict(line.split("=", 1) for line in path.read_text().splitlines() if line and not line.startswith("#"))
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, "w") as stream:
        stream.write("".join(f"{key}={value}\n" for key, value in values.items()))
    return values


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=["credentials", "bucket"])
    args = parser.parse_args()
    root = write_private(Path("/etc/minio-edifice.env"), {
        "MINIO_ROOT_USER": "edifice-root-" + secrets.token_hex(6),
        "MINIO_ROOT_PASSWORD": secrets.token_hex(32),
        "MINIO_API_REQUESTS_MAX": "8", "GOMEMLIMIT": "384MiB", "GOGC": "50",
        "MINIO_BROWSER": "off", "MINIO_UPDATE": "off",
    })
    app = write_private(Path("/etc/edifice/storage.env"), {
        "MINIO_ACCESS_KEY": "edifice-app-" + secrets.token_hex(6),
        "MINIO_SECRET_KEY": secrets.token_hex(32),
        "MINIO_ENDPOINT": "http://127.0.0.1:9000", "MINIO_BUCKET": "edifice-prod",
        "FILE_STORAGE_TYPE": "minio",
    })
    if args.phase == "credentials":
        print("Private runtime credentials ready; no application restart performed")
        return
    for attempt in range(60):
        try:
            with urllib.request.urlopen("http://127.0.0.1:9000/minio/health/ready", timeout=2) as response:
                if response.status == 200:
                    break
        except Exception:
            time.sleep(1)
    else:
        raise RuntimeError("MinIO did not become ready")
    env = {**os.environ, "MC_HOST_edifice": f"http://{root['MINIO_ROOT_USER']}:{root['MINIO_ROOT_PASSWORD']}@127.0.0.1:9000"}
    mc = "/usr/local/lib/edifice-storage/mc"

    def run(*command):
        # Commands never include credentials in output or argv except the user-add call below.
        result = subprocess.run([mc, *command], env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if result.returncode:
            raise RuntimeError("MinIO bootstrap failed: " + command[0])

    run("mb", "--ignore-existing", "edifice/edifice-prod")
    run("anonymous", "set", "none", "edifice/edifice-prod")
    run("version", "enable", "edifice/edifice-prod")
    policy = Path("/etc/edifice/storage-policy.json")
    policy.write_text(json.dumps({"Version": "2012-10-17", "Statement": [
        {"Effect": "Allow", "Action": ["s3:ListBucket", "s3:GetBucketLocation", "s3:ListBucketMultipartUploads"],
         "Resource": ["arn:aws:s3:::edifice-prod"]},
        {"Effect": "Allow", "Action": ["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject", "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"],
         "Resource": ["arn:aws:s3:::edifice-prod/*"]},
    ]}))
    os.chmod(policy, 0o600)
    run("admin", "policy", "create", "edifice", "edifice-files", str(policy))
    run("admin", "user", "add", "edifice", app["MINIO_ACCESS_KEY"], app["MINIO_SECRET_KEY"])
    run("admin", "policy", "attach", "edifice", "edifice-files", "--user", app["MINIO_ACCESS_KEY"])
    admin = boto3.client("s3", endpoint_url="http://127.0.0.1:9000",
                         aws_access_key_id=root["MINIO_ROOT_USER"], aws_secret_access_key=root["MINIO_ROOT_PASSWORD"],
                         region_name="us-east-1", config=Config(s3={"addressing_style": "path"}))
    admin.put_bucket_lifecycle_configuration(Bucket="edifice-prod", LifecycleConfiguration={"Rules": [{
        "ID": "abort-expired-multipart", "Status": "Enabled", "Filter": {"Prefix": ""},
        "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 7},
    }]})
    print("Private bucket, versioning, application policy and multipart expiry ready")


if __name__ == "__main__":
    main()
