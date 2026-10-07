#!/usr/bin/env python3
"""Reverse-proxy Manage Cases and inject a TEC 'Upload batch file' primary-nav item.

CFTLib's XUI builds headerConfig from baked-in menuConfigs. This proxy sits on the
public Manage Cases port, forwards to the real XUI container on an internal port,
and rewrites GET /external/config/ui/ (and the legacy /external/configuration-ui/
path) so TEC clerks see Upload batch file without a custom XUI image.

Upload batch file nav points at the ExUI CCD create-case deep link for uploadBatch.
Legacy /tec-create-batch redirects there for bookmarks.

Also stubs GET **/lov/categories/CaseLinkingReasonCode** (CFTLib has no
rd-commondata-api). ExUI Linked Cases resolves Reason codes through that LOV;
without it the Reasons column stays blank even when caseLinks carry CLRC007.
"""

from __future__ import annotations

import gzip
import json
import os
import select
import socket
import sys
import zlib
from http.client import HTTPConnection, HTTPSConnection
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

LISTEN_HOST = os.environ.get("XUI_NAV_PROXY_HOST", "127.0.0.1")
LISTEN_PORT = int(os.environ.get("XUI_NAV_PROXY_PORT", "3000"))
UPSTREAM = os.environ.get("XUI_NAV_PROXY_UPSTREAM", "http://127.0.0.1:3002")
CREATE_BATCH_PATH = os.environ.get("XUI_NAV_PROXY_CREATE_BATCH_PATH") or os.environ.get(
    "XUI_NAV_PROXY_BATCHES_PATH",
    "/cases/case-create/TEC/TEC_BATCH/uploadBatch",
)
LEGACY_CREATE_BATCH_STUB = "/tec-create-batch"
# Anchored so caseworker-tec-la does not match the clerk menu.
TEC_ROLE_KEY = os.environ.get("XUI_NAV_PROXY_TEC_ROLE_KEY", "^caseworker-tec$")
TEC_LA_ROLE_KEY = os.environ.get("XUI_NAV_PROXY_TEC_LA_ROLE_KEY", "^caseworker-tec-la$")
CREATE_BATCH_LABEL = "Upload batch file"

_HOP_BY_HOP = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailers",
    "transfer-encoding",
    "upgrade",
    "proxy-connection",
    "content-length",
    "content-encoding",
}

_CONFIG_PATHS = {
    "/external/config/ui",
    "/external/config/ui/",
    "/external/configuration-ui",
    "/external/configuration-ui/",
}

# CFTLib does not run rd-commondata-api. ExUI Linked Cases resolves Reason via
# CaseLinkingReasonCode LOV; without it the Reasons column stays blank.
_CASE_LINKING_REASON_LOV = {
    "list_of_values": [
        {"category_key": "CaseLinkingReasonCode", "key": "CLRC001", "value_en": "Case consolidated", "value_cy": "", "hint_text_en": "", "hint_text_cy": "", "lov_order": 1, "parent_category": "", "parent_key": "", "active_flag": "Y", "child_nodes": []},
        {"category_key": "CaseLinkingReasonCode", "key": "CLRC002", "value_en": "Linked for a hearing", "value_cy": "", "hint_text_en": "", "hint_text_cy": "", "lov_order": 2, "parent_category": "", "parent_key": "", "active_flag": "Y", "child_nodes": []},
        {"category_key": "CaseLinkingReasonCode", "key": "CLRC003", "value_en": "Progressed as part of this lead case", "value_cy": "", "hint_text_en": "", "hint_text_cy": "", "lov_order": 3, "parent_category": "", "parent_key": "", "active_flag": "Y", "child_nodes": []},
        {"category_key": "CaseLinkingReasonCode", "key": "CLRC004", "value_en": "Related appeal", "value_cy": "", "hint_text_en": "", "hint_text_cy": "", "lov_order": 4, "parent_category": "", "parent_key": "", "active_flag": "Y", "child_nodes": []},
        {"category_key": "CaseLinkingReasonCode", "key": "CLRC005", "value_en": "Related proceedings", "value_cy": "", "hint_text_en": "", "hint_text_cy": "", "lov_order": 5, "parent_category": "", "parent_key": "", "active_flag": "Y", "child_nodes": []},
        {"category_key": "CaseLinkingReasonCode", "key": "CLRC006", "value_en": "Same Party", "value_cy": "", "hint_text_en": "", "hint_text_cy": "", "lov_order": 6, "parent_category": "", "parent_key": "", "active_flag": "Y", "child_nodes": []},
        {"category_key": "CaseLinkingReasonCode", "key": "CLRC007", "value_en": "Other", "value_cy": "", "hint_text_en": "", "hint_text_cy": "", "lov_order": 7, "parent_category": "", "parent_key": "", "active_flag": "Y", "child_nodes": []},
    ]
}


