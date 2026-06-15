#!/bin/sh
set -eu

composer install --no-interaction --prefer-dist

mkdir -p runtime public/storage public/backup

exec php think run --host 0.0.0.0 --port 8080
