#!/bin/bash
# Edifice Vision 前端启动脚本（带自动重启）
# 用法: nohup ./deploy/run-edifice-vision.sh >> /var/log/edifice/vision-runner.log 2>&1 &
# 停止: pkill -f run-edifice-vision.sh; pkill -f "next start"

set -u
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$REPO_DIR/apps/edifice-vision"
PORT="${EDIFICE_VISION_PORT:-3001}"
LOG_DIR="/var/log/edifice"
LOG_FILE="$LOG_DIR/vision.log"
ERR_FILE="$LOG_DIR/vision.err.log"

mkdir -p "$LOG_DIR"

if [ ! -d "$APP_DIR/.next" ]; then
  echo "[$(date '+%F %T')] FATAL: .next not built in $APP_DIR" >&2
  exit 1
fi

# 停止之前可能残留的 next start 进程
pkill -f "next start" 2>/dev/null || true
sleep 2

echo "[$(date '+%F %T')] starting edifice-vision on port $PORT"

cd "$APP_DIR"
export NODE_ENV=production
export PORT="$PORT"
export NEXT_TELEMETRY_DISABLED=1

# 自动重启循环
while true; do
  /usr/bin/pnpm exec next start -H 0.0.0.0 -p "$PORT" \
    >> "$LOG_FILE" 2>> "$ERR_FILE"
  RC=$?
  echo "[$(date '+%F %T')] edifice-vision exited with code=$RC" >> "$LOG_FILE"
  if [ $RC -eq 0 ]; then
    echo "[$(date '+%F %T')] exit code 0, not restarting" >> "$LOG_FILE"
    break
  fi
  echo "[$(date '+%F %T')] restarting in 5s..." >> "$LOG_FILE"
  sleep 5
done
