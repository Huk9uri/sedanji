# Backend 개발 지침

루트 `../AGENTS.md`와 함께 적용한다. 아래 근거 경로는 별도 표시가 없으면 `backend/` 기준이다.

## 현재 구조와 책임

- Java 17, Spring Boot 3.5.16, Gradle Wrapper 8.14.3을 사용한다. 의존성·태스크는 `build.gradle`, 버전은 Wrapper 설정을 확인한다.
- `src/main/java/kr/sedanji/`의 chef, restaurant, video, mention, mentiondraft, restaurantcandidate, importjob, registration, admin은 도메인 중심 패키지다. 필요한 범위에서 Service·Repository·Entity 책임을 분리한다.
- 기존 Controller·DTO·오류는 `src/main/java/kr/sedanji/ApiController.java`, `Models.java`, `ApiErrors.java`다. 신규 직접 등록은 `registration/DirectRegistrationController`·요청/결과 record·`DirectRegistrationErrors`로 분리됐다. 외부 연동은 `ExternalApis.java`다. 별도 common·infrastructure 패키지가 이미 있다고 가정하지 않는다.
- V3의 초안 테이블은 `mentiondraft/MentionDraftEntity`·Repository·Service가 사용하며 `MentionDraftController`가 CREATE 초안 생성·단건 조회·식당별 페이지 목록·부분 수정을 제공한다. `MentionDraftInput`의 엄격 파싱과 `MentionDraftErrors`의 오류 계약은 신규 초안 API에 한정한다. CREATE 초안 승인은 `MentionDraftApprovalController`·전용 Request/Result/Errors와 NEVER `MentionDraftApprovalFacade`, `RegistrationService.approveDraft`로 제공한다. 정정·공개 중단·복구 API는 없다. `../docs/ADMIN_API.md`의 현재 계약과 목표 절을 구분하고 목표를 현재 API 소급 변경의 근거로 사용하지 않는다.

## 트랜잭션과 동시성

- #16 관리자 식당 GET은 `restaurant/AdminRestaurantQueryService`의 readOnly REPEATABLE_READ에서 JDBC count·식당 페이지·선택 ID 집계·셰프 미리보기 또는 관계 페이지를 조립한다. 기존 공개 조회·승인 트랜잭션과 분리한다. 조회마다 외부 API를 호출하거나 관계 검증·공개 상태를 변경하지 않는다.

