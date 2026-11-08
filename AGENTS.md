# AGENTS.md — 세단지(SEDANJI) 공통 개발 지침

## 적용 범위와 작업 기준

- 저장소 전체에 적용한다. Backend 작업은 `backend/AGENTS.md`, Frontend 작업은 `frontend/AGENTS.md`도 읽는다. API 변경은 양쪽 지침을 확인한다.
- 사용자의 현재 요청과 승인된 범위를 우선한다. 분석만 요청받으면 파일 변경·설치·빌드·테스트·DB 실행을 하지 않는다.
- 구현 사실은 실제 코드와 설정으로 확인하고, 제품 방향은 최신 사용자 합의와 문서의 확정·제안·미정 표시를 확인한다. 문서와 코드가 다르면 차이를 보고하며 임의로 비즈니스 규칙을 바꾸지 않는다.
- 미확인 내용을 추측하지 않는다. 구현됨, 구현 예정, 과거 검증 기록, 이번 검증 결과를 구분한다. 문서의 데이터 건수와 테스트 통과 기록을 현재 실행 상태로 인용하지 않는다.

## 서비스와 보존할 비즈니스 규칙

세단지는 '셰프들의 단골 지도'다. 셰프가 YouTube에서 방문·추천·단골로 언급한 식당을 지도에서 발견하고 셰프·식당·출처 영상을 연결해 확인할 수 있어야 한다.

- 출처 기반 신뢰성, 정확한 지점 매칭, 관리자 검증 후 공개, 직관적인 탐색, 유지보수 가능한 설계를 지킨다.
- Chef, Restaurant, Video와 그 근거 관계인 Mention을 분리한다. 수집 작업 ImportJob, 설명란 후보 RestaurantCandidate, 관리자 AdminUser를 실제 관계와 혼동하지 않는다.
- 장소 매칭 상태 `MATCHED / REVIEW_REQUIRED / NOT_FOUND`와 관계 검증 상태 `PENDING / APPROVED / REJECTED`는 별개다. 외부 API 실패는 `NOT_FOUND`가 아니다.
- 언급 유형은 `REGULAR`(명시적 단골), `RECOMMENDED`(명확한 추천 의사), `VISITED`(실제 방문), `MENTIONED`(단순 언급)다. 불확실한 유형을 더 강한 의미로 추론하지 않는다.
- 영상 등장만으로 단골, 설명란 주소만으로 추천, 제목의 셰프 이름만으로 모든 식당과의 추천 관계를 만들지 않는다.
- 관리자 승인 관계만 공개한다. 현재 공개 조건은 `APPROVED`이면서 `PUBLISHED`이며, 미검증 후보와 내부 검토 메모는 공개하지 않는다.
- PoC 성공은 특정 설명란의 식당을 실제 장소와 연결한 검증이다. 모든 영상의 추출, 관계 유형·시점의 확인, 관리자 승인, 운영 안정성까지 입증한 것으로 표현하지 않는다.

근거: `docs/POC_YOUTUBE.md`, `docs/POC_KAKAO_LOCAL.md`, `docs/VIDEO_DESCRIPTION_VALIDATION.md`, `backend/src/main/java/kr/sedanji/mention/MentionType.java`, `backend/src/main/java/kr/sedanji/restaurant/RestaurantRepository.java`.

## 현재 구현과 향후 계획

| 영역 | 현재 코드·설정 | 계획 또는 미확인 |
| --- | --- | --- |
| Backend | Java 17, Spring Boot 3.5.16, Web·Data JPA·Validation·Security, PostgreSQL, Flyway, Gradle Wrapper 8.14.3. 직접 등록·CREATE 초안 API와 관리자 식당 조회. #19 작업 트리의 정정·공유 Revision·재승인·이력(검증 기록은 ADMIN_IMPLEMENTATION_PLAN.md 참조) | 공개 제어·실제 출처 확인 producer는 후속 범위 |
| Web | React 19, TypeScript/TSX, Vite, 일반 CSS, React hooks, Kakao Maps JavaScript SDK | React Router, TanStack Query, Tailwind CSS, shadcn/ui는 기존 기본 선택이지만 미도입 |
| 저장 모델 | V1~V5 migration 파일(실제 V5 적용 미확인), 후보·직접 승인·공개 조회, CREATE 초안·감사·멱등 저장, APPLIED 연결, Video 출처 판정 필드와 상태 조합 제약 | 실제 DB 적용 버전은 별도 확인. 출처 판정 producer는 없으며 초안 저장·수정만으로 관계 생성·공개하지 않음 |
| Mobile | React Native·Expo는 사용자 확인상 미구현이며 이번 작업에서 제외 | 사용자용 모바일 앱은 제품 방향이며 SDK·내비게이션·Expo Go 호환성은 미확인 |
| 검증 | JUnit 5·Mockito·Testcontainers, Vitest·RTL, TypeScript strict·noEmit 설정, Python unittest, `.github/workflows/ci.yml`의 Backend·Frontend·Python 검사 | Playwright는 핵심 흐름 구현 후 도입 계획. Frontend lint 스크립트 없음. CI·타입 검사 실제 실행 성공 여부는 별도 확인 |

