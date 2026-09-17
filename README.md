RadioJavan Downloader
This repository contains a manually triggered GitHub Actions workflow that resolves one Radio Javan page, validates the resulting MP3, and publishes it as an asset of the `radiojavan-downloads` GitHub Release.
Installation
Place the files in these paths:
```text
.github/workflows/radiojavan.yml
scripts/download\_podcast.py
```
Then open the Actions tab, select Radio Javan Downloader, choose Run workflow, enter a Radio Javan page URL, and start the run. The uploaded file is available under the repository's `radiojavan-downloads` Release.
Safety and reliability changes
Only HTTPS Radio Javan page URLs are accepted.
Redirects are limited and validated at every hop.
Private or loopback DNS destinations are rejected.
Media URLs must remain on a Radio Javan domain.
Page and MP3 size limits are enforced.
The response content type and MP3 signature are checked.
File names are sanitized and existing files are never overwritten locally.
A short source-URL hash prevents different tracks with the same slug from colliding.
Concurrent runs are serialized.
Downloaded media is stored as a Release asset instead of being committed to Git history.
Push errors cannot be hidden because the workflow no longer uses `|| true`.
Important
Use this workflow only for content you are authorized to download and redistribute. Radio Javan's terms, copyright rules, and the rights of the content owner still apply.