- 후보 기반 승인은 `registration/RegistrationService.approveFromCandidate`가 트랜잭션을 소유한다. 후보 잠금, 셰프·관리자·장소 검증, 식당 생성·재사용, Mention 저장, 후보 RESOLVED 처리는 함께 커밋·롤백되어야 한다.
- 직접 승인은 `DirectRegistrationFacade`의 NEVER 경계에서 `RegistrationService.approveDirect`의 트랜잭션을 호출한다. 활성 관리자 행 잠금·세션 장소 검증·식당 재사용·관계·감사·24시간 영수증을 함께 처리한다. UNIQUE 충돌 후 기존 관계 조회는 롤백 완료 뒤 수행한다. 현재 직접 승인은 초안을 생성하지 않는다.
- CREATE 초안 생성은 장소 토큰 또는 기존 restaurantId 중 하나를 받아 식당 생성·재사용, 초안·감사·24시간 생성 영수증을 함께 저장한다. PATCH는 관리자 → 초안 순서로 잠그고 expectedVersion을 확인하며 누락 필드는 유지·선택 필드 null은 해제한다. 성공한 PATCH는 같은 값이어도 version을 증가시키며 수정 영수증은 만들지 않는다. 저장·수정만으로 Mention을 만들거나 공개하지 않는다. 활성 관리자 간 조회·수정 공유와 기존 장소 정보 보존을 유지한다.
- CREATE 초안 승인은 활성 관리자 확인·영수증 확인 후 관리자 → 초안 잠금 순서를 유지하며 저장된 필수 참조·expectedVersion을 검증한다. 관계 생성·APPLIED 전환·승인 감사·200 영수증은 원자적이다. 동일 관리자·키·요청의 유효 영수증만 24시간 재생하고 다른 키·관리자·만료 후 재승인은 DRAFT_ALREADY_APPLIED다. 상태 무관 관계 UNIQUE 충돌은 서비스 롤백 후 facade에서 조회·변환하며 자동 재시도·기존 관계 복구·장소 토큰 재검증·외부 호출은 하지 않는다.
- `restaurantcandidate/RestaurantCandidateService.getForUpdate`·`resolveCandidate`와 `mention/MentionService.approve`의 MANDATORY 경계를 보존한다. 잠금이 전체 승인 흐름에서 유지되도록 호출자의 트랜잭션을 확인한다.
- `RestaurantCandidateRepository`의 PESSIMISTIC_WRITE, 관계 UNIQUE 제약, `RestaurantRepository`의 Kakao ID 기반 atomic upsert를 우회하지 않는다. upsert는 현재 `on conflict ... do nothing`이며 기존 식당 이름·주소를 덮어쓰지 않는다.
- 같은 셰프·식당·영상은 유형이 달라도 중복 관계다. 동명이인 셰프를 이름만으로 합치지 않는다.
- `VideoService.fetchMetadata`는 NOT_SUPPORTED로 외부 메타데이터 조회를 DB 트랜잭션에서 분리한다. `VideoImportService`의 영상·수집 작업·후보 저장 원자성을 보존한다.
- `CandidateMatchingService.search`는 외부 호출 실패 시 기존 검색 스냅샷·매칭 결과를 보존하고 안전한 실패 코드를 저장하기 위해 `noRollbackFor`를 사용한다. 일반 승인 롤백 규칙을 이 경로에 일괄 적용하지 않는다.

근거: 위 서비스·Repository 및 `src/test/java/kr/sedanji/ApprovalIntegrationTest.java`, `src/test/java/kr/sedanji/RegistrationTransactionTest.java`.

## 후보 상태와 전이

- 후보 처리 상태 disposition은 OPEN(검토 대기), RESOLVED(관계 승인됨), DISMISSED(제외)다. matchStatus 및 Mention의 verificationStatus·publicationStatus와 독립적으로 관리한다.
- 승인 성공 때만 같은 트랜잭션에서 RESOLVED로 바꾼다. RESOLVED만으로 공개 조건을 만족한다고 판단하지 않는다.
- RESOLVED 후보도 다른 셰프의 관계를 근거 확인 후 추가할 수 있다. 후보 하나당 관계 하나로 제한하지 않는다. 동일 셰프·식당·영상 중복 방지는 유지한다.
- 제외는 OPEN에만 허용하고 사유를 요구한다. RESOLVED 제외와 DISMISSED 후보의 승인·재검색을 거부하는 현재 경계를 보존한다.
- 수동 후보의 evidence는 저장된 영상 설명란 원문에 포함되어야 한다. 수집·재수집으로 기존 근거를 덮어쓰지 않는다.

근거: `src/main/java/kr/sedanji/restaurantcandidate/RestaurantCandidateEntity.java`, `src/main/java/kr/sedanji/restaurantcandidate/RestaurantCandidateService.java`, `src/main/java/kr/sedanji/ApiController.java`, `src/test/java/kr/sedanji/ApprovalIntegrationTest.java`의 oneCandidateCanSupportMultipleChefs, resolvedCandidateCannotBeDismissed, resolvedCandidateDoesNotPublishUnapprovedRelation 테스트.

## 승인·공개와 데이터 신뢰성

