# 세단지

YouTube 설명란의 식당 후보를 관리자가 검토하고, 승인한 장소만 지도에 공개하는 첫 기능이다.

## 구성

- `backend/`: Java 17, Spring Boot 3.5.16, Spring Security, JDBC, Flyway
- `compose.yaml`: PostgreSQL 17, 로컬 포트 55432, 영속 볼륨
- `frontend/`: React 19, Vite, Kakao Maps Web SDK
- 루트 Python 스크립트: 기존 YouTube/Kakao PoC와 추가 영상 검증

## 로컬 실행

필수 도구: JDK 17, Maven 3.6.3 이상, Node.js 20.19 이상, Docker Desktop. 프로젝트 루트 `.env`에 `.env.example`의 변수를 설정한다. 기존 키를 유지하면서 항목을 추가한다. `POSTGRES_PASSWORD`와 `ADMIN_PASSWORD`는 `CHANGE_ME`를 사용하지 않고, 관리자 비밀번호는 16자 이상으로 지정한다. 현재 작업 환경의 로컬 개발용 비밀번호는 `.env`에 생성해 두었다. 값은 로그·문서·Git에 기록하지 않는다.

```powershell
# 터미널 1: PostgreSQL
docker compose up -d db

# 터미널 2: Spring Boot (.env 자동 로드)
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1

# 터미널 3: React
cd frontend
npm ci
npm run dev
```