def _is_case_linking_reason_lov(path: str) -> bool:
    return "lov/categories/CaseLinkingReasonCode" in path


def _decode_upstream_body(raw: bytes, content_encoding: str | None) -> bytes:
    """Content-Encoding is stripped as hop-by-hop, so the forwarded body must be plain.

    http.client leaves gzip/deflate bytes intact. Case File View JSON crosses the
    upstream compression threshold once documents are attached; without decoding,
    ExUI tries to parse gzip as JSON and shows a service error.
    """
    if not raw or not content_encoding:
        return raw
    encoding = content_encoding.lower()
    if "gzip" in encoding:
        return gzip.decompress(raw)
    if "deflate" in encoding:
        try:
            return zlib.decompress(raw)
        except zlib.error:
            return zlib.decompress(raw, -zlib.MAX_WBITS)
    return raw


def _tec_menu(create_batch_href: str) -> list[dict]:
    # Case list omitted on purpose: clerks still reach /cases via the Manage cases title.
    # href MUST be a real ExUI Angular route — routerLink does not hit the proxy, so
    # /tec-create-batch falls through to /cases.
    return [
        {"text": "Create case", "href": "/cases/case-filter", "active": False},
        {"text": CREATE_BATCH_LABEL, "href": create_batch_href, "active": False},
        {
            "text": "Find case",
            "href": "/cases/case-search",
            "active": False,
            "align": "right",
            "ngClass": "hmcts-search-toggle__button",
        },
    ]


def _tec_la_menu(create_batch_href: str) -> list[dict]:
    # Local authority users: Upload batch file + Find case only (no Create case).
    return [
        {"text": CREATE_BATCH_LABEL, "href": create_batch_href, "active": False},
        {
            "text": "Find case",
            "href": "/cases/case-search",
            "active": False,
            "align": "right",
            "ngClass": "hmcts-search-toggle__button",
        },
    ]


def _create_batch_item(create_batch_href: str) -> dict:
    # roles gate keeps this off non-TEC menus when injected into the ".+" fallback.
    return {
        "text": CREATE_BATCH_LABEL,
        "href": create_batch_href,
        "active": False,
        "roles": ["caseworker-tec", "caseworker-tec-system", "caseworker-tec-la"],
    }


def _force_create_batch_href(items: list, create_batch_href: str) -> tuple[list, bool]:
    """Ensure every Upload batch file nav item points at the CCD deep link."""
    updated: list = []
    found = False
    for item in items:
        if isinstance(item, dict) and item.get("text") == CREATE_BATCH_LABEL:
            found = True
            patched = dict(item)
            patched["href"] = create_batch_href
            updated.append(patched)
        else:
            updated.append(item)
    return updated, found


