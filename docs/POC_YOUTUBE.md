# YouTube 메타데이터 PoC 1

실험일: 2026-10-21 (Asia/Seoul)

## 목적과 범위

공식 YouTube Data API v3 `videos.list(part=snippet)`로 실제 얻는 메타데이터와 설명란 내 식당 정보를 확인한다. Python 3.10 이상 표준 라이브러리만 사용하며 React, Spring Boot, PostgreSQL, 지도 API는 구축하지 않았다. 자막·영상 다운로드, 페이지 스크래핑, 비공식 API 호출은 수행하지 않았다.

## 실행

Google Cloud 프로젝트에서 YouTube Data API v3를 활성화하고 API 키를 발급한다. 키의 API 제한 및 호출 환경 제한도 확인한다. 프로젝트 루트에서 다음 명령을 실행한다.

```powershell
# 현재 PowerShell 프로세스 환경변수로 설정 (실제 키를 소스에 넣지 않는다)
$env:YOUTUBE_API_KEY = '발급받은 키'
$env:PYTHONIOENCODING = 'utf-8'
python youtube_poc.py 'https://www.youtube.com/watch?v=RMTJPFKRbEk'

# 네트워크 및 실제 API 키 없이 오류 처리와 로직 검증
python -m unittest -v
```

이미 있는 루트 `.env`의 `YOUTUBE_API_KEY=...`도 자동으로 읽는다. 프로세스 환경변수가 우선한다. `.env`는 단순 `이름=값` 형식이며 셸 코드, 변수 치환, 인라인 주석을 해석하지 않는다. 새 환경에서는 `.env.example`을 `.env`로 복사해 키를 입력해도 된다. 실제 키는 터미널 기록 등에 남지 않도록 로컬 `.env` 사용을 권장한다.

`.gitignore`에 `.env`, `.env.*`, `results/`, `__pycache__/`를 제외하고 키가 없는 `.env.example`만 허용했다. 현재 폴더에는 `.git`이 없으므로 Git 추적 여부 검증은 불가능하지만, Git 저장소 초기화 시 제외 규칙이 적용된다. 커밋과 푸시는 수행하지 않았다. 키는 요청 헤더 `X-Goog-Api-Key`로만 전달하고 요청 URL·오류 로그·문서에 출력하지 않는다.

요청은 `GET https://www.googleapis.com/youtube/v3/videos?part=snippet&id=RMTJPFKRbEk`이다. JSON 출력에 `videoId`, `title`, `channelTitle`, `publishedAt`, 전체 `description`, 줄 번호가 있는 `descriptionChecks`를 포함한다. 제목과 설명의 원문을 유지하며 게시일은 API의 UTC 값이다.

## 실제 결과

로컬 `.env` 키로 공식 API 조회에 성공했다. 제한된 실행 환경에서 첫 시도는 네트워크 오류였으며, 네트워크 접근을 허용한 재실행에서 성공했다.

| 필드 | API에서 확인한 값 |
| --- | --- |
| videoId | RMTJPFKRbEk |
| title | 돼르~한 세계여행 돼리만족 제대로인 남노의 국경 없는 먹방 (망원동 맛집, 광진구 맛집)ㅣ윤남노포 EP.47 [EN] |
| channelTitle | 스튜디오슬램 |
| publishedAt | 2026-08-24T09:31:23Z |

설명란 전체는 실행 시 JSON의 `description`으로 출력한다. 이번 응답의 식당 관련 원문은 다음과 같다.

```text
🥘 뽀요베리
📍서울 마포구 월드컵로13길 55-15
💡돼겐남노 추천 메뉴
뽀요베리 편육, 뽈뽀, 초떡초떡, 뽀요 꼬레아노, 뽀요 에스빠뇰

🍣 모리본
📍서울 광진구 긴고랑로15길 6
💡돼겐남노 추천 메뉴
성게알 + 모듬 사시미, 단새우 사시미 머리튀김, 자루 모리 소바, 렌꼬도미 소금구이, 회덮밥, 다이야메
```

| 확인 항목 | 이번 설명란 원문 검토 결과 |
| --- | --- |
| 식당명 | 뽀요베리(7행), 모리본(12행) |
| 주소 | 서울 마포구 월드컵로13길 55-15(8행), 서울 광진구 긴고랑로15길 6(13행) |
| 장소 링크 | 없음 |
| 타임스탬프 | 없음 |

이는 해당 시점 설명란에 적힌 정보이며 식당의 실재·현재 영업·주소 정확성이나 추천 주체를 별도 검증한 결과는 아니다.

## 탐지 및 오류 처리 검증

URL은 watch, youtu.be, shorts, embed, live 형식에서 11자리 ID를 추출하고 호스트를 정확히 검사한다. 잘못된 URL은 API 호출 전에 종료한다.

설명란은 키워드·주소 패턴·알려진 지도 도메인·시간 패턴을 이용해 후보 줄을 표시한다. 초기 탐지가 이모지로 시작하는 식당명을 놓친 것을 확인해 주소 바로 앞줄도 식당명 후보에 추가했다. 해시태그 등 오탐이 생기며 임의 형식·해외 주소·다른 지도 링크는 놓칠 수 있다. 빈 후보 목록은 부재를 확정하지 않으므로 전체 설명란과 함께 수동 확인해야 한다. 자동 식당 엔티티 추출기나 지오코더가 아니다.

9개 단위 테스트 통과: 정상 URL, 잘못된 URL, 성공 응답과 요청 헤더, 빈 items, HTTP 오류와 키 비노출, 네트워크 실패, 잘못된 JSON/응답 구조, 누락 키, 설명란 후보 탐지. 실제 잘못된 호스트 CLI 실행도 오류 메시지와 종료 코드 1을 확인했다. 비공개 영상·할당량 오류 등은 모의 응답으로 검증했으며 실제 비공개 영상이나 할당량을 소진하는 실험은 하지 않았다.

HTTP 실패는 상태 코드와 reason을 표시하며 서버 메시지나 인증 헤더는 출력하지 않는다. 네트워크 요청은 20초 타임아웃을 사용한다. 빈 `items`는 비공개·삭제·존재하지 않는 영상 등을 구분하지 않고 조회 불가로 안내한다. 정상 종료 코드는 0, 처리한 오류는 1, CLI 인자 오류는 argparse의 2다.

## 결론과 한계

이번 영상에서는 공식 API의 snippet만으로 두 식당명과 주소를 설명란에서 확인했다. 식당명·주소·장소 링크·타임스탬프는 전용 구조화 필드가 아니라 작성자가 입력한 설명 텍스트이므로 다른 영상에서도 확보된다고 보장할 수 없다. 지도 표시를 위한 좌표나 장소 식별자는 이번 호출로 확보하지 않았다. 자막 본문은 snippet에 포함되지 않으며 이 PoC에서는 수집하지 않는다.

## 공식 참고 문서

- [videos.list: 요청 형식과 part, id](https://developers.google.com/youtube/data/reference/rest/v3/videos/list)
- [Video resource: snippet 필드 정의](https://developers.google.com/youtube/data/reference/rest/v3/videos)
- [YouTube Data API 시작하기: API 활성화 및 인증](https://developers.google.com/youtube/v3/getting-started)
- [API 오류](https://developers.google.com/youtube/data/reference/rest/v3/errors)