브라우저: [http://localhost:5173](http://localhost:5173). 백엔드는 `127.0.0.1:8090`이다. 이 작업 환경에서 기존 8080 포트가 사용 중이므로 8090을 사용한다. 개발 프록시로 `/api`를 같은 출처로 연결하며 백엔드·DB는 루프백에만 바인딩한다.

지도에는 REST 키가 아닌 `VITE_KAKAO_JAVASCRIPT_KEY`를 사용한다. 카카오 키 설정의 JavaScript SDK 도메인에 `http://localhost:5173`을 등록한다. Vite는 루트 `.env`를 읽으며 브라우저에 공개되는 값은 `VITE_` 접두어 변수뿐이다. REST API 키·YouTube 키·DB 비밀번호·관리자 비밀번호에 `VITE_` 접두어를 붙이지 않는다. JavaScript 키는 브라우저용 공개 식별자이므로 도메인 제한으로 관리한다.

## 사용 순서

1. 화면 우측 ‘관리자’에서 `.env`의 `ADMIN_USERNAME`/`ADMIN_PASSWORD`로 로그인한다.
2. 영상 URL을 입력해 공식 YouTube API의 설명란을 가져온다. 재입력은 기존 검토 결과를 보존한다.
3. 설명란 원문과 후보를 확인한다. 누락은 원문에 포함된 근거를 붙여 수동 후보로 추가한다.
4. 셰프 이름을 등록한다. 채널명에서 셰프를 자동 생성하지 않는다.
5. 후보의 Kakao 검색을 실행하고 지점·주소를 비교해 장소를 선택한다. 추천 셰프와 확인 근거를 작성한다.
6. ‘승인하고 지도에 공개’를 누르면 트랜잭션 안에서 Restaurant 및 Mention 연결을 저장한다. 반려도 가능하다.
7. ‘맛집 지도’에서 승인된 장소, 추천 셰프, 원본 영상 링크를 확인한다.

현재 DB에는 검토용 영상과 후보가 있을 수 있으며, 자동 승인하지 않는다. 공개 지도가 비어 있어도 승인 대기 후보는 관리자 화면에서 확인할 수 있다. 이미 승인/반려한 후보의 재승인은 409로 막는다. 첫 버전에는 승인 취소·수정, 사용자별 관리자 계정 관리, 페이징, 외부 배포 기능은 없다.

## 데이터 모델

| 모델 | 주요 데이터 및 관계 |
| --- | --- |
| Chef | 관리자 확인 후 등록한 셰프. 채널과 분리 |
| Video | YouTube videoId, 제목, 채널, 게시일, 설명란 원문, 추출 한계 안내 |
| Mention | Video에 속한 식당 언급. 추출 상호·주소·행·원문 근거, PENDING/APPROVED/REJECTED, 검토자·시각·메모. 승인 시 Chef 및 Restaurant 연결 |
| Restaurant | Kakao 장소 ID(unique), 이름, 도로명·지번, 경도·위도, 카카오맵 링크 |
| PlaceCandidate | Mention별 공식 Kakao 후보 스냅샷. 승인에는 해당 Mention에서 서버가 확보한 후보 ID만 허용 |

같은 Kakao ID는 여러 영상에서 승인해도 식당 하나로 저장한다. 승인 행에 잠금을 잡아 중복 승인을 방지한다. 승인 후에는 불변이며, 조회 API는 승인된 Mention이 존재하는 식당만 반환한다.

## API

| 요청 | 기능 |
| --- | --- |
| GET `/api/restaurants` | 공개 식당과 승인된 셰프·영상 근거 |
| GET `/api/csrf`, GET `/api/session` | CSRF 토큰, 로그인 상태 |
| POST `/api/login`, POST `/api/logout` | 세션 로그인·로그아웃 |
| GET/POST `/api/admin/chefs` | 셰프 조회·등록 |
| GET/POST `/api/admin/videos` | 영상 조회·공식 메타데이터 가져오기 |
| POST `/api/admin/videos/{id}/mentions` | 원문 근거를 가진 후보 수동 추가 |
| GET `/api/admin/mentions` | 검토 목록 |
| POST `/api/admin/mentions/{id}/places` | Kakao 후보 검색 및 서버 스냅샷 저장 |
| POST `/api/admin/mentions/{id}/approve` | 셰프·장소·메모를 검증한 승인 |
| POST `/api/admin/mentions/{id}/reject` | 사유와 함께 반려 |

관리자 API는 세션 인증을 요구하고 변경 요청에는 CSRF 토큰이 필요하다. 세션 쿠키는 HttpOnly, SameSite=Strict다. 외부 API는 고정된 공식 호스트만 호출하며 자막·영상·설명란 외부 링크의 목적지를 수집하지 않는다.

## 테스트

```powershell
# 기존 PoC 30개 (실제 API 호출 없음)
python -m unittest -v

# 최초 1회, 별도 테스트 DB 생성
docker compose exec -T db psql -U sedanji -d postgres -c 'CREATE DATABASE sedanji_test;'

# 실제 PostgreSQL 테스트 DB 사용, 외부 API는 모의 응답
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -Test

# React 로직 및 지도 좌표 전달 테스트
cd frontend
npm test
npm run build
```

백엔드 통합 테스트는 `sedanji_test`에 Flyway 마이그레이션을 적용하고 테스트별 트랜잭션을 롤백한다. `TEST_DATABASE_URL`로 별도 테스트 DB를 지정할 수 있다. 운영 DB를 테스트 대상으로 지정하지 않는다.

`python scripts/smoke-api.py`는 실행 중인 로컬 서버에 로그인해 샘플 영상을 실제 공식 API로 가져오고 장소 후보를 검색하는 개발용 검증이다. 로컬 DB에 PENDING 데이터를 저장하며 **승인하지 않는다**. 원본 영상과 `results/additional_videos.json`의 추가 영상이 대상이고 결과는 `results/api-smoke.json`이다.

## 검증 기록

- [YouTube PoC](docs/POC_YOUTUBE.md)
- [Kakao 실제 매칭 결과와 PoC 테스트 30개](docs/POC_KAKAO_LOCAL.md)
- [추가 영상 5개에서 확인한 추출 한계](docs/VIDEO_DESCRIPTION_VALIDATION.md)
- [첫 기능 구현·검증 결과](docs/FIRST_FEATURE.md)

외부 배포 전에는 HTTPS 및 Secure 쿠키, 별도 관리자 계정 저장소, 비밀번호 정책·로그인 시도 제한, API 사용량 제어·보관 기간 정책을 별도로 구성해야 한다. 현재 구현은 로컬 첫 기능 검증 범위다. 커밋과 푸시는 사용자 승인 후에만 수행하며, 커밋 메시지는 영어 접두어와 한국어 본문을 사용한다.

공식 참고: [Spring Boot 3.5 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Kakao Web SDK 가이드](https://apis.map.kakao.com/web/guide/), [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
