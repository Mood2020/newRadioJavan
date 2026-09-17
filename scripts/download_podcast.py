#!/usr/bin/env python3
"""Safely resolve and download one Radio Javan MP3."""

from __future__ import annotations

import hashlib
import html
import ipaddress
import os
import re
import socket
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, unquote, urljoin, urlsplit, urlunsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


PAGE_HOSTS = {
    "radiojavan.com",
    "www.radiojavan.com",
    "play.radiojavan.com",
    "www.play.radiojavan.com",
}
SHARE_HOSTS = {"rj.app", "www.rj.app"}
# Radio Javan currently serves podcast audio from this CDN.
MEDIA_DOMAINS = ("radiojavan.com", "rjmedia-content.app")
MAX_REDIRECTS = 4
MAX_PAGE_BYTES = 5 * 1024 * 1024
MAX_MEDIA_BYTES = 100 * 1024 * 1024
CHUNK_SIZE = 1024 * 1024
REDIRECT_CODES = {301, 302, 303, 307, 308}

PAGE_HEADERS = {
    "User-Agent": "RadioJavan-Downloader/1.0",
    "Referer": "https://play.radiojavan.com/",
    "Accept": "text/html,application/xhtml+xml",
}
MEDIA_HEADERS = {
    "User-Agent": "RadioJavan-Downloader/1.0",
    "Referer": "https://play.radiojavan.com/",
    "Accept": "audio/mpeg,audio/*;q=0.9,application/octet-stream;q=0.8",
}


class DownloadError(RuntimeError):
    """A user-facing download or validation failure."""


class NoRedirectHandler(HTTPRedirectHandler):
    """Expose redirects so every hop can be validated before following it."""

    def redirect_request(self, *args, **kwargs):  # type: ignore[no-untyped-def]
        return None


OPENER = build_opener(NoRedirectHandler())
MP3_URL_RE = re.compile(
    r"(?i)(?:https?:)?(?://|\\/\\/)[^\"'\s<>]+?\.mp3(?:\?[^\"'\s<>]*)?"
)
LINK_VALUE_RE = re.compile(r'(?i)["\']link["\']\s*:\s*["\']([^"\']+)["\']')


def fail(message: str) -> DownloadError:
    return DownloadError(message)


def host_is_allowed(host: str, role: str) -> bool:
    host = host.lower().rstrip(".")
    if role == "page":
        return host in PAGE_HOSTS
    if role == "share":
        return host in PAGE_HOSTS or host in SHARE_HOSTS
    return any(host == domain or host.endswith(f".{domain}") for domain in MEDIA_DOMAINS)


def reject_private_dns(host: str) -> None:
    try:
        addresses = {
            info[4][0]
            for info in socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
        }
    except socket.gaierror as exc:
        raise fail(f"Could not resolve host: {host}") from exc

    for address in addresses:
        parsed = ipaddress.ip_address(address)
        if not parsed.is_global:
            raise fail(f"Host resolves to a non-public address: {host}")


def validate_url(url: str, role: str) -> None:
    parts = urlsplit(url)
    host = parts.hostname
    if parts.scheme != "https" or not host:
        raise fail("Only HTTPS URLs are allowed")
    if parts.username or parts.password:
        raise fail("URLs with embedded credentials are not allowed")
    if parts.port not in (None, 443):
        raise fail("Only the default HTTPS port is allowed")
    if not host_is_allowed(host, role):
        raise fail(f"Host is not allowed: {host}")
    reject_private_dns(host)


def safe_open(url: str, role: str, headers: dict[str, str]):
    current = url
    for _ in range(MAX_REDIRECTS + 1):
        validate_url(current, role)
        request = Request(current, headers=headers, method="GET")
        try:
            response = OPENER.open(request, timeout=30)
        except HTTPError as exc:
            if exc.code not in REDIRECT_CODES:
                raise fail(f"HTTP {exc.code} while fetching {current}") from exc
            location = exc.headers.get("Location")
            if not location:
                raise fail("Redirect did not include a Location header") from exc
            current = urljoin(current, location)
            continue
        except URLError as exc:
            raise fail(f"Network error while fetching {current}") from exc

        if response.status in REDIRECT_CODES:
            location = response.headers.get("Location")
            response.close()
            if not location:
                raise fail("Redirect did not include a Location header")
            current = urljoin(current, location)
            continue
        if response.status != 200:
            response.close()
            raise fail(f"HTTP {response.status} while fetching {current}")
        return response, current

    raise fail("Too many redirects")


def read_page(url: str) -> tuple[str, str]:
    response, final_url = safe_open(url, "page", PAGE_HEADERS)
    try:
        length = response.headers.get("Content-Length")
        if length and int(length) > MAX_PAGE_BYTES:
            raise fail("The page is larger than the allowed limit")
        content = response.read(MAX_PAGE_BYTES + 1)
    finally:
        response.close()
    if len(content) > MAX_PAGE_BYTES:
        raise fail("The page is larger than the allowed limit")
    return content.decode("utf-8", errors="replace"), final_url


