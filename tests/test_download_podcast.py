import sys
import unittest
from pathlib import Path
from unittest.mock import patch


project_root = Path(__file__).resolve().parents[1]
script_dir = project_root / "scripts"
sys.path.insert(0, str(script_dir if (script_dir / "download_podcast.py").exists() else project_root))
import download_podcast


class SongUrlTest(unittest.TestCase):
    def test_direct_song_page_is_supported(self) -> None:
        url = "https://play.radiojavan.com/song/googoosh-gharibeh-ashena"
        with patch("download_podcast.validate_url"):
            self.assertEqual(download_podcast.resolve_input_url(url), url)

    def test_shared_song_deep_link_is_supported(self) -> None:
        url = "https://play.radiojavan.com/redirect?r=radiojavan://song/12345"
        with patch("download_podcast.validate_url"):
            self.assertEqual(
                download_podcast.resolve_input_url(url),
                "https://play.radiojavan.com/song/12345",
            )


if __name__ == "__main__":
    unittest.main()
