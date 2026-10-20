# Unified Edifice File Storage

This change affects Edifice only, not Gougu OA uploads. All business file IDs and
references are preserved. The default backend remains local until explicitly
configured. Every upload uses 5MiB parts, a 500MiB per-file cap, server-side
ownership checks and a read-back SHA-256 check before becoming a business file.
Multi-attachment fields allow 20 files; contract main document and Excel import
remain single-file. Project files are submitted atomically as a batch, but retain
independent approval chains. Failed queue items block form submission until
retried or removed. Reselecting the same file resumes verified parts for 7 days.

## Pinned Community Build

User accepted the discontinued community edition. Source:
https://github.com/minio/minio/tree/RELEASE.2025-10-15T17-29-55Z
(commit prefix 9e49d5e7a648), AGPL-3.0. No AIStor activation key is used.
Build from the official pinned Go module, verify with `go version -m`, record
SHA-256, and retain the source license next to the binary. Never use `latest`.
The administrative client is pinned to RELEASE.2025-08-13T08-35-41Z.

MinIO binds only 127.0.0.1:9000/9001. No console, bucket or object is publicly
accessible. Application credentials have access to one bucket only and cannot
administer MinIO or delete completed objects. Secrets live in
`/etc/minio-edifice.env` and `/etc/edifice/storage.env`, both mode 0600.
The service uses `/home/minio-edifice/data`, versioning, automatic restart,
512MiB MemoryHigh and 768MiB MemoryMax. These are safeguards for the user's 4GB
server, not a claim that it meets vendor production sizing recommendations.
Measure real transfer workloads before switching. Same-host MinIO does not
increase internet bandwidth or provide disaster recovery by itself.

## Migration Order

1. Compare local/server git HEAD and changes. Back up DB, original file trees,
   legacy chunks, runtime configuration and old application artifact. Keep a
   verified backup off this server before retiring old copies. Do not remove original directories.
2. Stage and test MinIO, including a 500MiB multipart write/read/hash comparison.
3. Apply `database/migrations/v11/unified_file_storage.sql` once. It is additive.
4. Install the new application with storage still local; verify legacy downloads.
5. Source backend `.env` and `/etc/edifice/storage.env` with exported environment
   variables. Run `migrate_files.py copy --root REPO --manifest BACKUP/copy.json`.
   It reads every local source, checks DB size/MD5/SHA-256, copies it, reads the
   target back and records a migration ledger. It never deletes files.
6. Stop the backend briefly. Create `/run/edifice-storage-maintenance`, rerun copy
   for any new local files, then run `switch` with `--confirm-database edifice_prod`.
   All metadata switches commit together only after every file passes read-back.
   Business tables, file IDs and legacy URLs are not changed.
7. Add `/etc/edifice/storage.env` as a systemd EnvironmentFile for the backend,
   start it and deploy the matching frontend. Remove the maintenance marker.
8. Run `verify`, authenticated old/new preview/download/range and archive tests,
   upload/resume/cancel tests, and compare business counts. Check service memory
   and logs. Keep manifests outside git. Do not consider an HTTP 200 alone a pass.

`migrate_files.py` requires the pinned packages in requirements.txt. Thumbnail
migration deliberately fails closed if inventory has thumbnails; this production
inventory has none. Legacy unfinished chunks are backed up and retained, not
misidentified as complete files. Old unsafe chunk endpoints require a refresh.

## Rollback

Stop the backend, create the maintenance marker and run `rollback` with the same
environment, root and exact DB confirmation. It verifies original local sources
and object data before restoring only the migrated rows' storage metadata. New
MinIO uploads are excluded from this rollback and must remain readable using the
new storage-aware backend. Set FILE_STORAGE_TYPE=local for future uploads; keep
MinIO running. Do not blindly restore an old jar or overwrite the database after
new objects exist. Additive schema does not need to be dropped.

Versioning and the retained originals are not an independent backup. Configure a
separate off-server backup before deleting old copies or relying on this host as
the only copy of newly uploaded files.
