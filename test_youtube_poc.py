import io
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

import youtube_poc as poc


class YoutubePocTests(unittest.TestCase):
    def test_urls(self):
        for url in ("https://www.youtube.com/watch?v=RMTJPFKRbEk&t=10",
                    "https://youtu.be/RMTJPFKRbEk", "https://youtube.com/shorts/RMTJPFKRbEk"):
            with self.subTest(url=url):
                self.assertEqual(poc.extract_video_id(url), "RMTJPFKRbEk")

    def test_invalid_urls(self):
        for url in ("invalid", "https://youtube.com.evil.test/watch?v=RMTJPFKRbEk",
                    "https://youtube.com/watch?v=short", "https://youtube.com/watch",
                    "https://youtube.com/watch?v=RMTJPFKRbEk&v=RMTJPFKRbEk"):
            with self.subTest(url=url), self.assertRaises(poc.PocError):
                poc.extract_video_id(url)

    def response(self, body):
        return patch.object(poc, "urlopen", return_value=io.BytesIO(body))

    def test_success_and_request(self):
        with self.response(b'{"items":[{"snippet":{"title":"test"}}]}') as mock:
            self.assertEqual(poc.fetch_snippet("RMTJPFKRbEk", "secret")["title"], "test")
            request = mock.call_args.args[0]
            self.assertIn("part=snippet", request.full_url)
            self.assertNotIn("secret", request.full_url)
            self.assertEqual(request.get_header("X-goog-api-key"), "secret")

    def test_unavailable(self):
        with self.response(b'{"items":[]}'), self.assertRaisesRegex(poc.PocError, "비공개"):
            poc.fetch_snippet("RMTJPFKRbEk", "secret")

    def test_http_failure_no_secret(self):
        error = HTTPError("https://example.test/?key=secret", 403, "secret", {},
                          io.BytesIO(b'{"error":{"errors":[{"reason":"quotaExceeded"}]}}'))
        with patch.object(poc, "urlopen", side_effect=error), self.assertRaises(poc.PocError) as result:
            poc.fetch_snippet("RMTJPFKRbEk", "secret")
        self.assertIn("quotaExceeded", str(result.exception))
        self.assertNotIn("secret", str(result.exception))

    def test_network_failure(self):
        with patch.object(poc, "urlopen", side_effect=URLError("secret")), self.assertRaises(poc.PocError):
            poc.fetch_snippet("RMTJPFKRbEk", "secret")

    def test_bad_response(self):
        for body in (b'not json', b'{}', b'[]', b'{"items":[{}]}'):
            with self.subTest(body=body), self.response(body), self.assertRaises(poc.PocError):
                poc.fetch_snippet("RMTJPFKRbEk", "secret")

    def test_missing_key(self):
        with patch.dict(poc.os.environ, {}, clear=True), patch.object(poc.Path, "exists", return_value=False):
            with self.assertRaisesRegex(poc.PocError, "YOUTUBE_API_KEY"):
                poc.load_api_key()

    def test_description_candidates(self):
        checks = poc.inspect_description("🥘 뽀요베리\n📍서울 마포구 월드컵로13길 55-15\n01:20 소개\nhttps://naver.me/example")
        self.assertTrue(all(checks.values()))
        self.assertEqual(next(iter(checks.values()))[0]["text"], "🥘 뽀요베리")


if __name__ == "__main__":
    unittest.main()
