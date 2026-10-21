import io
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

import kakao_local_poc as poc


def candidate(id="1", name="뽀요베리", road="서울특별시 마포구 월드컵로13길 55-15", jibun="서울 마포구 망원동 1"):
    return {"id": id, "place_name": name, "road_address_name": road, "address_name": jibun,
            "x": "126.9", "y": "37.5", "place_url": "http://place.map.kakao.com/" + id}


def response(docs, total=None, end=True):
    return {"documents": docs, "meta": {"is_end": end, "total_count": len(docs) if total is None else total,
                                        "pageable_count": len(docs) if total is None else min(total, 45)}}


class MatchingTests(unittest.TestCase):
    def evaluate(self, docs, target=None, complete=True):
        return poc.evaluate(target or poc.TARGETS[0], docs, complete)

    def test_normalized_match(self):
        result = self.evaluate([candidate(name="뽀요 베리")])
        self.assertEqual(result["status"], "MATCHED")
        self.assertEqual(result["selected_place_id"], "1")

    def test_name_only_wrong_address(self):
        for road in ("서울 마포구 월드컵로13길 55", "서울 마포구 월드컵로13길 55-16", "부산 마포구 월드컵로13길 55-15"):
            with self.subTest(road=road):
                self.assertEqual(self.evaluate([candidate(road=road)])["status"], "REVIEW_REQUIRED")

    def test_branches_distinguished_by_address(self):
        result = self.evaluate([candidate(name="뽀요베리 망원점"),
                                candidate("2", "뽀요베리 강남점", "서울 강남구 테헤란로 1")])
        self.assertEqual(result["status"], "MATCHED")
        self.assertEqual(result["selected_place_id"], "1")

    def test_multiple_likely_candidates(self):
        self.assertEqual(self.evaluate([candidate(), candidate("2")])["status"], "REVIEW_REQUIRED")

    def test_near_name_same_address_blocks_match(self):
        self.assertEqual(self.evaluate([candidate(), candidate("2", "뽀요베리바")])["status"], "REVIEW_REQUIRED")

    def test_missing_road_does_not_infer_conversion(self):
        self.assertEqual(self.evaluate([candidate(road="")])["status"], "REVIEW_REQUIRED")

    def test_jibun_input(self):
        self.assertEqual(self.evaluate([candidate(road="")], {"name": "뽀요베리", "address": "서울 마포구 망원동 1"})["status"], "MATCHED")

    def test_empty_results(self):
        self.assertEqual(self.evaluate([])["status"], "NOT_FOUND")

    def test_incomplete_search(self):
        self.assertEqual(self.evaluate([candidate()], complete=False)["status"], "REVIEW_REQUIRED")

    def test_floor_and_missing_address_require_review(self):
        for road in ("", "서울 마포구 월드컵로13길 55-15 2층"):
            self.assertEqual(self.evaluate([candidate(road=road)])["status"], "REVIEW_REQUIRED")