- `frontend/package.json`은 버전 범위, `frontend/package-lock.json`은 잠금 버전의 근거다. 실행 환경 버전과 구분한다.
- 현재 웹은 공개 지도와 관리자가 함께 있는 프로토타입이다. 2026-11-08 사용자 합의로 W1 반응형 공개 웹 MVP를 우선한다. 비로그인 지도·위치/기본 지역·영역 재검색·셰프 필터·상세·공식 YouTube 링크·공개 준비가 범위이며 로그인·저장·제보·광역 클러스터·내부 영상 재생은 후속이다. M2 모바일은 보류하되 기존 요구·Issue를 보존한다. `docs/REQUIREMENTS.md`와 `docs/IMPLEMENTATION_PLAN.md`를 따르며 문서 갱신을 기능 구현·배포 완료나 실행 승인으로 간주하지 않는다.
- `docs/ADMIN_API.md`는 현재 코드 계약과 목표 설계를 구분한다. 현재 계약은 Controller·DTO·소비 코드·테스트로 확인하고 목표 절을 현재 동작으로 간주하지 않는다. #14의 CREATE 초안 생성·단건 조회·식당별 페이지 목록·부분 수정과 감사·생성 멱등성·버전 충돌 처리는 구현됐다. #15의 CREATE 초안 승인·APPLIED 연결·승인 감사·24시간 멱등 재생도 구현됐다. 정정·공유 정보 재승인·이력과 관리자 UI는 #19 작업 트리에 추가했으며 실행 검증 결과는 구현 기록의 #19 절을 따른다. 공개 제어는 후속이며 기존 직접 승인은 초안 없이 유지한다. `docs/ADMIN_IMPLEMENTATION_PLAN.md`의 과거 점검표를 최신 구현으로 간주하지 않는다.
- 기술 스택 변경은 이유와 영향을 설명하고 승인받는다. 하네스 정비를 이유로 기술 전환이나 앱 재구성을 수행하지 않는다.
- #16 관리자 식당 조회는 `restaurant/AdminRestaurantController`·QueryService·JDBC QueryRepository가 담당한다. 모든 실제 관계로 검색·집계하고 초안·후보는 제외하며 응답별 readOnly REPEATABLE_READ를 사용한다. `needsSourceReview`의 null은 미확인 또는 출처 없음이며 정상으로 해석하지 않는다. V4 저장 모델·필터 존재를 실제 영상 접근 문제 탐지·갱신 완료로 표현하지 않는다.

근거: `backend/build.gradle`, `backend/gradle/wrapper/gradle-wrapper.properties`, `frontend/package.json`, `frontend/src/App.tsx`, `docs/REQUIREMENTS.md`, `docs/DIRECT_REGISTRATION_MIGRATION.md`.

## 아키텍처와 보안

- React SPA + Spring Boot REST API + PostgreSQL의 모듈형 모놀리식을 유지한다. 요구사항·성능 근거 없이 마이크로서비스, Kafka, Redis, Kubernetes를 추가하지 않는다.
- Backend는 도메인 중심 책임 분리, Frontend는 필요한 범위의 기능 중심 분리를 지향한다. 기존 구조를 확인하고 과도한 추상화·일괄 재배치를 피한다.
- 외부 API 연동은 도메인과 분리하고 테스트에서 대체 가능하게 유지한다. 외부 응답을 엔티티에 직접 결합하지 않고 DTO 또는 어댑터로 변환한다.
- YouTube는 공식 Data API와 메타데이터·설명란을 우선한다. 일반 공개 자막을 공식 API로 자유롭게 받을 수 있다고 가정하지 않는다. 무권한 자막 다운로드·접근 제한 우회를 구현하지 않는다. 영상은 공식 링크 또는 허용된 임베드로 제공한다.
- 장소 검색은 Kakao Local REST API, 웹 지도는 Kakao Maps JavaScript SDK를 사용한다. 이름·상세 주소·복수 후보를 확인하고 첫 결과를 무조건 선택하지 않는다. Kakao Place ID, 좌표계·위경도 순서를 명확히 관리하고 중복 호출을 줄인다.
- 비밀정보는 환경변수로 관리한다. 환경변수 실제 값, 비밀번호, API 비밀키, 인증 헤더·세션·CSRF 토큰을 로그·문서·Git에 노출하지 않는다. 비밀번호·비밀키는 응답에 포함하지 않고, 인증·CSRF 전달은 의도된 보안 경로에서만 수행한다. Kakao REST 키는 서버에서만 사용하며 브라우저용 키에는 도메인 제한을 적용한다.