- 장소 매칭은 관계 승인이 아니다. 후보 추출·장소 검색·후보 제외만으로 Mention이나 공개 식당을 만들지 않는다. 성공한 빈 검색과 외부 호출 실패를 구분한다.
- 현재 후보 기반 승인은 명시적 Chef·Restaurant·출처 Video·언급 유형·비어 있지 않은 evidence와 활성 관리자를 요구한다. 후보에서 서버가 확보한 장소만 선택할 수 있으며 후보와 관계의 영상이 같아야 한다.
- 직접 승인은 세션 장소 선택 토큰·기존 셰프·영상·유형과 활성 관리자가 필요하다. 자유 입력 evidence는 요구하지 않고 `verificationNote`는 선택이다. 후보 승인 evidence 규칙을 직접 승인에 일괄 적용하지 않는다.
- `timestampSeconds`는 미확인일 때 null, 확인된 0초는 0이다. 음수는 거부하고 확인되지 않은 시점을 생성하지 않는다.
- 현재 서버에서 검토 메모 note는 선택이며 빈 값은 null로 저장한다. 기존 웹은 아직 메모를 필수로 요구한다. 이 불일치를 API 변경 시 함께 검토하되 임의로 서버 필수 조건을 추가하지 않는다.
- 공개 식당과 공개 출처 모두 `verificationStatus=APPROVED` 및 `publicationStatus=PUBLISHED`를 적용한다. 중단된 관계를 다른 공개 관계에 섞지 않는다.
- 공개 응답에는 승인된 셰프·영상·유형·시점 등 필요한 근거만 제공한다. 후보, 내부 evidence·reviewNote·관리자 정보는 공개 DTO에 추가하지 않는다.

근거: `src/main/java/kr/sedanji/mention/MentionEntity.java`, `src/main/java/kr/sedanji/mention/MentionService.java`, `src/main/java/kr/sedanji/restaurant/RestaurantRepository.java`, `src/main/java/kr/sedanji/restaurantcandidate/RestaurantCandidateService.java`, `src/main/java/kr/sedanji/Models.java`, `../docs/DIRECT_REGISTRATION_MIGRATION.md`.

## DTO·API 계약

- JPA Entity나 외부 JsonNode를 그대로 API 응답으로 노출하지 않는다. 현재 Java record DTO와 toDto·toSource 경계를 유지한다.
- 요청은 `ApiController`·`DirectRegistrationController`의 Bean Validation과 서비스의 도메인 검증을 함께 확인한다. DB 제약을 UI 검증만으로 대체하지 않는다.
- 현재 오류 변환은 `ApiErrors`: 입력 400, 외부 API 실패 502, DB 저장 충돌 409 등이다. Security의 401·403은 빈 응답일 수 있다. 내부 예외·외부 원문 오류·비밀정보를 반환하지 않는다.
- 신규 Controller 전용 `DirectRegistrationErrors`는 code/message와 중복 관계 existingMentionId를 처리한다. 모든 오류에 code가 있다고 가정하지 않는다. 직접 승인·메타데이터 등록은 JSON 201이며 기존 빈 응답과 구분한다. 상세 필드·경로·멱등키는 `../docs/ADMIN_API.md`의 현재 계약을 확인한다.
- 현재 후보·관계 생성은 빈 201, 로그인·로그아웃·후보 제외는 204인 경로가 있다. 예정 계약의 JSON 성공 응답·페이지 구조를 현재 전체 API에 적용된 것으로 간주하지 않는다.
- API 변경 전후 `../frontend/src/api.ts`, `../frontend/src/App.tsx`, `../frontend/src/features/admin/CandidateReviewCard.tsx`와 테스트·호출 스크립트를 검색한다. 필드·null·상태 코드·인증·CSRF·입력 제약·갱신 흐름의 양쪽 영향을 보고한다.

## 인증·CSRF

- `src/main/java/kr/sedanji/SecurityConfig.java`의 DB 관리자·BCrypt·세션 기반 폼 로그인을 유지한다. JWT·일반 사용자 인증이 있다고 가정하지 않는다.
- `/api/admin/**`의 ADMIN 권한, 그 외 명시 허용 경로와 기본 denyAll을 보존한다. 화면 숨김은 서버 권한 검증을 대신하지 않는다.
- `/api/csrf`의 headerName·token 계약과 변경 요청 CSRF 보호를 유지한다. 연동 문제 해결을 위해 CSRF를 끄거나 관리자 API를 공개하지 않는다.
- 현재 쿠키는 HttpOnly·SameSite=Strict다. 배포용 HTTPS·Secure 쿠키나 교차 출처·모바일 정책이 완료됐다고 주장하지 않는다.
- 최초 관리자 생성만 환경변수 비밀번호를 사용한다. 기존 계정은 재시작·환경변수 변경만으로 비밀번호가 교체되지 않는다. 비밀번호·해시·토큰을 출력하지 않는다.

