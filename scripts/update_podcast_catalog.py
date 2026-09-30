#!/usr/bin/env python3
"""Refresh the small public feed used by the web and Android apps."""

from __future__ import annotations

import json
from html.parser import HTMLParser
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urljoin, urlsplit
from urllib.request import Request, urlopen


CATALOG_URL = "https://play.radiojavan.com/special/podcasts"
HEADERS = {
    "Accept": "text/html,application/xhtml+xml",
    "User-Agent": "Mozilla/5.0 (compatible; RadioJavanCatalog/1.0)",
}
MAX_PAGE_BYTES = 5 * 1024 * 1024
MAX_EPISODES = 12


def best_image_url(src: str, srcset: str) -> str:
    candidates = [entry.strip().split()[0] for entry in srcset.split(",") if entry.strip()]
    candidates.append(src)

    def width(candidate: str) -> int:
        try:
            return int(parse_qs(urlsplit(candidate).query).get("w", ["0"])[0])
        except ValueError:
            return 0

    candidate = max(candidates, key=width, default="")
    absolute = urljoin(CATALOG_URL, candidate)
    parsed = urlsplit(absolute)
    if parsed.scheme == "https" and parsed.hostname == "play.radiojavan.com" and parsed.path.startswith("/_next/image"):
        return absolute
    return ""


class PodcastRows(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.depth = 0
        self.row: dict[str, object] | None = None
        self.reading_title = False
        self.episodes: list[dict[str, str]] = []
        self.seen: set[str] = set()

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        classes = set((values.get("class") or "").split())
        if self.row is None:
            if tag == "div" and {"track-row", "podcast"}.issubset(classes):
                self.row = {"title": [], "url": "", "image_url": ""}
                self.depth = 1
            return

        if tag == "div":
            self.depth += 1
        elif tag == "a" and not self.row["url"]:
            href = values.get("href") or ""
            parts = urlsplit(href)
            if parts.path.startswith("/podcast/") and parts.path.count("/") == 2:
                self.row["url"] = urljoin(CATALOG_URL, href)
                self.reading_title = True
        elif tag == "img" and not self.row["image_url"]:
            self.row["image_url"] = best_image_url(values.get("src") or "", values.get("srcset") or "")

    def handle_data(self, data: str) -> None:
        if self.row is not None and self.reading_title:
            self.row["title"].append(data)

    def handle_endtag(self, tag: str) -> None:
        if self.row is None:
            return
        if tag == "a":
            self.reading_title = False
        elif tag == "div":
            self.depth -= 1
            if self.depth == 0:
                self._finish_row()

    def _finish_row(self) -> None:
        assert self.row is not None
        title = " ".join("".join(self.row["title"]).split())
        url = str(self.row["url"])
        image_url = str(self.row["image_url"])
        if title and url and url not in self.seen:
            self.episodes.append({"title": title, "url": url, "image_url": image_url})
            self.seen.add(url)
        self.row = None
        self.depth = 0
        self.reading_title = False


def fetch_episodes() -> list[dict[str, str]]:
    request = Request(CATALOG_URL, headers=HEADERS)
    with urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError(f"Radio Javan returned HTTP {response.status}")
        html = response.read(MAX_PAGE_BYTES + 1)
    if len(html) > MAX_PAGE_BYTES:
        raise RuntimeError("Radio Javan podcast page exceeded the 5 MB safety limit")

    parser = PodcastRows()
    parser.feed(html.decode("utf-8", errors="replace"))
    episodes = [episode for episode in parser.episodes if episode["image_url"]]
    if not episodes:
        raise RuntimeError("No podcast rows with cover art were found; the existing feed was left unchanged")
    return episodes[:MAX_EPISODES]


def main() -> None:
    try:
        episodes = fetch_episodes()
    except (HTTPError, URLError, TimeoutError, RuntimeError) as error:
        raise SystemExit(f"Could not refresh podcast catalog: {error}") from error

    payload = json.dumps({"episodes": episodes}, ensure_ascii=False, indent=2) + "\n"
    for path in (Path("podcast-catalog.json"), Path("site/podcast-catalog.json")):
        path.write_text(payload, encoding="utf-8")
    print(f"Wrote {len(episodes)} podcast episodes")


if __name__ == "__main__":
    main()
