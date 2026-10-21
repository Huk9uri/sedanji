"""YouTube Data API v3 snippet PoC. Python 3.10+, no dependencies."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import Request, urlopen


class PocError(Exception):
    pass


def extract_video_id(url):
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or parsed.username or parsed.password:
        raise PocError("유효한 http(s) YouTube URL을 입력하세요.")
    host = (parsed.hostname or "").lower()
    parts = parsed.path.strip("/").split("/")
    video_id = ""
    if host in ("youtube.com", "www.youtube.com", "m.youtube.com"):
        if parsed.path == "/watch":
            values = parse_qs(parsed.query).get("v", [])
            video_id = values[0] if len(values) == 1 else ""
        elif len(parts) == 2 and parts[0] in ("shorts", "embed", "live"):
            video_id = parts[1]
    elif host == "youtu.be" and len(parts) == 1:
        video_id = parts[0]
    if not re.fullmatch(r"[A-Za-z0-9_-]{11}", video_id):
        raise PocError("지원하는 YouTube URL과 11자리 videoId를 확인하세요.")
    return video_id


def load_api_key():
    # Read only the required variable. Never execute .env contents or log secrets.
    if not os.environ.get("YOUTUBE_API_KEY"):
        env_path = Path(__file__).with_name(".env")
        if env_path.exists():
            for line in env_path.read_text(encoding="utf-8-sig").splitlines():
                name, sep, value = line.strip().partition("=")
                if sep and name.strip() == "YOUTUBE_API_KEY":
                    os.environ["YOUTUBE_API_KEY"] = value.strip().strip("\"'")
                    break
    key = os.environ.get("YOUTUBE_API_KEY", "").strip()
    if not key or key == "YOUR_YOUTUBE_DATA_API_KEY":
        raise PocError("YOUTUBE_API_KEY 환경변수를 설정하세요 (.env 지원).")
    return key


def fetch_snippet(video_id, api_key):
    query = urlencode({"part": "snippet", "id": video_id})
    # Header keeps the API key out of request URLs and URL-based error messages.
    request = Request("https://www.googleapis.com/youtube/v3/videos?" + query,
                      headers={"X-Goog-Api-Key": api_key, "Accept": "application/json"})
    try:
        with urlopen(request, timeout=20) as response:
            data = json.load(response)
    except HTTPError as exc:
        reason = "unknown"
        try:
            error = json.loads(exc.read()).get("error", {})
            reason = error.get("errors", [{}])[0].get("reason", "unknown")
        except (ValueError, IndexError, AttributeError, TypeError):
            pass
        # Do not echo server text, request headers, or exception URLs.
        safe_reason = re.sub(r"[^A-Za-z0-9_-]", "", str(reason))[:80]
        raise PocError(f"YouTube API 실패: HTTP {exc.code}, reason={safe_reason}. 키·API 활성화·제한·할당량을 확인하세요.") from None
    except (URLError, TimeoutError, OSError):
        raise PocError("YouTube API 네트워크/타임아웃 오류입니다. 연결을 확인하세요.") from None
    except (ValueError, UnicodeError):
        raise PocError("YouTube API 응답이 올바른 JSON이 아닙니다.") from None
    if not isinstance(data, dict) or not isinstance(data.get("items"), list):
        raise PocError("YouTube API 응답 형식이 예상과 다릅니다.")
    if not data["items"]:
        raise PocError("조회 가능한 영상이 없습니다. 비공개·삭제·존재하지 않는 ID 등을 이 응답만으로 구분할 수 없습니다.")
    item = data["items"][0]
    if not isinstance(item, dict) or not isinstance(item.get("snippet"), dict):
        raise PocError("YouTube API 응답에 snippet이 없습니다.")
    return item["snippet"]


def inspect_description(description):
    patterns = {
        "식당명 후보 줄 (수동 확인 필요)": r"식당|맛집|상호|가게|레스토랑|식당명|restaurant|\[[^\]]+\]",
        "주소 후보 줄 (수동 확인 필요)": r"주소|address|(?:서울|부산|대구|인천|광주|대전|울산|제주|경기|강원)|[가-힣]+(?:로|길)\s*\d+",
        "장소 링크 후보 줄 (수동 확인 필요)": r"(?:map\.naver\.com|naver\.me|place\.naver\.com|maps?\.google\.|maps\.app\.goo\.gl|goo\.gl/maps|kko\.to|map\.kakao\.com)",
        "타임스탬프 후보 줄": r"(?<![\d:])(?:\d{1,2}:)?\d{1,3}:[0-5]\d(?![\d:])",
    }
    checks = {label: [{"line": n, "text": line} for n, line in enumerate(description.splitlines(), 1)
                    if re.search(pattern, line, re.IGNORECASE)]
            for label, pattern in patterns.items()}
    lines = description.splitlines()
    names = checks["식당명 후보 줄 (수동 확인 필요)"]
    for address in checks["주소 후보 줄 (수동 확인 필요)"]:
        previous = address["line"] - 1
        if previous > 0 and lines[previous - 1].strip():
            candidate = {"line": previous, "text": lines[previous - 1]}
            if candidate not in names:
                names.append(candidate)
    names.sort(key=lambda candidate: candidate["line"])
    return checks


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("url", help="YouTube video URL")
    args = parser.parse_args()
    try:
        video_id = extract_video_id(args.url)
        snippet = fetch_snippet(video_id, load_api_key())
        description = snippet.get("description", "")
        print(json.dumps({"videoId": video_id, "title": snippet.get("title"),
                          "channelTitle": snippet.get("channelTitle"),
                          "publishedAt": snippet.get("publishedAt"),
                          "description": description,
                          "descriptionChecks": inspect_description(description),
                          "note": "후보 없음은 정보 부재를 확정하지 않습니다. 설명란 원문을 함께 확인하세요."},
                         ensure_ascii=False, indent=2))
        return 0
    except PocError as exc:
        print(f"오류: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
