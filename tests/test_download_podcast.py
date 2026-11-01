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


class PodcastMediaUrlTest(unittest.TestCase):
    def extract(self, page: str) -> str:
        with patch("download_podcast.validate_url"):
            return download_podcast.extract_media_url(
                page, "https://play.radiojavan.com/podcast/example"
            )

    def test_selects_matching_full_track_instead_of_preview_or_recommendation(self) -> None:
        page = """
        <meta name="apple-itunes-app" content="app-argument=radiojavan://podcast/5346">
        "preview_link":"https://host2.rjmedia-content.app/media/podcast/preview/5346-preview.mp3"
        "link":"https://host2.rjmedia-content.app/media/podcast/mp3-192/5346-full.mp3"
        "link":"https://host2.rjmedia-content.app/media/podcast/mp3-192/5118-other.mp3"
        """
        self.assertEqual(
            self.extract(page),
            "https://host2.rjmedia-content.app/media/podcast/mp3-192/5346-full.mp3",
        )

    def test_rejects_preview_when_current_item_has_no_full_track(self) -> None:
        page = """
        <meta name="apple-itunes-app" content="app-argument=radiojavan://podcast/5492">
        "preview_link":"https://host2.rjmedia-content.app/media/podcast/preview/5492-preview.mp3"
        "link":"https://host2.rjmedia-content.app/media/podcast/mp3-192/5118-other.mp3"
        """
        with self.assertRaisesRegex(download_podcast.DownloadError, "Only a preview MP3"):
            self.extract(page)

    def test_rejects_premium_labeled_page_even_if_a_full_url_is_present(self) -> None:
        page = """
        <p>Included with Premium</p>
        <meta name="apple-itunes-app" content="app-argument=radiojavan://podcast/5492">
        "link":"https://host2.rjmedia-content.app/media/podcast/mp3-192/5492-full.mp3"
        """
        with self.assertRaisesRegex(download_podcast.DownloadError, "marks this item as Premium"):
            self.extract(page)

    def test_does_not_guess_between_preview_and_unidentified_media(self) -> None:
        page = """
        "preview_link":"https://host2.rjmedia-content.app/media/podcast/preview/5492-preview.mp3"
        "link":"https://host2.rjmedia-content.app/media/podcast/mp3-192/5118-other.mp3"
        """
        with self.assertRaisesRegex(
            download_podcast.DownloadError, "requested item could not be identified"
        ):
            self.extract(page)


if __name__ == "__main__":
    unittest.main()