근거: `src/main/java/kr/sedanji/SecurityConfig.java`, `src/main/resources/application.properties`, `../README.md`.

## PostgreSQL·Flyway

- PostgreSQL과 Flyway를 사용한다. `ddl-auto=validate`, `open-in-view=false`를 유지하고 update·create로 이관을 대신하지 않는다.
- 필드·관계·제약 변경 전에 `../docs/DOMAIN_MODEL.md`의 ERD와 데이터 보존·삭제 정책·참조 무결성을 검토한다. FK·인덱스·NOT NULL·UNIQUE를 필요한 범위에 설계한다.
- 기존 V1~V4를 임의 수정하지 않고 필요한 경우 후속 migration으로 변경한다. V4는 Video의 UNCHECKED/AVAILABLE/REVIEW_REQUIRED·확인 시각·UNAVAILABLE 원인 조합을 제약하며 실제 DB 적용 상태는 별도 확인한다. Kakao Place ID·YouTube Video ID와 관계 유일성, 좌표·상태 제약을 보존한다.
- V2의 기존 승인 자료 보호, V3의 중복 관계 충돌 중단 조건을 우회하지 않는다. 기존 근거·legacy 기록·감사 이력은 자동 삭제·병합하지 않는다.
- 스키마 파일 존재·과거 적용 기록과 실제 대상 DB 상태를 구분한다. 개발 DB 이관은 백업·충돌·적용 대상을 확인하고 승인된 범위에서만 수행한다. `migrateDevelopmentDatabase`는 검증 전용 명령이 아니라 DB 변경 명령이다.

근거: `src/main/resources/db/migration/V1__initial_schema.sql`, `src/main/resources/db/migration/V2__separate_candidates_and_mentions.sql`, `src/main/resources/db/migration/V3__prepare_direct_registration.sql`, `../docs/ADMIN_MIGRATION_PLAN.md`, `src/test/java/kr/sedanji/tools/MigrateDevelopmentDatabase.java`.

## 외부 API와 검증

- 공식 YouTube·Kakao 호스트와 DTO 변환 경계를 유지하고 타임아웃, 할당량·삭제 영상·잘못된 응답을 구분해 처리한다. 키·인증 헤더·원문 예외를 로그에 출력하지 않는다.
- Kakao `x=longitude`, `y=latitude`를 유지한다. 최대 3페이지 검색의 totalCount·complete를 보존하며 잘린 결과를 전체 확보로 표시하지 않는다.
- 실행이 허용된 작업에서는 `backend`에서 `./gradlew test`(Windows: `.\gradlew.bat test`), 패키징은 `bootJar`를 사용한다. 루트 `scripts/start-backend.ps1 -Test`도 기존 테스트 진입점이다.
- 통합·이관 테스트는 Testcontainers 임시 PostgreSQL과 모의 외부 API를 사용한다. 개발 DB나 실제 API 키를 요구하는 테스트로 바꾸지 않는다. Docker 부재·미실행을 통과나 무조건 skip으로 처리하지 않는다.
- 변경에 맞춰 승인·공개 조건, 접근 제어·CSRF, 중복·지점 검증, null/0, 외부 실패 보존, 서비스 자체 커밋·롤백, 이관 데이터 보존을 검증한다. `RegistrationTransactionTest`의 호출자 트랜잭션 없는 검증을 유지한다.
- 문서만 변경한 작업에서는 사실·참조·diff 검토를 우선하고 실행 금지 조건을 따른다.

근거: `src/main/java/kr/sedanji/ExternalApis.java`, `build.gradle`, `src/test/java/kr/sedanji/`의 기존 단위·통합·이관 테스트, `../scripts/start-backend.ps1`.