def _inject_header_config(
    payload: dict,
    create_batch_href: str,
    role_key: str,
    la_role_key: str,
) -> dict:
    header = payload.get("headerConfig")
    if not isinstance(header, dict):
        header = {}

    tec_menu = _tec_menu(create_batch_href)
    tec_la_menu = _tec_la_menu(create_batch_href)
    create_batch_item = _create_batch_item(create_batch_href)

    # Prefer dedicated TEC keys (matched first among non-".+" keys when placed early).
    rebuilt: dict = {role_key: tec_menu, la_role_key: tec_la_menu}
    for key, items in header.items():
        if key in (role_key, la_role_key):
            continue
        if key == ".+" and isinstance(items, list):
            items, found = _force_create_batch_href(list(items), create_batch_href)
            if not found:
                # Insert before right-aligned Find case / Search entries when possible.
                insert_at = next(
                    (
                        idx
                        for idx, item in enumerate(items)
                        if isinstance(item, dict) and item.get("align") == "right"
                    ),
                    len(items),
                )
                items.insert(insert_at, create_batch_item)
            rebuilt[key] = items
        elif isinstance(items, list):
            patched, _found = _force_create_batch_href(list(items), create_batch_href)
            rebuilt[key] = patched
        else:
            rebuilt[key] = items

    payload["headerConfig"] = rebuilt
    return payload


def _legacy_create_batch_bounce_html(target: str) -> bytes:
    # Full-page bounce for bookmarks. ExUI primary nav must not use this path:
    # it binds href via Angular routerLink, which never requests this URL.
    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta http-equiv="refresh" content="0;url={target}" />
  <title>Upload batch file</title>
  <script>window.location.replace({target!r});</script>
</head>
<body>
  <p>Redirecting to <a href="{target}">Upload batch file</a>…</p>
