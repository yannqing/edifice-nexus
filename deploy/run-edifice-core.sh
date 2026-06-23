#!/bin/bash
# Edifice Core 后端启动脚本（带自动重启）
# 用法: nohup ./deploy/run-edifice-core.sh >> /var/log/edifice/core-runner.log 2>&1 &
# 停止: pkill -f run-edifice-core.sh; pkill -f "edifice-0.0.1-SNAPSHOT.jar"

set -u
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$REPO_DIR/apps/edifice-core"
JAR="$APP_DIR/target/edifice-0.0.1-SNAPSHOT.jar"
PORT="${EDIFICE_PORT:-8081}"
LOG_DIR="/var/log/edifice"
LOG_FILE="$LOG_DIR/core.log"
ERR_FILE="$LOG_DIR/core.err.log"

mkdir -p "$LOG_DIR"

if [ ! -f "$JAR" ]; then
  echo "[$(date '+%F %T')] FATAL: jar not found: $JAR" >&2
  exit 1
fi

# 加载 .env 到环境变量（java -jar 不会自动读 .env，必须显式注入）
ENV_FILE="$APP_DIR/.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
  echo "[$(date '+%F %T')] loaded env from $ENV_FILE"
else
  echo "[$(date '+%F %T')] WARN: .env not found at $ENV_FILE" >&2
fi

# 停止之前可能残留的同端口 java 进程（基于 jar 路径匹配，避免误伤）
pkill -f "edifice-0.0.1-SNAPSHOT.jar.*--server.port=$PORT" 2>/dev/null || true
sleep 2

echo "[$(date '+%F %T')] starting edifice-core on port $PORT, jar=$JAR"

# 自动重启循环：进程异常退出（非 0）后等 5 秒重启；正常退出（exit 0）不重启
while true; do
  /usr/bin/java -Dfile.encoding=UTF-8 \
    -Xms256m -Xmx768m -XX:+UseG1GC \
    -jar "$JAR" \
    --server.port="$PORT" \
    >> "$LOG_FILE" 2>> "$ERR_FILE"
  RC=$?
  echo "[$(date '+%F %T')] edifice-core exited with code=$RC" >> "$LOG_FILE"
  if [ $RC -eq 0 ]; then
    echo "[$(date '+%F %T')] exit code 0, not restarting" >> "$LOG_FILE"
    break
  fi
  echo "[$(date '+%F %T')] restarting in 5s..." >> "$LOG_FILE"
  sleep 5
done
