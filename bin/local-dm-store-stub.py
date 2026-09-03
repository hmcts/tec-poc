#!/usr/bin/env python3
"""Minimal dm-store stub for local CDAM uploads and attachToCase metadata patches.

Documents are persisted under bin/.local-dm-store-data so binaries survive stub restarts.
"""

from __future__ import annotations

import json
import mimetypes
import re
import uuid
from email import policy
from email.parser import BytesParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse

HOST = "127.0.0.1"
PORT = 4506
BASE_URL = f"http://localhost:{PORT}"
DATA_DIR = Path(__file__).resolve().parent / ".local-dm-store-data"
META_FILE = DATA_DIR / "documents.json"

DOCUMENTS: dict[str, dict] = {}


def load_documents() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if not META_FILE.exists():
        return
    try:
        raw = json.loads(META_FILE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return

    for document_id, meta in raw.items():
        content_path = DATA_DIR / f"{document_id}.bin"
        content = content_path.read_bytes() if content_path.exists() else b""
        DOCUMENTS[document_id.lower()] = {
            "originalDocumentName": meta.get("originalDocumentName", "upload.bin"),
            "mimeType": meta.get("mimeType", "application/octet-stream"),
            "size": meta.get("size", len(content)),
            "content": content,
            "metadata": meta.get("metadata") or {},
        }


def save_documents() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    serialisable: dict[str, dict] = {}
    for document_id, document in DOCUMENTS.items():
        content_path = DATA_DIR / f"{document_id}.bin"
        content_path.write_bytes(document.get("content") or b"")
        serialisable[document_id] = {
            "originalDocumentName": document.get("originalDocumentName", "upload.bin"),
            "mimeType": document.get("mimeType", "application/octet-stream"),
            "size": document.get("size", 0),
            "metadata": document.get("metadata") or {},
        }
    META_FILE.write_text(json.dumps(serialisable, indent=2), encoding="utf-8")


def read_chunked_body(handler: BaseHTTPRequestHandler) -> bytes:
    chunks: list[bytes] = []
    while True:
        size_line = handler.rfile.readline()
        if not size_line:
            break
        size_text = size_line.strip().split(b";", 1)[0]
        if not size_text:
            continue
        size = int(size_text, 16)
        if size == 0:
            while True:
                line = handler.rfile.readline()
                if line in (b"\r\n", b"\n", b""):
                    break
            break
        chunks.append(handler.rfile.read(size))
        handler.rfile.read(2)
    return b"".join(chunks)


def read_request_body(handler: BaseHTTPRequestHandler) -> bytes:
    encoding = (handler.headers.get("Transfer-Encoding") or "").lower()
    if "chunked" in encoding:
        return read_chunked_body(handler)

    length_header = handler.headers.get("Content-Length")
    if length_header:
        return handler.rfile.read(int(length_header))
    return b""


def parse_multipart(content_type: str, body: bytes) -> tuple[str, str, bytes, dict[str, str]]:
    filename = "upload.bin"
    mime_type = "application/octet-stream"
    file_bytes = body
    metadata: dict[str, str] = {}

    if "multipart/form-data" not in content_type:
        return filename, mime_type, file_bytes, metadata

    header = f"Content-Type: {content_type}\r\nMIME-Version: 1.0\r\n\r\n".encode("utf-8")
    message = BytesParser(policy=policy.default).parsebytes(header + body)

    if not message.is_multipart():
        return filename, mime_type, file_bytes, metadata

    for part in message.iter_parts():
        disposition = part.get_content_disposition()
        name = part.get_param("name", header="content-disposition") or ""
        part_filename = part.get_filename()
        if disposition != "form-data":
            continue

        payload = part.get_payload(decode=True)
        if payload is None:
            payload = b""
        if isinstance(payload, str):
            payload = payload.encode("utf-8")

        if name.startswith("metadata["):
            key = name[len("metadata[") : -1] if name.endswith("]") else name
            metadata[key] = payload.decode("utf-8", errors="replace")
            continue

        if name not in ("files", "file") and not part_filename:
            continue

        if part_filename:
            filename = part_filename
        part_mime = part.get_content_type()
        if part_mime and part_mime != "text/plain":
            mime_type = part_mime
        else:
            guessed = mimetypes.guess_type(filename)[0]
            if guessed:
                mime_type = guessed
        file_bytes = payload

    return filename, mime_type, file_bytes, metadata


class DmStoreHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format: str, *args) -> None:  # noqa: A003
        print(f"[dm-store-stub] {self.address_string()} - {format % args}", flush=True)

    def do_GET(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path in ("/health", "/"):
            self._json(200, {"status": "UP"})
            return

        match = re.fullmatch(r"/documents/([0-9a-fA-F-]{36})(/binary)?", path)
        if not match:
            self._json(404, {"message": f"Not found: {path}"})
            return

        document_id = match.group(1).lower()
        document = DOCUMENTS.get(document_id)
        if document is None:
            self._json(404, {"message": f"Document not found: {document_id}"})
            return

        if match.group(2):
            body = document["content"]
            self.send_response(200)
            self.send_header("Content-Type", document["mimeType"])
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        self._json(200, self._metadata(document_id, document))

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path != "/documents":
            self._json(404, {"message": f"Not found: {path}"})
            return

        body = read_request_body(self)
        content_type = self.headers.get("Content-Type", "")
        filename, mime_type, file_bytes, metadata = parse_multipart(content_type, body)

        document_id = str(uuid.uuid4())
        DOCUMENTS[document_id] = {
            "originalDocumentName": filename,
            "mimeType": mime_type,
            "size": len(file_bytes),
            "content": file_bytes,
            "metadata": metadata,
        }
        save_documents()

        response = {
            "_embedded": {
                "documents": [self._metadata(document_id, DOCUMENTS[document_id])]
            }
        }
        self._json(200, response)

    def do_PATCH(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        body = read_request_body(self)

        if path == "/documents":
            try:
                payload = json.loads(body.decode("utf-8") or "{}")
            except json.JSONDecodeError:
                self._json(400, {"message": "Invalid JSON"})
                return

            for update in payload.get("documents", []):
                document_id = str(update.get("documentId") or update.get("id") or "").lower()
                metadata = update.get("metadata") or {}
                document = DOCUMENTS.get(document_id)
                if document is None:
                    continue
                document.setdefault("metadata", {}).update(
                    {str(key): str(value) for key, value in metadata.items()}
                )
            save_documents()

            self.send_response(204)
            self.send_header("Content-Length", "0")
            self.send_header("Connection", "close")
            self.end_headers()
            return

        match = re.fullmatch(r"/documents/([0-9a-fA-F-]{36})", path)
        if not match:
            self._json(404, {"message": f"Not found: {path}"})
            return

        document_id = match.group(1).lower()
        document = DOCUMENTS.get(document_id)
        if document is None:
            self._json(404, {"message": f"Document not found: {document_id}"})
            return

        try:
            payload = json.loads(body.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            self._json(400, {"message": "Invalid JSON"})
            return

        metadata = payload.get("metadata") or {}
        document.setdefault("metadata", {}).update(
            {str(key): str(value) for key, value in metadata.items()}
        )
        save_documents()
        self._json(200, self._metadata(document_id, document))

    def _metadata(self, document_id: str, document: dict) -> dict:
        return {
            "originalDocumentName": document["originalDocumentName"],
            "mimeType": document["mimeType"],
            "size": document["size"],
            "classification": "PUBLIC",
            "metadata": document.get("metadata") or {},
            "_links": {
                "self": {"href": f"{BASE_URL}/documents/{document_id}"},
                "binary": {"href": f"{BASE_URL}/documents/{document_id}/binary"},
            },
        }

    def _json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)


def main() -> None:
    load_documents()
    server = ThreadingHTTPServer((HOST, PORT), DmStoreHandler)
    print(
        f"[dm-store-stub] listening on {BASE_URL} "
        f"({len(DOCUMENTS)} persisted document(s) in {DATA_DIR})",
        flush=True,
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
