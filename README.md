# 세단지

YouTube 설명란의 식당 후보를 관리자가 검토하고, 승인한 장소만 지도에 공개하는 첫 기능이다.

## 구성

- `backend/`: Java 17, Spring Boot 3.5.16, Spring Security, Spring Data JPA, Flyway, Gradle Wrapper
- `compose.yaml`: PostgreSQL 17, 로컬 포트 55432, 영속 볼륨
- `frontend/`: React 19, Vite, Kakao Maps Web SDK
- 루트 Python 스크립트: 기존 YouTube/Kakao PoC와 추가 영상 검증

## 로컬 실행

필수 도구: JDK 17, Node.js 20.19 이상, Docker Desktop. Gradle 8.14.3은 Wrapper가 공식 배포본을 내려받아 SHA-256을 검증하므로 별도 설치하지 않는다. 프로젝트 루트 `.env`에 `.env.example`의 변수를 설정한다. 기존 키를 유지하면서 항목을 추가한다. `POSTGRES_PASSWORD`와 `ADMIN_PASSWORD`는 `CHANGE_ME`를 사용하지 않고, 관리자 비밀번호는 16자 이상으로 지정한다. 현재 작업 환경의 로컬 개발용 비밀번호는 `.env`에 생성해 두었다. 값은 로그·문서·Git에 기록하지 않는다.

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
5. 후보의 Kakao 검색을 실행하거나 저장된 결과를 확인하고 지점·주소를 비교해 장소를 선택한다. 관계 셰프, 언급 유형(단골/추천/방문/단순 언급), 관계 확인 근거와 검토 메모를 작성한다. 영상 시점은 확인된 경우에만 초 단위로 입력한다.
6. ‘관계 승인하고 공개’를 누르면 트랜잭션 안에서 Restaurant과 별도 Mention을 저장한다. 같은 후보에서 다른 셰프의 관계를 추가할 수 있다. 관계를 만들지 않을 후보는 사유와 함께 제외한다.
7. ‘맛집 지도’에서 승인된 장소, 추천 셰프, 원본 영상 링크를 확인한다.

현재 개발 DB에는 영상 6개와 추출 후보 3개가 있으며 승인 관계는 0개다. 장소 검색 성공은 관계 승인으로 취급하지 않는다. 같은 셰프·식당·영상·언급 유형의 중복 관계는 409로 막는다. 첫 버전에는 승인 취소·수정, 관리자 계정 관리 화면, 페이징, 외부 배포 기능은 없다.

관리자 계정은 최초 실행 시 환경변수의 아이디와 BCrypt 비밀번호 해시로 DB에 생성한다. 이후 재시작에서는 기존 해시를 유지하므로 `.env` 값 변경만으로 기존 비밀번호를 변경하지 않는다.

## 데이터 모델

| 모델 | 주요 데이터 및 관계 |
| --- | --- |
| Chef | 관리자 확인 후 등록한 셰프. 채널과 분리 |
| Video | YouTube videoId, 제목, 채널, 게시일, 설명란 원문, 추출 한계 안내 |
| RestaurantCandidate | 설명란에서 추출한 상호·주소·행·원문 근거. ImportJob 연결, 장소 매칭 상태, OPEN/RESOLVED/DISMISSED 처리 상태 |
| Mention | Chef·Restaurant·Video가 모두 정해진 근거 관계. 언급 유형·확인된 시점·PENDING/APPROVED/REJECTED·검토자·시각·메모. 후보는 1:N으로 여러 Mention을 지원 |
| Restaurant | Kakao 장소 ID(unique), 이름, 도로명·지번, 경도·위도, 카카오맵 링크 |
| PlaceCandidate | RestaurantCandidate별 공식 Kakao 검색 스냅샷. 승인에는 해당 후보에서 서버가 확보한 장소 ID만 허용 |
| ImportJob | 후보의 수집 출처 기록. 현재는 성공한 영상 수집 및 기존 데이터 이관 기록을 저장 |
| AdminUser | 관리자 계정과 BCrypt 해시. 관계의 검토자를 FK로 연결 |

같은 Kakao ID는 식당 하나로 저장한다. 후보 행 잠금과 관계 유일성 제약으로 중복 승인을 막는다. 조회 API는 APPROVED Mention에 연결된 식당만 반환하며, 내부 후보와 검토 메모를 공개하지 않는다. 기존 V1 후보 데이터는 읽기 전용 `legacy_mention_candidates`에 보존한다.

## API

