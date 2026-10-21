"""Read-only catalog regression check. Login/logout only; no imports or approvals."""
import argparse
import http.cookiejar
import json
from pathlib import Path
import sys
from urllib.parse import urlencode
from urllib.request import build_opener, HTTPCookieProcessor, ProxyHandler, Request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--compare', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    settings = {}
    for line in (root / '.env').read_text(encoding='utf-8-sig').splitlines():
        name, sep, value = line.partition('=')
        if sep:
            settings[name.strip()] = value.strip().strip('\"\'')
    opener = build_opener(ProxyHandler({}), HTTPCookieProcessor(http.cookiejar.CookieJar()))
    base = 'http://127.0.0.1:8090/api'

    def call(path, body=None):
        headers = {}
        if body is not None:
            token = call('/csrf')
            headers[token['headerName']] = token['token']
            headers['Content-Type'] = 'application/x-www-form-urlencoded'
        request = Request(base + path, headers=headers, data=None if body is None else urlencode(body).encode())
        with opener.open(request, timeout=20) as response:
            return None if response.status == 204 else json.load(response)

    try:
        call('/login', {'username': settings.get('ADMIN_USERNAME', 'admin'), 'password': settings['ADMIN_PASSWORD']})
        state = {name: call(path) for name, path in {
            'videos': '/admin/videos', 'candidates': '/admin/candidates', 'mentions': '/admin/mentions',
            'chefs': '/admin/chefs', 'restaurants': '/restaurants'}.items()}
        call('/logout', {})
        if args.compare and state != json.loads(args.compare.read_text(encoding='utf-8')):
            print('Catalog state differs from the baseline', file=sys.stderr)
            return 1
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'counts': {name: len(items) for name, items in state.items()},
                          'baseline_equal': True if args.compare else None}))
    except Exception:
        print('Catalog read-only verification failed (credentials omitted)', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
