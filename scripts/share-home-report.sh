#!/usr/bin/env bash
# Publicly expose only the self-contained Home report through a Quick Tunnel.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
report_path="$repo_root/docs/design/home-redesigns/index.html"
report_port="${REPORT_PORT:-8787}"
cloudflared_path="${CLOUDFLARED_BIN:-/workspace/toolchains/bin/cloudflared}"
if [[ ! -x "$cloudflared_path" ]]; then
  cloudflared_path="$(command -v cloudflared || true)"
fi
if [[ -z "$cloudflared_path" || ! -x "$cloudflared_path" ]]; then
  echo 'Install cloudflared, or set CLOUDFLARED_BIN to its executable.' >&2
  exit 1
fi
python3 - "$report_path" "$report_port" <<'PY' &
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import sys
from urllib.parse import urlsplit
report = Path(sys.argv[1])
class ReportHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if urlsplit(self.path).path not in ('/', '/index.html'):
            self.send_error(404)
            return
        body = report.read_bytes()
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(body)
ThreadingHTTPServer(('127.0.0.1', int(sys.argv[2])), ReportHandler).serve_forever()
PY
report_server_pid=$!
trap 'kill "$report_server_pid" 2>/dev/null || true' EXIT
# HTTP/2 avoids requiring QUIC/UDP; outbound tunnel access is still required.
"$cloudflared_path" tunnel --no-autoupdate --protocol http2 --url "http://127.0.0.1:$report_port"
