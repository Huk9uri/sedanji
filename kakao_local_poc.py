"""Kakao Local keyword matching PoC (Python 3.10+, standard library only)."""
import argparse
from datetime import datetime, timezone
from difflib import SequenceMatcher
import json
import math
import os
from pathlib import Path
import re
import sys
import unicodedata
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

TARGETS = [
    {"name": "뽀요베리", "address": "서울 마포구 월드컵로13길 55-15"},
    {"name": "모리본", "address": "서울 광진구 긴고랑로15길 6"},
]
FIELDS = ("id", "place_name", "road_address_name", "address_name", "x", "y", "place_url")


class PocError(Exception):
    pass


def load_api_key():
    key = os.environ.get("KAKAO_REST_API_KEY", "").strip()
    if not key:
        try:
            lines = Path(__file__).with_name(".env").read_text(encoding="utf-8-sig").splitlines()
        except FileNotFoundError:
            lines = []
        except (OSError, UnicodeError):
            raise PocError(".env 읽기 실패") from None
        for line in lines:
            name, sep, value = line.strip().partition("=")
            if sep and name.strip() == "KAKAO_REST_API_KEY":
                key = value.strip().strip("\"'")
                break
    if not key or key == "YOUR_KAKAO_REST_API_KEY":
        raise PocError("KAKAO_REST_API_KEY 환경변수 또는 .env를 설정하세요.")
    return key


def normalize_name(value):
    return re.sub(r"[^가-힣a-z0-9]", "", unicodedata.normalize("NFKC", value).casefold())


def address_key(value):
    """Conservative Seoul road/jibun key; do not infer road-to-jibun conversion."""
    value = unicodedata.normalize("NFKC", value).strip()
    value = re.sub(r"^서울(?:특별시|시)?\s*", "서울 ", value)
    # Preserve building subnumbers. Floor/unit suffixes are not discarded for a match.
    match = re.fullmatch(r"서울\s+([가-힣]+구)\s+([가-힣0-9·]+(?:로|길|동|가))\s*(\d+(?:-\d+)?)", value)
    return tuple(match.groups()) if match else None


def validate_response(data):
    if not isinstance(data, dict) or not isinstance(data.get("documents"), list) or not isinstance(data.get("meta"), dict):
        raise PocError("응답 형식 오류: documents/meta")
    meta = data["meta"]
    if type(meta.get("is_end")) is not bool or any(type(meta.get(k)) is not int or meta[k] < 0 for k in ("total_count", "pageable_count")):
        raise PocError("응답 형식 오류: 페이지 메타데이터")
    for doc in data["documents"]:
        if not isinstance(doc, dict) or any(not isinstance(doc.get(k), str) for k in FIELDS):
            raise PocError("응답 형식 오류: 장소 필드")
        if not doc["id"].isdigit() or not doc["place_name"].strip() or not re.fullmatch(r"https?://place\.map\.kakao\.com/" + re.escape(doc["id"]), doc["place_url"]):
            raise PocError("응답 형식 오류: 장소 ID/이름/링크")
        try:
            x, y = float(doc["x"]), float(doc["y"])
            if not (math.isfinite(x) and math.isfinite(y) and -180 <= x <= 180 and -90 <= y <= 90):
                raise ValueError
        except ValueError:
            raise PocError("응답 형식 오류: 좌표") from None
    return data


def fetch_page(name, key, page):
    query = urlencode({"query": name, "page": page, "size": 15, "sort": "accuracy"})
    request = Request("https://dapi.kakao.com/v2/local/search/keyword.json?" + query,
                      headers={"Authorization": "KakaoAK " + key, "Accept": "application/json"})
    try:
        with urlopen(request, timeout=20) as response:
            data = json.load(response)
    except HTTPError as exc:
        labels = {401: "인증 실패: REST API 키 확인", 403: "권한 또는 쿼터 제한 확인", 429: "할당량/호출 빈도 초과"}
        detail = ""
        try:
            body = json.loads(exc.read())
            message = str(body.get("message", body.get("msg", ""))).lower() if isinstance(body, dict) else ""
            if "disabled" in message or "not enabled" in message:
                detail = " (API 기능 비활성화 감지: 앱의 카카오맵/로컬 사용 설정 확인)"
            elif "quota" in message or "limit exceeded" in message:
                detail = " (쿼터/사용량 제한 감지)"
        except (OSError, ValueError, UnicodeError):
            pass
        # Never echo response bodies, headers or exception text.
        raise PocError(f"HTTP {exc.code}: {labels.get(exc.code, 'API 요청 실패')}{detail}") from None
    except (URLError, TimeoutError, OSError):
        raise PocError("네트워크/타임아웃 오류") from None
    except (ValueError, UnicodeError):
        raise PocError("잘못된 JSON 응답") from None
    return validate_response(data)