def canonical_page_url(url: str) -> str:
    parts = urlsplit(url)
    return urlunsplit((parts.scheme, parts.netloc, parts.path.rstrip("/") or "/", "", ""))


def resolve_input_url(source_url: str) -> str:
    """Convert direct, short, and app deep links to a web page URL."""
    parts = urlsplit(source_url)
    host = (parts.hostname or "").lower().rstrip(".")

    if host in SHARE_HOSTS:
        validate_url(source_url, "share")
        response, resolved_url = safe_open(source_url, "share", PAGE_HEADERS)
        response.close()
        resolved_parts = urlsplit(resolved_url)
        if not host_is_allowed(resolved_parts.hostname or "", "page"):
            raise fail("The short link did not resolve to a Radio Javan page")
        return canonical_page_url(resolved_url)

    validate_url(source_url, "page")
    if host == "play.radiojavan.com" and parts.path.rstrip("/") == "/redirect":
        deep_link = parse_qs(parts.query).get("r", [""])[0]
        match = re.fullmatch(r"radiojavan://(podcast|song)/([A-Za-z0-9_-]+)", deep_link)
        if not match:
            raise fail("Unsupported Radio Javan app link")
        return f"https://play.radiojavan.com/{match.group(1)}/{match.group(2)}"

    return canonical_page_url(source_url)


def normalize_candidate(raw: str, page_url: str) -> str:
    value = html.unescape(raw).strip()
    value = value.replace("\\/", "/")
    value = value.replace("\\u002F", "/").replace("\\u002f", "/")
    value = value.replace("\\u0026", "&")
    return urljoin(page_url, value)


def extract_media_url(page: str, page_url: str) -> str:
    candidates = MP3_URL_RE.findall(page)
    candidates.extend(LINK_VALUE_RE.findall(page))
    for raw in candidates:
        candidate = normalize_candidate(raw, page_url)
        try:
            validate_url(candidate, "media")
        except DownloadError:
            continue
        if urlsplit(candidate).path.lower().endswith(".mp3"):
            return candidate
    raise fail("Could not find an allowed MP3 URL in the page")


def safe_filename(source_url: str) -> str:
    name = unquote(Path(urlsplit(source_url).path).name)
    if name.lower().endswith(".mp3"):
        name = name[:-4]
    name = re.sub(r"[^A-Za-z0-9._-]+", "-", name).strip(".-")
    name = name[:100].strip(".-") or "track"
    source_suffix = hashlib.sha256(source_url.encode("utf-8")).hexdigest()[:8]
    return f"{name}-{source_suffix}.mp3"


def looks_like_mp3(data: bytes) -> bool:
    if data.startswith(b"ID3"):
        return True
    return any(
        data[index] == 0xFF and data[index + 1] & 0xE0 == 0xE0
        for index in range(max(0, min(len(data) - 1, 4096)))
    )


def set_github_output(name: str, value: str) -> None:
    output_file = os.environ.get("GITHUB_OUTPUT")
    if not output_file:
        return
    with open(output_file, "a", encoding="utf-8") as handle:
        handle.write(f"{name}={value}\n")


def download_media(media_url: str, destination: Path) -> tuple[int, str]:
    destination.parent.mkdir(parents=True, exist_ok=True)
    response, _ = safe_open(media_url, "media", MEDIA_HEADERS)
    try:
        content_type = response.headers.get_content_type().lower()
        if content_type and not (
            content_type.startswith("audio/")
            or content_type in {"application/octet-stream", "binary/octet-stream"}
        ):
            raise fail(f"Unexpected media type: {content_type}")

        length = response.headers.get("Content-Length")
        if length and int(length) > MAX_MEDIA_BYTES:
            raise fail("The media file is larger than the allowed limit")

        partial = destination.with_name(f".{destination.name}.part")
        total = 0
        digest = hashlib.sha256()
        first_chunk = b""
        try:
            with open(partial, "wb") as handle:
                while chunk := response.read(CHUNK_SIZE):
                    if not first_chunk:
                        first_chunk = chunk[:4096]
                    total += len(chunk)
                    if total > MAX_MEDIA_BYTES:
                        raise fail("The media file is larger than the allowed limit")
                    digest.update(chunk)
                    handle.write(chunk)
            if not looks_like_mp3(first_chunk):
                raise fail("The downloaded response does not look like an MP3")
            partial.replace(destination)
        finally:
            partial.unlink(missing_ok=True)
    finally:
        response.close()
    return total, digest.hexdigest()


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: download_podcast.py <Radio Javan URL>", file=sys.stderr)
        return 2

    source_url = sys.argv[1].strip()
    try:
        page_url = resolve_input_url(source_url)
        page, final_page_url = read_page(page_url)
        media_url = extract_media_url(page, final_page_url)
        destination = Path("podcasts") / safe_filename(page_url)
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            raise fail(f"Refusing to overwrite existing file: {destination}")
        size, digest = download_media(media_url, destination)
    except (DownloadError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    print(f"Downloaded {size} bytes to {destination}")
    print(f"SHA-256: {digest}")
    set_github_output("file_path", str(destination))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
