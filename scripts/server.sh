#!/usr/bin/env bash
# Local production web server on 127.0.0.1:3000 (Funnel → LOCAL_BASE_URL), detached in its own session
# so it survives the shell/tool that started it.
#   scripts/server.sh start | stop | restart | status
set -uo pipefail
cd "$(dirname "$0")/.."
CMD=${RENTCARE_SERVER_CMD:-"scripts/with-secrets.sh /postgres,/minio -- env PUBLIC_BASE_URL=$(grep -E "^LOCAL_BASE_URL=" web/.env | cut -d= -f2-) pnpm --dir web start"}
LOG=${RENTCARE_SERVER_LOG:-${TMPDIR:-/tmp}/rentcare-server.log}
B=$(grep -E "^LOCAL_BASE_URL=" web/.env | cut -d= -f2-)
pid() { lsof -nP -iTCP@127.0.0.1:3000 -sTCP:LISTEN -t 2>/dev/null | head -1; }
up() { [[ "$(curl -s -o /dev/null -w '%{http_code}' -m 3 "$B/")" == 200 ]]; }
start() {
  [[ -n "$(pid)" ]] && { echo "already running (pid $(pid))"; return 0; }
  # os.setsid: new session and process group, so stopping the caller does not stop the server.
  python3 -c 'import os,sys; os.setsid(); f=os.open(sys.argv[2],os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600); os.dup2(f,1); os.dup2(f,2); os.dup2(os.open(os.devnull,os.O_RDONLY),0); os.execvp("sh",["sh","-c",sys.argv[1]])' "$CMD" "$LOG" &
  for _ in $(seq 1 40); do up && { echo "up (pid $(pid)) $B"; return 0; }; sleep 1; done
  echo "did not come up, see $LOG" >&2; return 1
}
stop() {
  local p; p=$(pid); [[ -z "$p" ]] && { echo "not running"; return 0; }
  kill "$p"; for _ in $(seq 1 10); do [[ -z "$(pid)" ]] && { echo "stopped"; return 0; }; sleep 1; done
  echo "still running (pid $p)" >&2; return 1
}
case "${1:-}" in
  start) start ;; stop) stop ;; restart) stop && start ;;
  status) if up; then echo "up (pid $(pid)) $B"; else echo "down"; exit 1; fi ;;
  *) sed -n '4p' "$0"; exit 1 ;;
esac
