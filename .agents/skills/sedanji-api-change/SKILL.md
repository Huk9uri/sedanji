---
name: sedanji-api-change
description: Sedanji Spring Boot Backend와 React TypeScript Frontend 사이의 API 경로·메서드·DTO·필드·상태 코드·인증 계약을 분석하거나 변경할 때 적용한다. 양쪽 계약·기존 클라이언트 호환성·변경 계약을 검증하는 테스트를 확인하고 sedanji-verification과 연계한다. 분석 요청은 분석만 수행하며 구현은 승인된 범위에 한한다.
---

# Sedanji API 계약 변경

## 적용 조건과 우선 근거

- API 경로·HTTP 메서드·Content-Type·요청 검증·DTO·필드·상태 코드·오류·인증·CSRF·공개 응답의 의미를 분석하거나 변경할 때 적용한다. 단순 화면 스타일 변경만으로 API 변경 작업을 시작하지 않는다.
- 분석·계획·문서 작업과 코드 구현을 구분한다. Skill 선택은 파일 수정·테스트 추가·실행·데이터 이관의 권한을 확대하지 않는다. 현재 요청과 실행 제한을 우선한다.
- 파일 생성만으로 모든 Codex 작업에서 자동 선택·실행된다고 가정하지 않는다. 명시 호출 또는 호스트의 실제 Skill 선택에 따라 적용한다.
- [루트 지침](../../../AGENTS.md), [Backend 지침](../../../backend/AGENTS.md), [Frontend 지침](../../../frontend/AGENTS.md)을 읽는다. 변경 수집·검증 선택·실행·보고는 [sedanji-verification](../sedanji-verification/SKILL.md)의 규칙을 그대로 따른다.
- 현재 API 계약의 우선 근거는 현재 체크아웃의 Controller·요청/응답 DTO·Security 실행 구현이다. 서비스·직렬화·조회 조건·소비 코드·테스트 assertion으로 실제 의미를 추적한다. 정적 확인만 했다면 실제 배포 중인 서버의 응답까지 확인했다고 표현하지 않는다.
- [ADMIN_API.md](../../../docs/ADMIN_API.md) 등 설계 문서는 현재 계약과 별도로 읽는다. 구현됨·구현 예정·제안·미확인을 구분하고, 설계 문서에 있다는 이유로 API가 이미 동작한다고 가정하거나 소급 변경하지 않는다. 제품 방향은 최신 사용자 합의를 따르되 현재 동작과의 차이를 설명한다.

## 1. 변경 전후 계약 체크리스트

Verification Skill에 따라 staged·unstaged·untracked, 삭제·이전 경로, 기존 변경과 이번 작업 범위를 확인한다. diff와 실제 내용을 읽고 다음을 기록한다.

| 항목 | 확인 내용 |
| --- | --- |
| 경로·메서드 | 전체 경로, path/query 매개변수, HTTP 메서드, 기존 경로 제거·이름 변경 |
| 요청 | JSON/폼 인코딩, 필드명·타입·필수 여부, Bean Validation·서비스 검증·길이·범위 |
| 응답 | Java DTO·실제 변환 결과, 배열/객체 구조, 필드 추가·삭제·이름 변경, 빈 본문 |
| 값 의미 | 누락·null·빈 문자열·빈 배열·0, 날짜 문자열·enum·ID 표현과 범위 |
| 상태·오류 | 성공/실패 상태 코드, message JSON과 빈 오류 응답, 201/204 처리 |
| 보안·공개 | 세션·ADMIN·CSRF·쿠키, APPROVED AND PUBLISHED 조건과 내부 정보 제외 |
| 소비자·호환성 | TS 타입·래퍼·폼·화면 분기·갱신·fixture·호출 스크립트, 구/신 클라이언트 영향 |

현재 주요 근거 파일은 다음과 같다. 구조가 바뀌었으면 실제 파일을 다시 찾는다.

- Backend: [ApiController.java](../../../backend/src/main/java/kr/sedanji/ApiController.java), [Models.java](../../../backend/src/main/java/kr/sedanji/Models.java), [ApiErrors.java](../../../backend/src/main/java/kr/sedanji/ApiErrors.java), [SecurityConfig.java](../../../backend/src/main/java/kr/sedanji/SecurityConfig.java).
- Frontend: [api.ts](../../../frontend/src/api.ts), [types.ts](../../../frontend/src/types.ts), [App.tsx](../../../frontend/src/App.tsx), [CandidateReviewCard.tsx](../../../frontend/src/features/admin/CandidateReviewCard.tsx).

기존 클라이언트가 동시에 모두 갱신된다고 가정하지 않는다. 구 클라이언트→신 서버와 신 클라이언트→구 서버를 각각 검토한다. 필드 추가도 필수 요청·엄격한 소비자·새 enum 값 때문에 비호환일 수 있다. 파괴적 변경은 영향과 호환 처리·배포 전환 순서를 검토하고 승인된 범위에서만 반영한다. 요청되지 않은 API 버전 체계·의존성·코드 생성기를 도입하지 않는다.

## 2. Backend·Frontend 동기화 절차

