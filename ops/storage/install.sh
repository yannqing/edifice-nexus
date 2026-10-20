#!/bin/bash
set -euo pipefail
# Run only after verifying both binaries against the locally built artifacts.
test "$(id -u)" = 0
test -f /tmp/edifice-minio-community.gz
test -f /tmp/edifice-mc.gz
mountpoint -q /home
! ss -ltn | grep -Eq ':(9000|9001) '
gzip -t /tmp/edifice-minio-community.gz /tmp/edifice-mc.gz
getent passwd minio-edifice >/dev/null || useradd --system --user-group --home-dir /home/minio-edifice --shell /usr/sbin/nologin minio-edifice
install -d -o minio-edifice -g minio-edifice -m 700 /home/minio-edifice/data
install -d -m 755 /usr/local/lib/edifice-storage
gzip -dc /tmp/edifice-minio-community.gz > /usr/local/lib/edifice-storage/minio
gzip -dc /tmp/edifice-mc.gz > /usr/local/lib/edifice-storage/mc
chmod 755 /usr/local/lib/edifice-storage/minio /usr/local/lib/edifice-storage/mc
install -m 644 /tmp/LICENSE /usr/local/lib/edifice-storage/LICENSE-minio
install -d -m 700 /opt/edifice-storage-deps
tar -xzf /tmp/edifice-storage-deps.tar.gz -C /opt/edifice-storage-deps
export PYTHONPATH=/opt/edifice-storage-deps
python3 /tmp/bootstrap.py credentials
install -m 644 /tmp/minio-edifice.service /etc/systemd/system/minio-edifice.service
systemctl daemon-reload
systemctl enable --now minio-edifice.service
python3 /tmp/bootstrap.py bucket
systemctl show minio-edifice -p ActiveState -p MemoryCurrent -p MemoryPeak -p NRestarts
