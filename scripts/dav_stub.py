#!/usr/bin/env python3
"""本地 WebDAV 桩服务：仅用于验证「课外班费用管家」的坚果云同步链路。

支持 PUT / GET / HEAD / DELETE / PROPFIND(Depth 0|1) / MKCOL，Basic 认证，
真实返回 ETag、Last-Modified、Content-Length，并实现 If-Match / If-None-Match 乐观锁
（不匹配返回 412），用于测试 401 / 405 / 412 / 507 等分支。

用法：
    python3 scripts/dav_stub.py 8099 test@test.com apppass /tmp/ledger-dav
环境变量：
    FORCE_412_ONCE=1   第一次 PUT 强制返回 412（验证重试逻辑）
    FORCE_507=1        所有 PUT 返回 507（验证空间不足提示）
"""
import base64
import email.utils
import hashlib
import os
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
USER = sys.argv[2] if len(sys.argv) > 2 else "test@test.com"
PASS = sys.argv[3] if len(sys.argv) > 3 else "apppass"
ROOT = sys.argv[4] if len(sys.argv) > 4 else "/tmp/ledger-dav"
PREFIX = "/dav"

force_412_done = False


def etag_of(data: bytes) -> str:
    return hashlib.md5(data).hexdigest()


def iso_now() -> str:
    return email.utils.formatdate(usegmt=True)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[dav] " + (fmt % args) + "\n")

    # ---------------- helpers ----------------
    def authed(self):
        h = self.headers.get("Authorization", "")
        if not h.startswith("Basic "):
            return False
        try:
            raw = base64.b64decode(h[6:]).decode()
        except Exception:
            return False
        return raw == f"{USER}:{PASS}"

    def drain(self):
        n = int(self.headers.get("Content-Length", "0") or 0)
        if n:
            self.rfile.read(n)

    def need_auth(self):
        if self.authed():
            return False
        self.drain()
        self.reply(401, extra={"WWW-Authenticate": 'Basic realm="dav"'})
        return True

    def rel_path(self):
        raw = urllib.parse.unquote(self.path.split("?")[0])
        if raw.startswith(PREFIX):
            raw = raw[len(PREFIX):]
        raw = raw.lstrip("/")
        full = os.path.normpath(os.path.join(ROOT, raw))
        if not full.startswith(os.path.abspath(ROOT)):
            return None
        return full

    def reply(self, code, body=b"", ctype="text/plain; charset=utf-8", extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if body:
            self.wfile.write(body)

    def entry_xml(self, full: str, href: str) -> str:
        is_dir = os.path.isdir(full)
        if is_dir:
            props = "<d:resourcetype><d:collection/></d:resourcetype>"
            length = ""
        else:
            with open(full, "rb") as fh:
                data = fh.read()
            props = "<d:resourcetype/>"
            length = f"<d:getcontentlength>{len(data)}</d:getcontentlength>"
            props += f"<d:getetag>&quot;{etag_of(data)}&quot;</d:getetag>"
            props += f"<d:getlastmodified>{email.utils.formatdate(os.path.getmtime(full), usegmt=True)}</d:getlastmodified>"
        if is_dir:
            # 目录也给一个 etag，便于客户端判断
            sig = f"{full}:{os.path.getmtime(full)}".encode()
            props += f"<d:getetag>&quot;{etag_of(sig)}&quot;</d:getetag>"
            props += f"<d:getlastmodified>{email.utils.formatdate(os.path.getmtime(full), usegmt=True)}</d:getlastmodified>"
        return (
            f"<d:response><d:href>{href}</d:href>"
            f"<d:propstat><d:prop>{props}{length}</d:prop>"
            f"<d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
        )

    # ---------------- methods ----------------
    def do_PROPFIND(self):
        if self.need_auth():
            return
        full = self.rel_path()
        if full is None:
            return self.reply(403)
        depth = self.headers.get("Depth", "1")
        self.drain()

        parts = []
        base_href = self.path.split("?")[0]
        if not os.path.exists(full):
            return self.reply(404)

        parts.append(self.entry_xml(full, base_href))
        if os.path.isdir(full) and depth != "0":
            for name in sorted(os.listdir(full)):
                child = os.path.join(full, name)
                href = base_href.rstrip("/") + "/" + urllib.parse.quote(name) + ("/" if os.path.isdir(child) else "")
                parts.append(self.entry_xml(child, href))

        quota = (
            f"<d:response><d:href>{PREFIX}/</d:href><d:propstat><d:prop>"
            f"<d:quota-used-bytes>104857600</d:quota-used-bytes>"
            f"<d:quota-available-bytes>3221225472</d:quota-available-bytes>"
            f"</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"
        )
        body = (
            '<?xml version="1.0" encoding="utf-8"?>'
            '<d:multistatus xmlns:d="DAV:">' + "".join(parts) + quota + "</d:multistatus>"
        ).encode()
        self.reply(207, body, "application/xml; charset=utf-8")

    def do_MKCOL(self):
        if self.need_auth():
            return
        full = self.rel_path()
        self.drain()
        if full is None:
            return self.reply(403)
        if os.path.isdir(full):
            return self.reply(405)  # 已存在：坚果云也是这个行为
        if not os.path.isdir(os.path.dirname(full)):
            return self.reply(409)
        os.makedirs(full, exist_ok=True)
        self.reply(201)

    def do_PUT(self):
        global force_412_done
        if self.need_auth():
            return
        full = self.rel_path()
        n = int(self.headers.get("Content-Length", "0") or 0)
        data = self.rfile.read(n) if n else b""
        if full is None:
            return self.reply(403)

        if os.environ.get("FORCE_507") == "1":
            return self.reply(507)
        # 只在正式数据文件上制造一次 412，避免落在 backups/data-*.json 或 .tmp 上
        if (
            os.environ.get("FORCE_412_ONCE") == "1"
            and not force_412_done
            and full.endswith("/data.json")
        ):
            force_412_done = True
            sys.stderr.write("[dav] injected 412 on " + full + "\n")
            return self.reply(412)

        if not os.path.isdir(os.path.dirname(full)):
            return self.reply(409)

        if_match = self.headers.get("If-Match")
        if_none = self.headers.get("If-None-Match")
        current = None
        if os.path.isfile(full):
            with open(full, "rb") as fh:
                current = etag_of(fh.read())

        if if_none == "*" and current is not None:
            return self.reply(412)
        if if_match:
            want = if_match.strip().strip('"')
            if current is None or current != want:
                return self.reply(412)

        with open(full, "wb") as fh:
            fh.write(data)
        self.reply(
            201,
            extra={"ETag": f'"{etag_of(data)}"', "Last-Modified": iso_now()},
        )

    def do_GET(self):
        if self.need_auth():
            return
        full = self.rel_path()
        if full is None or not os.path.isfile(full):
            return self.reply(404)
        with open(full, "rb") as fh:
            data = fh.read()
        self.reply(
            200,
            data,
            "application/octet-stream",
            extra={
                "ETag": f'"{etag_of(data)}"',
                "Last-Modified": email.utils.formatdate(os.path.getmtime(full), usegmt=True),
            },
        )

    def do_HEAD(self):
        if self.need_auth():
            return
        full = self.rel_path()
        if full is None or not os.path.isfile(full):
            return self.reply(404)
        self.reply(200, extra={"ETag": f'"{etag_of(open(full, "rb").read())}"'})

    def do_DELETE(self):
        if self.need_auth():
            return
        full = self.rel_path()
        self.drain()
        if full is None or not os.path.exists(full):
            return self.reply(404)
        if os.path.isdir(full):
            os.rmdir(full)
        else:
            os.remove(full)
        self.reply(204)


if __name__ == "__main__":
    os.makedirs(ROOT, exist_ok=True)
    print(f"[dav] serving {ROOT} on 0.0.0.0:{PORT} user={USER} pass={PASS}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