1. 변경 전 계약과 요청한 변경을 확정한다. 예정 계약 구현이나 미정 비즈니스 규칙 결정을 임의로 포함하지 않는다.
2. Controller의 요청 record·메서드·상태 코드, 응답 Models, 서비스 검증·toDto/toSource·Repository 공개 조건, Security·직렬화 설정까지 추적한다. Entity·외부 응답을 그대로 공개 DTO로 노출하지 않는다.
3. TS 응답 타입뿐 아니라 api 호출의 요청 body·form 옵션·제네릭, 입력 조건·표시·오류·갱신·fixture를 대조한다. 한쪽 수정이 불필요해도 근거를 보고한다.
4. `필드 변경 전 → 변경 후 → 서버 근거 → TS/소비자 영향 → 검증 assertion`을 연결한다. Java 참조 타입만으로 nullable을 확정하지 않는다. TS의 `field?: T`와 `field: T | null`을 같은 계약으로 취급하지 않고 실제 직렬화·검증·생성 경로를 확인한다.
5. 구현·테스트·문서 수정은 해당 요청의 승인 범위에 한한다. 저장 모델 영향이 있으면 ERD·후속 migration 필요성을 별도로 검토한다. 개발 DB 직접 적용과 기존 Flyway 수정은 하지 않는다.
6. 실제 diff에서 양쪽 동기화·호환성을 재검토하고 Verification Skill로 검증을 선택한다. 타입 단언이나 mock 값 변경만으로 서버 계약이 입증됐다고 보고하지 않는다.

현재 `api<T>()`의 T는 정적 응답 선언이며 런타임 JSON 검증이 아니다. 요청 body도 unknown을 허용하므로 타입 검사만으로 Java 요청 DTO 일치를 판단하지 않는다. 모든 성공 응답이 JSON인 것도 아니다. 현재 빈 201·204는 래퍼에서 null이며 보안 401·403은 본문이 없을 수 있다.

## 3. 인증·승인·공개 불변식

- DB 관리자·BCrypt·세션 폼 로그인, `/api/admin/**` ADMIN 권한과 기본 denyAll을 보존한다. 화면 숨김·TS 타입은 서버 권한 검증을 대체하지 않는다.
- `/api/csrf`의 headerName·token, 변경 요청 CSRF 보호, 로그인 폼 인코딩·세션 조회·로그아웃을 유지한다. 연동 오류 해결을 위해 CSRF를 끄거나 관리자 경로를 공개하지 않는다.
- HttpOnly·SameSite=Strict 쿠키와 현재 동일 출처/개발 프록시 흐름을 보존한다. credentials·CORS·쿠키·JWT 저장 정책을 임의 변경하거나 JS로 세션 쿠키를 읽는 방식으로 바꾸지 않는다. 비밀번호·토큰·비밀키를 출력하지 않는다.
- 공개 식당과 공개 출처 모두 `APPROVED AND PUBLISHED`를 만족해야 한다. 후보·내부 evidence·검토 메모·관리자 정보를 공개 DTO에 추가하지 않는다.
- MATCHED는 장소 식별이며 관계 승인이 아니다. 후보 OPEN/RESOLVED/DISMISSED와 관계 검증·공개 상태를 구분한다. 검색·추출·후보 처리만으로 공개하지 않는다.
- [RegistrationService](../../../backend/src/main/java/kr/sedanji/registration/RegistrationService.java)의 승인 트랜잭션·후보 잠금, 서버가 확보한 장소·출처 검증, 식당 재사용·관계 생성·후보 해결의 원자성, 셰프·식당·영상 중복 방지를 보존한다.
- 미확인 timestampSeconds는 null, 확인된 0초는 0이다. 요청·응답·화면·링크에서 보존한다. 영상 등장·주소·제목만으로 관계 유형을 강화하지 않는다.
- **현재 차이:** 후보 기반 승인 메모 note는 Backend에서 선택이지만 기존 Frontend는 필수로 요구한다. 어느 한쪽을 임의 수정하거나 모든 API의 필수/선택 비즈니스 규칙으로 일반화하지 않는다. 관련 변경이 요청됐다면 차이·영향·승인 범위를 확인한다. 후보 제외 사유의 필수 조건과도 혼동하지 않는다.

공개 조건은 [RestaurantRepository](../../../backend/src/main/java/kr/sedanji/restaurant/RestaurantRepository.java)와 [MentionService](../../../backend/src/main/java/kr/sedanji/mention/MentionService.java), 승인 입력·메모 변환은 Controller·서비스·[MentionEntity](../../../backend/src/main/java/kr/sedanji/mention/MentionEntity.java)를 대조한다. 선언만으로 실행 의미를 추정하지 않는다.

## 4. 대표 API 변경 사례

아래는 승인된 API 변경 작업의 검토 규칙이며 실제 변경 지시가 아니다. 각 계약 변경의 기본 검증은 Backend + Frontend다.