def search_places(name, key):
    candidates = {}
    total = 0
    complete = False
    # Kakao keyword search exposes at most 45 documents, 15 per page.
    for page in range(1, 4):
        data = fetch_page(name, key, page)
        total = max(total, data["meta"]["total_count"])
        for doc in data["documents"]:
            candidates[doc["id"]] = {field: doc[field] for field in FIELDS}
        if data["meta"]["is_end"]:
            complete = len(candidates) >= total
            break
    return list(candidates.values()), {"total_count": total, "fetched_count": len(candidates),
                                      "pages_requested": page, "complete": complete}


def evaluate(target, candidates, complete=True):
    results = []
    name = normalize_name(target["name"])
    address = address_key(target["address"])
    for doc in candidates:
        normalized = normalize_name(doc["place_name"])
        exact = bool(name) and name == normalized
        # Branch suffixes are retained in output and always require address agreement.
        branch = bool(name) and normalized.startswith(name) and bool(re.fullmatch(r"[가-힣a-z0-9]+점", normalized[len(name):]))
        similarity = round(SequenceMatcher(None, name, normalized).ratio(), 3)
        address_sources = [field for field in ("road_address_name", "address_name")
                           if address is not None and address_key(doc[field]) == address]
        strong = (exact or branch) and bool(address_sources)
        results.append({**doc, "name_match": "EXACT" if exact else "BRANCH" if branch else "WEAK",
                        "name_similarity": similarity, "address_match": bool(address_sources),
                        "address_sources": address_sources, "strong_match": strong})
    strong = [doc for doc in results if doc["strong_match"]]
    ambiguous = [doc for doc in results if doc["address_match"] and doc["name_similarity"] >= 0.7]
    if not complete:
        status, reason = "REVIEW_REQUIRED", "전체 후보를 확보하지 못해 유일성을 확인할 수 없음"
    elif not results:
        status, reason = "NOT_FOUND", "정상 검색 응답에 후보 없음; 미등록/검색어 차이 등 수동 확인 필요"
    elif len(strong) == 1 and all(doc["id"] == strong[0]["id"] for doc in ambiguous):
        status, reason = "MATCHED", "이름과 상세 주소가 함께 일치하는 유일 후보"
    else:
        status, reason = "REVIEW_REQUIRED", "이름·주소 동시 일치 부족 또는 복수 유력 후보"
    return {"input": target, "status": status, "reason": reason,
            "selected_place_id": strong[0]["id"] if status == "MATCHED" else None,
            "candidates": results}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", help="추가 검증용 식당명 (--address와 함께 사용)")
    parser.add_argument("--address", help="설명란 주소")
    parser.add_argument("--output", type=Path, help="인증정보 없는 JSON 결과 저장 경로")
    args = parser.parse_args()
    if bool(args.name) != bool(args.address):
        parser.error("--name과 --address를 함께 지정하세요.")
    try:
        key = load_api_key()
    except PocError as exc:
        print(f"오류: {exc}", file=sys.stderr)
        return 1
    targets = [{"name": args.name, "address": args.address}] if args.name else TARGETS
    results = []
    failed = False
    for target in targets:
        try:
            candidates, meta = search_places(target["name"], key)
            results.append({"api_ok": True, "search": meta, **evaluate(target, candidates, meta["complete"])})
        except PocError as exc:
            failed = True
            results.append({"input": target, "api_ok": False, "status": "REVIEW_REQUIRED",
                            "error": str(exc), "selected_place_id": None, "candidates": []})
    report = {"queried_at": datetime.now(timezone.utc).isoformat(),
              "source_video": "https://www.youtube.com/watch?v=RMTJPFKRbEk" if not args.name else None,
              "results": results}
    output = json.dumps(report, ensure_ascii=False, indent=2)
    print(output)
    if args.output:
        try:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(output + "\n", encoding="utf-8")
        except OSError:
            print("오류: 결과 파일 저장 실패", file=sys.stderr)
            return 1
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
