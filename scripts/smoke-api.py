"""Local development smoke check. Imports OPEN candidates, never creates a Mention."""
import http.cookiejar
import json
from pathlib import Path
import sys
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, build_opener, HTTPCookieProcessor, ProxyHandler


def main():
    root = Path(__file__).resolve().parent.parent
    settings = {}
    for line in (root / '.env').read_text(encoding='utf-8-sig').splitlines():
        name, sep, value = line.partition('=')
        if sep:
            settings[name.strip()] = value.strip().strip('\"\'')
    opener = build_opener(ProxyHandler({}), HTTPCookieProcessor(http.cookiejar.CookieJar()))
    base = 'http://127.0.0.1:8090/api'

    def call(path, body=None, form=False):
        headers = {}
        encoded = None
        if body is not None:
            token = call('/csrf')
            headers[token['headerName']] = token['token']
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
            encoded = (urlencode(body) if form else json.dumps(body)).encode('utf-8')
        with opener.open(Request(base + path, data=encoded, headers=headers), timeout=90) as response:
            return json.load(response) if response.status != 204 else None

    try:
        public_before = call('/restaurants')
        call('/login', {'username': settings.get('ADMIN_USERNAME', 'admin'), 'password': settings['ADMIN_PASSWORD']}, form=True)
        assert call('/session')['authenticated']
        ids = ['RMTJPFKRbEk']
        sample_path = root / 'results/additional_videos.json'
        if sample_path.exists():
            ids += [item['videoId'] for item in json.loads(sample_path.read_text(encoding='utf-8'))]
        for video_id in ids:
            call('/admin/videos', {'url': 'https://www.youtube.com/watch?v=' + video_id})
        candidates = call('/admin/candidates')
        searches = []
        for candidate in candidates:
            if candidate['disposition'] == 'OPEN':
                result = call('/admin/candidates/' + str(candidate['id']) + '/places', {'query': candidate['extractedName']})
                searches.append({'name': candidate['extractedName'], 'count': len(result['candidates']), 'complete': result['complete']})
        public_after = call('/restaurants')
        assert public_before == public_after, 'Import must not publish restaurants'
        report = {'session_login': True, 'imported_video_count': len(call('/admin/videos')),
                  'candidates': [{'name': c['extractedName'], 'address': c['extractedAddress'], 'disposition': c['disposition']} for c in candidates],
                  'mention_count': len(call('/admin/mentions')),
                  'searches': searches, 'published_count': len(public_after), 'no_automatic_approval': True}
        (root / 'results/api-smoke.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(report, ensure_ascii=False, indent=2))
        call('/logout', {})
    except Exception as exc:
        # Never echo arbitrary HTTP exception bodies or credentials.
        print('Local API smoke failed' + (': HTTP ' + str(exc.code) if isinstance(exc, HTTPError) else ''), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