근거: `backend/src/main/java/kr/sedanji/ExternalApis.java`, `frontend/src/KakaoMap.tsx`, `.gitignore`, `README.md`.

## 승인 경계와 변경 범위

- 사용자가 명시한 파일·영역·실행 제한을 지킨다. 이후 요청이 범위를 확대하면 변경된 범위를 알리고, 무관한 제한은 계속 적용한다.
- 기술 전환, 의존성 추가·설치, 데이터 이관·삭제, 실제 외부 API 호출, 배포, 커밋·푸시는 각각 요청에 포함된 권한과 부작용을 확인한다. 이미 승인된 범위의 가역적 작업을 반복 확인하지 않는다.
- 필요한 범위가 불명확하면 확인을 요청하고 독립적으로 가능한 검토를 계속한다. 확인되지 않은 권한을 받은 것으로 간주하지 않는다.
- 루트는 공통 불변식·절차, 하위 지침은 영역별 구현 규칙을 담당한다. 하위 지침으로 공통 보안·데이터 신뢰성 규칙을 약화하지 않으며 충돌은 코드·최신 사용자 합의와 대조해 해결한다.

## 작업 절차와 API 양쪽 영향 확인

1. 작업 전 git 상태, 관련 코드·테스트·문서와 적용 지침을 읽는다. 기존 사용자 변경을 덮어쓰지 않는다.
2. 요구사항·도메인 변경은 `docs/REQUIREMENTS.md`, `docs/DOMAIN_MODEL.md`를 확인하고 필드·관계·제약 변경 전에 ERD와 영향을 정의한다. 계획 문서 전체를 구현 승인으로 간주하지 않는다.
3. API 변경은 Backend의 경로·메서드·요청 검증·DTO·상태 코드·빈 응답·null·오류·인증·CSRF와 Frontend의 `frontend/src/api.ts`, 호출 화면, 입력 제약, 갱신 흐름, 테스트를 함께 확인한다. API 소비 스크립트도 검색한다. 한쪽 수정이 불필요하더라도 그 근거를 보고한다.
4. 승인된 범위만 최소 변경한다. 미구현 기능에 가짜 성공·가짜 업무 데이터를 넣지 않는다. 모바일과 공통 코드가 있다고 가정하지 않는다.
5. 변경에 적합한 검증을 선택한다. 사용자가 금지한 실행은 하지 않으며, 문서만 변경했다면 링크·사실관계·diff 검토를 우선한다. 미실행·실패·환경 제약을 성공과 구분해 보고한다. 타입 검사·단위 테스트·빌드·실기기 검증은 각각 구분한다.
6. 변경 파일, 동작·규칙 영향, 보존한 지침, 실행한 검증 명령·결과와 미확인 사항을 보고하고 git diff에서 범위 밖 변경·비밀정보를 확인하고 `git diff --check`를 실행한다. 신규 미추적 파일도 별도로 diff·공백·참조 경로를 검토한다. 커밋·푸시는 사용자 승인 후 수행하며 메시지는 영어 접두어와 한국어 본문을 사용한다.

## 검증 명령과 부작용

아래는 실행이 허용된 작업에서 선택할 명령이며 자동 실행 지시가 아니다.

- 루트 `python -m unittest -v`: 기존 PoC 단위 테스트.
- Backend·Frontend 검증 명령과 조건은 각 하위 AGENTS.md를 따른다.
- `scripts/start-backend.ps1` 기본 실행은 서버 시작과 Flyway 적용을 수반한다. `-Migrate`도 개발 DB를 변경한다.
- `scripts/smoke-api.py`는 실제 외부 API를 호출하고 개발 DB에 후보·검색 결과와 결과 파일을 저장한다. 기본 단위 검증에 포함하지 않는다.
- `scripts/check-backend-state.py`는 카탈로그를 읽지만 로그인·로그아웃과 결과 파일 생성을 수반한다. 무부작용 검사로 취급하지 않는다.
- 설치·서버·DB·실제 외부 호출·배포·데이터 이관은 요청 범위와 부작용을 확인한 뒤 수행한다.