</body>
</html>
"""
    return html.encode("utf-8")


def _split_upstream(url: str) -> tuple[str, str, int, bool]:
    parts = urlsplit(url)
    scheme = parts.scheme or "http"
    host = parts.hostname or "127.0.0.1"
    port = parts.port or (443 if scheme == "https" else 80)
    return scheme, host, port, scheme == "https"


class ProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def do_GET(self) -> None:
        self._handle()

    def do_POST(self) -> None:
        self._handle()

    def do_PUT(self) -> None:
        self._handle()

    def do_PATCH(self) -> None:
        self._handle()

    def do_DELETE(self) -> None:
        self._handle()

    def do_HEAD(self) -> None:
        self._handle()

    def do_OPTIONS(self) -> None:
        self._handle()

    def _handle(self) -> None:
        path = urlsplit(self.path).path
        if path.rstrip("/") == LEGACY_CREATE_BATCH_STUB.rstrip("/") and self.command in (
            "GET",
            "HEAD",
        ):
            body = _legacy_create_batch_bounce_html(CREATE_BATCH_PATH)
            self.send_response(302)
            self.send_header("Location", CREATE_BATCH_PATH)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(body)
            return

        if _is_case_linking_reason_lov(path) and self.command in ("GET", "HEAD"):
            raw = json.dumps(_CASE_LINKING_REASON_LOV).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(raw)
            return

        if self.headers.get("Upgrade", "").lower() == "websocket":
            self._tunnel_websocket()
            return

        self._proxy_http(path)

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length <= 0:
            return b""
        return self.rfile.read(length)

    def _forward_headers(self) -> list[tuple[str, str]]:
        out: list[tuple[str, str]] = []
        for key, value in self.headers.items():
            if key.lower() in _HOP_BY_HOP:
                continue
            # Keep the browser Host (e.g. localhost:3000). If we let http.client
            # substitute the upstream host:port, XUI builds OAuth redirect_uri to
            # :3002 and the user leaves the nav-injection proxy after login.
            out.append((key, value))
        client_host = self.headers.get("Host")
        if client_host:
            out.append(("X-Forwarded-Host", client_host))
            out.append(("X-Forwarded-Proto", "http"))
            out.append(("X-Forwarded-Port", client_host.split(":")[-1] if ":" in client_host else "80"))
        return out

    def _proxy_http(self, path: str) -> None:
        scheme, host, port, tls = _split_upstream(UPSTREAM)
        body = self._read_body()
        conn: HTTPConnection | HTTPSConnection
        if tls:
            conn = HTTPSConnection(host, port, timeout=120)
        else:
            conn = HTTPConnection(host, port, timeout=120)

        headers = dict(self._forward_headers())
        # http.client overwrites Host unless we set it explicitly after construction.
        if "Host" not in {k.title() for k in headers} and "host" not in {k.lower() for k in headers}:
            headers["Host"] = self.headers.get("Host", f"{host}:{port}")

        try:
            conn.request(
                self.command,
                self.path,
                body=body or None,
                headers=headers,
            )
            upstream = conn.getresponse()
            raw = _decode_upstream_body(upstream.read(), upstream.getheader("Content-Encoding"))

            if path.rstrip("/") in {p.rstrip("/") for p in _CONFIG_PATHS} and self.command == "GET":
                try:
                    payload = json.loads(raw.decode("utf-8"))
                    if isinstance(payload, dict):
                        payload = _inject_header_config(
                            payload, CREATE_BATCH_PATH, TEC_ROLE_KEY, TEC_LA_ROLE_KEY
                        )
                        raw = json.dumps(payload).encode("utf-8")
                except (UnicodeDecodeError, json.JSONDecodeError):
                    pass

            self.send_response(upstream.status)
            for key, value in upstream.getheaders():
                if key.lower() in _HOP_BY_HOP:
                    continue
                # Avoid browsers / SW keeping a stale Upload batch file href.
                if path.rstrip("/") in {p.rstrip("/") for p in _CONFIG_PATHS} and key.lower() in {
                    "cache-control",
                    "etag",
                    "last-modified",
                    "expires",
                }:
                    continue
                self.send_header(key, value)
            if path.rstrip("/") in {p.rstrip("/") for p in _CONFIG_PATHS}:
                self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(raw)
        except OSError as exc:
            message = f"Upstream Manage Cases unavailable at {UPSTREAM}: {exc}\n".encode()
            self.send_response(502)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(message)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(message)
        finally:
            conn.close()

    def _tunnel_websocket(self) -> None:
        scheme, host, port, _tls = _split_upstream(UPSTREAM)
        try:
            upstream = socket.create_connection((host, port), timeout=30)
        except OSError as exc:
            message = f"Upstream Manage Cases unavailable at {UPSTREAM}: {exc}\n".encode()
            self.send_response(502)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(message)))
            self.end_headers()
            self.wfile.write(message)
            return

        # Rebuild the client request line + headers for the upstream socket.
        client_host = self.headers.get("Host", f"{host}:{port}")
        request = f"{self.command} {self.path} {self.request_version}\r\n"
        for key, value in self.headers.items():
            if key.lower() == "host":
                request += f"Host: {client_host}\r\n"
            else:
                request += f"{key}: {value}\r\n"
        request += f"X-Forwarded-Host: {client_host}\r\n"
        request += "X-Forwarded-Proto: http\r\n"
        request += "\r\n"
        upstream.sendall(request.encode("latin-1"))

        client = self.connection
        try:
            while True:
                readable, _, errored = select.select([client, upstream], [], [client, upstream], 60)
                if errored:
                    break
                if not readable:
                    continue
                for source in readable:
                    data = source.recv(65536)
                    if not data:
                        return
                    dest = upstream if source is client else client
                    dest.sendall(data)
        except OSError:
            return
        finally:
            try:
                upstream.close()
            except OSError:
                pass


def main() -> int:
    server = ThreadingHTTPServer((LISTEN_HOST, LISTEN_PORT), ProxyHandler)
    print(
        f"XUI Upload batch file nav proxy listening on http://{LISTEN_HOST}:{LISTEN_PORT} "
        f"-> {UPSTREAM} (TEC menu keys {TEC_ROLE_KEY!r}/{TEC_LA_ROLE_KEY!r}, "
        f"upload batch file {CREATE_BATCH_PATH})",
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down nav proxy", flush=True)
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
