import sys
import unittest
from pathlib import Path


project_root = Path(__file__).resolve().parents[1]
script_dir = project_root / "scripts"
sys.path.insert(0, str(script_dir if (script_dir / "update_podcast_catalog.py").exists() else project_root))
from update_podcast_catalog import PodcastRows


class PodcastRowsTest(unittest.TestCase):
    def test_matches_cover_and_title_within_each_row(self) -> None:
        html = """
        <div class="track-row podcast track-item">
          <div><img srcset="/_next/image?url=cover&amp;w=128&amp;q=75 1x,
            /_next/image?url=cover&amp;w=256&amp;q=75 2x">
            <div><a href="/podcast/epic-3"><span>EPIC</span> 3</a></div>
          </div>
        </div>
        <div class="track-row podcast track-item">
          <div><img src="/_next/image?url=second&amp;w=256&amp;q=75">
            <div><a href="/podcast/shans-4">Shans 4</a></div>
          </div>
        </div>
        """
        parser = PodcastRows()
        parser.feed(html)

        self.assertEqual([episode["title"] for episode in parser.episodes], ["EPIC 3", "Shans 4"])
        self.assertTrue(parser.episodes[0]["image_url"].endswith("w=256&q=75"))
        self.assertTrue(parser.episodes[1]["image_url"].endswith("w=256&q=75"))

    def test_ignores_duplicate_and_non_episode_links(self) -> None:
        row = """<div class="track-row podcast"><img src="/_next/image?url=x&amp;w=256&amp;q=75">
          <a href="/podcast/episode-1">Episode</a><a href="/podcast/show/test">Show</a></div>"""
        parser = PodcastRows()
        parser.feed(row + row)

        self.assertEqual(len(parser.episodes), 1)
        self.assertEqual(parser.episodes[0]["url"], "https://play.radiojavan.com/podcast/episode-1")


if __name__ == "__main__":
    unittest.main()