| 요청 | 기능 |
| --- | --- |
| GET `/api/restaurants` | 공개 식당과 승인된 셰프·영상 근거 |
| GET `/api/csrf`, GET `/api/session` | CSRF 토큰, 로그인 상태 |
| POST `/api/login`, POST `/api/logout` | 세션 로그인·로그아웃 |
| GET/POST `/api/admin/chefs` | 셰프 조회·등록 |
| GET/POST `/api/admin/videos` | 영상 조회·공식 메타데이터 가져오기 |
| POST `/api/admin/videos/{id}/candidates` | 원문 근거를 가진 추출 후보 수동 추가 |
| GET `/api/admin/candidates` | 추출 후보 목록과 장소 매칭·처리 상태 |
| GET `/api/admin/candidates/{id}/places` | 저장된 검색 결과 조회 (외부 API 호출 없음) |
| POST `/api/admin/candidates/{id}/places` | Kakao 검색 및 후보 스냅샷·매칭 상태 저장 |
| POST `/api/admin/candidates/{id}/mentions` | 셰프·장소·유형·근거·메모를 검증해 별도 승인 관계 생성 |
| POST `/api/admin/candidates/{id}/dismiss` | 사유와 함께 후보 제외 |
| GET `/api/admin/mentions` | 실제 셰프–식당 근거 관계 목록 |

관리자 API는 세션 인증을 요구하고 변경 요청에는 CSRF 토큰이 필요하다. 세션 쿠키는 HttpOnly, SameSite=Strict다. 외부 API는 고정된 공식 호스트만 호출하며 자막·영상·설명란 외부 링크의 목적지를 수집하지 않는다.

## 테스트

```powershell
# 기존 PoC 30개 (실제 API 호출 없음)
python -m unittest -v

# Docker 실행 필요. Testcontainers가 임시 PostgreSQL을 생성하며 .env 불필요
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -Test

# React 로직 및 지도 좌표 전달 테스트
cd frontend
npm test
npm run build
```

백엔드 통합 테스트는 Testcontainers가 임의 포트의 전용 PostgreSQL을 생성하고 Flyway 마이그레이션을 적용한다. 개발 DB와 이전 `sedanji_test`를 사용하지 않는다. Docker가 없으면 테스트를 건너뛰지 않고 실패한다. JPA는 `ddl-auto=validate`, `open-in-view=false`이며 스키마를 변경하지 않는다. 테스트 스크립트는 `.env`를 로드하지 않고 외부 API는 모의 응답으로 대체한다.

직접 실행하려면 `backend`에서 `.\gradlew.bat test` 또는 `./gradlew test`를 사용한다. 실행 JAR 생성은 `.\gradlew.bat bootJar`다. `.\gradlew.bat bootRun`은 실행 환경에 앱 변수를 직접 설정해야 하므로, 로컬 `.env`를 쓰는 경우 루트의 `scripts/start-backend.ps1`을 사용한다.

`python scripts/smoke-api.py`는 실행 중인 로컬 서버에 로그인해 샘플 영상을 실제 공식 API로 가져오고 장소 후보를 검색하는 개발용 검증이다. 로컬 DB에 OPEN 추출 후보를 저장하며 **Mention을 생성하거나 승인하지 않는다**. 원본 영상과 `results/additional_videos.json`의 추가 영상이 대상이고 결과는 `results/api-smoke.json`이다. 외부 호출 없이 상태를 확인하려면 `python scripts/check-backend-state.py --output results/current.json`을 사용한다.

## 검증 기록

- [YouTube PoC](docs/POC_YOUTUBE.md)
- [Kakao 실제 매칭 결과와 PoC 테스트 30개](docs/POC_KAKAO_LOCAL.md)
- [추가 영상 5개에서 확인한 추출 한계](docs/VIDEO_DESCRIPTION_VALIDATION.md)
- [첫 기능 구현·검증 결과](docs/FIRST_FEATURE.md)
- [Gradle·JPA·Testcontainers 전환 결과](docs/BACKEND_TRANSITION.md)
- [후보와 Mention 분리 및 기존 데이터 이관](docs/CANDIDATE_MENTION_SEPARATION.md)

외부 배포 전에는 HTTPS 및 Secure 쿠키, 별도 관리자 계정 저장소, 비밀번호 정책·로그인 시도 제한, API 사용량 제어·보관 기간 정책을 별도로 구성해야 한다. 현재 구현은 로컬 첫 기능 검증 범위다. 커밋과 푸시는 사용자 승인 후에만 수행하며, 커밋 메시지는 영어 접두어와 한국어 본문을 사용한다.

공식 참고: [Spring Boot 3.5 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Kakao Web SDK 가이드](https://apis.map.kakao.com/web/guide/), [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
