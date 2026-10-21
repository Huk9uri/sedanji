"""Fetch five diverse search results via official YouTube Data API only."""
import json
from pathlib import Path
import sys
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from youtube_poc import fetch_snippet, load_api_key


def main():
    key = load_api_key()
    query = urlencode({"part": "snippet", "type": "video", "q": "셰프 맛집", "maxResults": 5})
    request = Request("https://www.googleapis.com/youtube/v3/search?" + query,
                      headers={"X-Goog-Api-Key": key})
    try:
        with urlopen(request, timeout=20) as response:
            search = json.load(response)
        videos = []
        for item in search["items"]:
            video_id = item["id"]["videoId"]
            videos.append({"videoId": video_id, **fetch_snippet(video_id, key)})
        path = Path("results/additional_videos.json")
        path.parent.mkdir(exist_ok=True)
        path.write_text(json.dumps(videos, ensure_ascii=False, indent=2), encoding="utf-8")
        # No credentials or request exception details in stdout.
        for video in videos:
            print(json.dumps({k: video[k] for k in ("videoId", "title", "channelTitle", "description")}, ensure_ascii=False))
    except Exception:
        print("공식 YouTube 검색/메타데이터 검증 실패 (인증정보 비노출)", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