class ApiTests(unittest.TestCase):
    def test_disabled_service_safe_diagnostic(self):
        error = HTTPError("secret", 403, "secret", {}, io.BytesIO(b'{"message":"secret service disabled"}'))
        with patch.object(poc, "urlopen", side_effect=error), self.assertRaises(poc.PocError) as exc:
            poc.fetch_page("name", "secret", 1)
        self.assertIn("비활성화", str(exc.exception))
        self.assertNotIn("secret", str(exc.exception))

    def test_request_and_success(self):
        with patch.object(poc, "urlopen", return_value=io.BytesIO(json.dumps(response([candidate()])).encode())) as mock:
            self.assertEqual(poc.fetch_page("뽀요베리", "test-secret", 1)["documents"][0]["id"], "1")
            request = mock.call_args.args[0]
            self.assertEqual(request.get_header("Authorization"), "KakaoAK test-secret")
            self.assertNotIn("test-secret", request.full_url)
            self.assertIn("size=15", request.full_url)

    def test_auth_quota_and_http_errors_hide_secrets(self):
        for code in (400, 401, 403, 429, 500):
            error = HTTPError("secret", code, "secret", {}, io.BytesIO(b'{"message":"secret"}'))
            with self.subTest(code=code), patch.object(poc, "urlopen", side_effect=error), self.assertRaises(poc.PocError) as exc:
                poc.fetch_page("name", "secret", 1)
            self.assertIn(str(code), str(exc.exception))
            self.assertNotIn("secret", str(exc.exception))

    def test_network_errors_hide_secrets(self):
        for error in (URLError("secret"), TimeoutError("secret")):
            with patch.object(poc, "urlopen", side_effect=error), self.assertRaises(poc.PocError) as exc:
                poc.fetch_page("name", "secret", 1)
            self.assertNotIn("secret", str(exc.exception))

    def test_invalid_json(self):
        with patch.object(poc, "urlopen", return_value=io.BytesIO(b'not-json')), self.assertRaisesRegex(poc.PocError, "JSON"):
            poc.fetch_page("name", "secret", 1)

    def test_malformed_responses(self):
        cases = [[], {}, response([{}]), response([candidate()])]
        cases[-1]["meta"]["is_end"] = "true"
        for field, value in (("x", "nan"), ("y", "91"), ("id", ""), ("place_url", "https://evil.test"), ("road_address_name", None)):
            doc = candidate()
            doc[field] = value
            cases.append(response([doc]))
        for body in cases:
            with self.subTest(body=body), self.assertRaises(poc.PocError):
                poc.validate_response(body)

    def test_pagination_deduplication(self):
        with patch.object(poc, "fetch_page", side_effect=[response([candidate()], 2, False), response([candidate(), candidate("2")], 2)]) as mock:
            docs, meta = poc.search_places("name", "secret")
        self.assertEqual(len(docs), 2)
        self.assertTrue(meta["complete"])
        self.assertEqual(mock.call_args.args[-1], 2)

    def test_capped_search_cannot_auto_match(self):
        with patch.object(poc, "fetch_page", return_value=response([candidate()], 100, False)) as mock:
            docs, meta = poc.search_places("name", "secret")
        self.assertEqual(mock.call_count, 3)
        self.assertFalse(meta["complete"])

    def test_empty_search_is_complete(self):
        with patch.object(poc, "fetch_page", return_value=response([])):
            docs, meta = poc.search_places("name", "secret")
        self.assertEqual(poc.evaluate(poc.TARGETS[0], docs, meta["complete"])["status"], "NOT_FOUND")

    def test_key_loading_precedence_and_missing(self):
        with patch.dict(os.environ, {"KAKAO_REST_API_KEY": "environment"}), patch.object(Path, "read_text", side_effect=AssertionError):
            self.assertEqual(poc.load_api_key(), "environment")
        with patch.dict(os.environ, {}, clear=True), patch.object(Path, "read_text", return_value='KAKAO_REST_API_KEY="dotenv"'):
            self.assertEqual(poc.load_api_key(), "dotenv")
        with patch.dict(os.environ, {}, clear=True), patch.object(Path, "read_text", side_effect=FileNotFoundError), self.assertRaises(poc.PocError):
            poc.load_api_key()

    def test_cli_failure_not_not_found(self):
        with patch.object(poc, "load_api_key", return_value="secret"), patch.object(poc, "search_places", side_effect=poc.PocError("HTTP 401")), patch.object(poc.sys, "argv", ["poc"]), patch("sys.stdout", new_callable=io.StringIO) as output:
            self.assertEqual(poc.main(), 1)
        report = json.loads(output.getvalue())
        self.assertTrue(all(not r["api_ok"] and r["status"] == "REVIEW_REQUIRED" for r in report["results"]))
        self.assertNotIn("secret", output.getvalue())


if __name__ == "__main__":
    unittest.main()
