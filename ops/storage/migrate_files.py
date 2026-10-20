#!/usr/bin/env python3
"""Non-destructive local -> private S3 migration. Never removes source files."""
import argparse
import hashlib
import json
import os
from pathlib import Path

import boto3
from boto3.s3.transfer import TransferConfig
from botocore.config import Config
import pymysql


def digest(stream):
    sha, md5, size = hashlib.sha256(), hashlib.md5(), 0
    while chunk := stream.read(1024 * 1024):
        sha.update(chunk)
        md5.update(chunk)
        size += len(chunk)
    return sha.hexdigest(), md5.hexdigest(), size


def source_path(root, row):
    relative = row["file_path"].lstrip("/")
    candidates = [relative[7:], relative] if relative.startswith("upload/") else [relative, "upload/" + relative]
    for candidate in candidates:
        path = (root / candidate).resolve()
        if not path.is_relative_to(root):
            raise ValueError("Source outside root: " + str(row["file_id"]))
        if path.is_file():
            return path
    raise FileNotFoundError("Missing source: " + str(row["file_id"]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["audit", "copy", "verify", "switch", "rollback"])
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--confirm-database")
    args = parser.parse_args()
    database = os.environ["MYSQL_DATABASE"]
    if args.mode in ("switch", "rollback"):
        if args.confirm_database != database or not Path("/run/edifice-storage-maintenance").exists():
            raise ValueError("Stop backend, create maintenance marker, and confirm the exact database before switching")
    root = args.root.resolve(strict=True)
    db = pymysql.connect(host=os.environ.get("MYSQL_HOST", "127.0.0.1"),
                         port=int(os.environ.get("MYSQL_PORT", "3306")),
                         user=os.environ["MYSQL_USERNAME"], password=os.environ["MYSQL_PASSWORD"],
                         database=database, charset="utf8mb4", autocommit=False,
                         cursorclass=pymysql.cursors.DictCursor)
    s3 = None
    bucket = os.environ.get("MINIO_BUCKET", "edifice-prod")
    if args.mode != "audit":
        s3 = boto3.client("s3", endpoint_url=os.environ.get("MINIO_ENDPOINT", "http://127.0.0.1:9000"),
                          aws_access_key_id=os.environ["MINIO_ACCESS_KEY"], aws_secret_access_key=os.environ["MINIO_SECRET_KEY"],
                          region_name="us-east-1", config=Config(s3={"addressing_style": "path"},
                          retries={"max_attempts": 3}, connect_timeout=10, read_timeout=300))
        s3.head_bucket(Bucket=bucket)
    report = []
    try:
        with db.cursor() as cur:
            cur.execute("SELECT * FROM files ORDER BY file_id")
            rows = cur.fetchall()
        # Refuse to silently drop thumbnails; this environment's inventory has none.
        if any(row.get("thumbnail_url") for row in rows):
            raise ValueError("Thumbnail inventory requires a separate migration; no switch performed")
        for row in rows:
            file_id = row["file_id"]
            with db.cursor() as cur:
                cur.execute("SELECT * FROM file_storage_migration WHERE file_id=%s", (file_id,))
                ledger = cur.fetchone()
            if row.get("storage_type") not in (None, "", "local", "minio"):
                raise ValueError("Unexpected storage backend: " + str(file_id))
            if row.get("storage_type") == "minio" and not ledger:
                # New uploads are not part of the migration or its rollback.
                result = s3.get_object(Bucket=row["storage_bucket"], Key=row["object_key"])
                with result["Body"] as stream:
                    actual = digest(stream)
                assert actual == (row["file_hash"], row["file_md5"], row["file_size"]), file_id
                report.append({"fileId": str(file_id), "status": "new-object-verified", "size": actual[2]})
                continue
            local = source_path(root, row)
            with local.open("rb") as stream:
                sha, md5, size = digest(stream)
            if (sha, md5, size) != (row["file_hash"], row["file_md5"], row["file_size"]):
                raise ValueError("Source hash/size mismatch: " + str(file_id))
            key = ledger["target_key"] if ledger else f"legacy/{file_id}/{sha}/{local.name}"
            if args.mode == "copy":
                s3.upload_file(str(local), bucket, key, ExtraArgs={"Metadata": {"sha256": sha, "file-id": str(file_id)}},
                               Config=TransferConfig(multipart_threshold=5 * 1024**2,
                                                     multipart_chunksize=5 * 1024**2, max_concurrency=1))
            version = None
            if args.mode != "audit":
                result = s3.get_object(Bucket=bucket, Key=key)
                version = result.get("VersionId")
                with result["Body"] as stream:
                    actual = digest(stream)
                if actual != (sha, md5, size):
                    raise ValueError("Target hash/size mismatch: " + str(file_id))
            if args.mode == "copy":
                with db.cursor() as cur:
                    cur.execute("""INSERT INTO file_storage_migration
                      (file_id,original_storage_type,original_file_path,target_bucket,target_key,sha256,size,object_version,status,verified_time)
                      VALUES(%s,%s,%s,%s,%s,%s,%s,%s,'verified',NOW())
                      ON DUPLICATE KEY UPDATE sha256=VALUES(sha256),size=VALUES(size),object_version=VALUES(object_version),verified_time=NOW()""",
                                (file_id, row.get("storage_type"), row["file_path"], bucket, key, sha, size, version))
                db.commit()
            elif args.mode in ("switch", "rollback"):
                if not ledger or ledger["sha256"] != sha or ledger["size"] != size or ledger["target_bucket"] != bucket:
                    raise ValueError("Missing or mismatched migration ledger: " + str(file_id))
                # All metadata switches are committed atomically, only after every file verifies.
                with db.cursor() as cur:
                    if args.mode == "switch":
                        cur.execute("""UPDATE files SET storage_type='minio', storage_bucket=%s,object_key=%s,
                          object_version=%s,storage_verified_time=NOW() WHERE file_id=%s AND file_hash=%s AND file_size=%s""",
                                    (bucket, key, version, file_id, sha, size))
                        cur.execute("UPDATE file_storage_migration SET status='switched',switched_time=NOW() WHERE file_id=%s", (file_id,))
                    else:
                        cur.execute("""UPDATE files SET storage_type=%s, storage_bucket=NULL,object_key=NULL,
                          object_version=NULL,storage_verified_time=NULL WHERE file_id=%s""",
                                    (ledger["original_storage_type"], file_id))
                        cur.execute("UPDATE file_storage_migration SET status='rolled-back' WHERE file_id=%s", (file_id,))
            report.append({"fileId": str(file_id), "source": str(local), "key": key,
                           "sha256": sha, "size": size, "status": args.mode})
            print(json.dumps({"fileId": str(file_id), "bytes": size, "status": args.mode}), flush=True)
        args.manifest.parent.mkdir(parents=True, exist_ok=True)
        args.manifest.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
        os.chmod(args.manifest, 0o600)
        db.commit()
        print(json.dumps({"files": len(report), "bytes": sum(item["size"] for item in report), "mode": args.mode}), flush=True)
    except BaseException:
        db.rollback()
        raise
    finally:
        db.close()


if __name__ == "__main__":
    main()