| 사례 | 동기화·호환성 규칙 | 계약을 확인할 assertion 예 |
| --- | --- | --- |
| 응답 필드 추가 | 공개 가능 정보·누락/null·TS·화면·기존 소비자 영향 확인 | 실제 JSON 필드·값·노출 범위와 화면 처리 |
| 필드 삭제·이름 변경 | 모든 요청·응답·소비자 검색, 구/신 호환 처리·전환 검토 | 요구사항에 따른 기존/변경 필드의 허용·부재·변환 |
| nullability·필수 조건 변경 | Bean Validation·서비스·직렬화와 TS optional/null·폼·분기 대조 | 누락·null·빈 값·정상 값의 상태·본문·UI 동작 |
| 경로·메서드 변경 | Security matcher·호출부·스크립트·구 클라이언트 확인 | 정상·잘못된 메서드·권한·CSRF·호출 경로 |
| 빈 201/204 ↔ JSON 또는 오류 변경 | 상태·본문과 래퍼 파싱·message/fallback·갱신 대조 | HTTP 상태·본문과 반환·파싱·오류 처리 |
| 세션·권한·CSRF 관련 변경 | 기존 정책을 유지하며 익명·권한 부족·토큰 부재 검토 | MockMvc 보안 응답과 로그인·폼·오류 경로 |
| 공개 응답·상태 변경 | 이중 공개 조건·내부 필드 제외·매칭/승인 분리 | 미승인·중단 관계 제외, 공개 출처만 포함, 내부 필드 비노출 |
| 시점·enum·ID 표현 변경 | null/0·새 값·문자열/숫자와 기존 소비자 분기 확인 | 경계값·구/신 값 처리·0초 출처 링크 |

## 5. 테스트 충족 여부와 Verification 연계

- 테스트 파일·이름의 존재만 보지 않는다. 변경된 계약의 기대 결과를 실제 assertion이 검증하는지 읽고 `계약 변경 → 테스트/사례 → assertion → 남은 공백`을 기록한다.
- [ApprovalIntegrationTest](../../../backend/src/test/java/kr/sedanji/ApprovalIntegrationTest.java), [RegistrationTransactionTest](../../../backend/src/test/java/kr/sedanji/RegistrationTransactionTest.java), [api.test.ts](../../../frontend/src/api.test.ts), [App.test.tsx](../../../frontend/src/App.test.tsx) 등을 먼저 확인하고 변경 경로에 맞는 검증을 찾는다.
- Java 변경은 MockMvc의 실제 직렬화·상태·입력 검증과 서비스 검증을, Frontend는 래퍼·타입 검사·화면 행동·오류를 필요한 범위에서 확인한다. fixture가 새 계약과 일치하는지 서버 근거와 대조한다. 양쪽이 서로 다른 mock 전제만 검증해도 각각 통과할 수 있음을 고려한다.
- **기존 테스트가 모두 통과해도 변경 계약의 assertion이 없다면 계약 검증 완료가 아니다.** 미검증 동작과 필요한 사례·추가 테스트를 보고한다. 추가 구현은 승인 범위 안에서만 수행하며 범위 밖이면 공백을 명시한다. 부족한 검증을 숨기거나 테스트·검증 설정을 완화하지 않는다.
- [sedanji-verification](../sedanji-verification/SKILL.md)의 Git 변경 수집·검증 선택·실행 예시·종료 코드·보고 규칙을 그대로 사용한다. API 계약 변경은 `-Backend -Frontend`가 기본이며 공통 설정·검증 스크립트 등 추가 변경은 그 Skill의 합집합·전체 규칙을 따른다.
- 분석 전용·문서만 추가·변경 없음 상황도 Verification Skill의 목적별 판단을 따른다. 이 Skill 문서 생성만으로 앱 테스트를 자동 재실행하지 않는다.
- 실제 명령·관측한 종료 코드·각 검사 PASS/FAIL/NOT RUN과 미실행 사유를 보고한다. **명령의 PASS와 계약 검증 공백은 별도**로 기재한다. 과거 통과 수치를 이번 결과로 사용하지 않는다.
- 타입 검사·단위/통합 테스트·빌드 통과를 브라우저 세션·지도·실제 외부 API·배포 환경 E2E 확인으로 표현하지 않는다.

## 안전 경계와 완료 보고

- 개발 DB 직접 수정·migration, 기존 Flyway 변경, 실제 외부 API 호출을 하지 않는다. 기존 mock·Testcontainers 전용 임시 DB 경계를 지킨다.
- 의존성·도구·이미지 자동 설치, Docker·서버 자동 시작을 하지 않는다. 환경 부재·사전검사 한계는 NOT RUN으로 보고하며 검증 설정을 우회하지 않는다.
- 자동 stage·커밋·푸시를 하지 않는다. 존재하지 않는 Expo 앱·모바일 소비자·공통 클라이언트·OpenAPI 생성 체계를 가정하지 않는다.
- 완료 보고에 변경 전후 계약, 양쪽 수정/불필요 근거, 호환성 영향, 보존한 불변식, 계약별 assertion·검증 공백, Verification Skill의 실행 결과·미실행 사유를 포함한다.
- `git diff --check`, 신규 파일 공백·참조·변경 범위를 확인한다. 정적 검토만 했다면 실제 테스트는 NOT RUN이며 실행 종료 코드는 해당 없음으로 표시한다.