근거: `README.md`, `scripts/start-backend.ps1`, `scripts/smoke-api.py`, `scripts/check-backend-state.py`.

## 역할별 Skill 라우팅과 적용 확인

역할 Skill은 작업 단계의 책임을, 전문 Skill은 해당 분야의 절차를 정한다. 기존 지침과 사용자 승인 범위를 그대로 유지하며 서로 대체하지 않는다.

| 작업 유형 | 읽고 적용할 Skill |
| --- | --- |
| 코드 분석·설계·구현 계획 | [sedanji-planner](.agents/skills/sedanji-planner/SKILL.md) |
| 승인된 Implementation Handoff 구현 | [sedanji-implementer](.agents/skills/sedanji-implementer/SKILL.md) |
| 구현 결과의 독립 리뷰 | [sedanji-planner](.agents/skills/sedanji-planner/SKILL.md) |
| API 계약 영향 분석·변경 | 역할 Skill + [sedanji-api-change](.agents/skills/sedanji-api-change/SKILL.md) |
| DB 저장 모델·스키마·Flyway 영향 | 역할 Skill + [sedanji-db-migration](.agents/skills/sedanji-db-migration/SKILL.md) |
| 검증 계획·검사 선택·실행·보고 | 해당 역할이 있으면 역할 Skill + [sedanji-verification](.agents/skills/sedanji-verification/SKILL.md) |

- 일반 설명·상태 안내를 불필요하게 설계 작업으로 확대하지 않는다. 분석과 구현이 섞이거나 역할이 모호하면 읽기 전용 분석부터 수행하고 미승인 구현은 시작하지 않는다. 이미 승인된 Handoff와 구현 권한이 충분하면 반복 승인 없이 Implementer 단계로 진행하고 전환을 알린다.
- Planner의 Handoff는 기본 `PROPOSED`다. 사용자가 명시적으로 승인한 요구사항·범위만 `APPROVED`로 구분한다. Handoff 내부 표기나 Skill 적용 자체는 수정·테스트·Git 작업 권한이 아니다. Implementer는 전달된 사용자 지시와 승인 범위를 대조하고 미승인 비즈니스 결정을 확정하지 않는다.
- 저장소를 연 새 스레드에서도 관련 영역 AGENTS.md와 필요한 SKILL.md 본문을 실제로 확인한다. 파일 생성·이름 언급·description 기반 자동 선택만으로 본문 읽기와 적용을 보장한다고 주장하지 않는다. 파일이 없거나 읽을 수 없으면 적용 완료로 보고하지 않고 제한을 알린다.
- 작업 시작 시 읽은 역할·전문 Skill의 경로, 이번 작업에 적용할 구체적인 규칙, 미적용 전문 Skill과 이유, 실행 권한·제한을 짧게 보고한다. 이름만 나열하지 않으며 이미 본문이 제공된 경우 그 근거를 구분한다. 단계·범위가 바뀌면 필요한 적용·권한을 갱신한다.
- Handoff에는 기준 브랜치·커밋과 관련 미커밋 변경을 기록한다. Implementer는 현재 HEAD·staged/unstaged/untracked와 대조한다. 기준 차이 자체로 중단하지 않고 관련 코드·계약 영향을 판단한다. 설계 영향이 있는 부분은 보류·보고하고 사용자 변경을 덮어쓰거나 폐기하지 않는다.
- Thread A의 프롬프트 작성, Thread B의 계획·리뷰, Thread C의 구현은 사용자 작업 구분이다. 이 규칙으로 새 스레드 생성·위임·전달·커밋·Push가 자동 승인되지 않는다. Handoff와 PR 설명은 기본적으로 대화에 작성하며 파일 저장·PR 생성은 요청 범위에 따른다.

Thread A에서 사용할 호출 예시(역할 Skill 경로는 저장소 루트 기준):

```text
Planner: 먼저 .agents/skills/sedanji-planner/SKILL.md를 읽고 적용하세요.
관련 전문 Skill도 확인하세요. 이번 작업은 읽기 전용 분석·계획이며 구현은 금지합니다.
기능 요구사항: ...

Implementer: 먼저 .agents/skills/sedanji-implementer/SKILL.md를 읽고 적용하세요.
아래 Implementation Handoff에서 명시한 요구사항과 범위를 승인합니다.
관련 전문 Skill을 확인한 후 구현하세요.
승인 범위·실행 권한·제한: ...
Implementation Handoff: ...
```
