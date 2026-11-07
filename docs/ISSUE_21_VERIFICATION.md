# #21 관리자 핵심 업무 E2E와 M1 검증

기준: `feat/AdminPage`, HEAD `6c7c6e8d8ba017379f0cf9b4e67439d38005d516`. 2026-11-07 시작 당시 staged/unstaged/untracked 없음. Handoff v2 D1~D7에 따라 구현했다. 실행 검증은 이 HEAD 위의 미커밋 작업 트리에서 수행했으며 커밋 후 재실행한 PASS가 아니다. 검증 당시 Git fetch·브랜치 전환·병합·stage·커밋·Push·PR 생성은 하지 않았다. 이후 사용자 승인에 따른 커밋은 문서 끝의 후속 기록을 따른다.

**최초 M1 판정: 미완료(외부 검증 보류).** 이후 사용자가 대상·도메인·예산을 지정하고 실제 외부 검증 재개를 승인했다. 후속 실제 연결 검증은 PASS이며, 최종 M1 판정은 아래 최신 검증 기록을 따른다. 최초에는 사용자가 실제 외부 연결 검증을 보류했다. 아래 기본 E2E와 회귀 결과로 실제 Kakao Local·YouTube·지도 SDK·영상 접근 성공을 대신하지 않는다. GitHub 읽기 전용 확인에서 M1의 #13~#20은 closed, #21은 open이었다. 이슈 상태를 테스트 성공 근거로 사용하지 않는다. #21 본문의 선행 #5~#8은 현재 구현 이슈 번호가 아니다.

**최종 M1 판정(2026-11-07): 완료 제안.** 승인된 기본 E2E·현재 전체 회귀·실제 외부 연결 범위를 모두 통과했고 이 검증 범위에서 중요 미해결 제품 결함은 발견하지 않았다. 실행 검증 당시 작업은 미커밋이었으며 원격 CI Job·PR 병합·Issue/마일스톤 완료 처리는 수행하지 않았다. 영상 근거 정확성·실제 재생·운영 안정성까지 완료로 확대하지 않는다.

**후속 최신 상태:** PR #34의 최초 CI는 E2E 이력 선택 경쟁으로 실패했다. 아래 수정의 로컬 검증은 PASS이지만 수정본의 원격 CI는 아직 실행하지 않았다. M1 최종 확정은 수정본 CI 확인 후 판단한다. 앞의 완료 제안과 로컬 PASS를 CI PASS로 인용하지 않는다.

## 구현과 격리

- `frontend/e2e/admin.spec.ts`: Chromium 8개 테스트에 A1~A3/B1~B4/C1~C3/보안/충돌을 묶었다. worker 1, retry 0, 테스트마다 새 BrowserContext. 별도 관리자 context를 만드는 충돌 테스트도 실제 로그인한다.
- `backend/src/test/java/kr/sedanji/E2eLauncher.java`: test runtime에만 존재한다. 실행마다 PostgreSQL 17 Testcontainers를 생성하고 기존 V1~V5 Flyway와 JPA validate를 수행한다. DB URL을 외부에서 받지 않는다. 테스트 계정 2개와 시나리오별 독립 자료를 삽입하며 DELETE/TRUNCATE·감사 trigger 해제는 없다.
- `ExternalApis`만 Primary 테스트 Bean으로 대체한다. Spring HTTP·세션·로그인·CSRF·SecurityFilterChain·서비스·Repository는 실제 구현이다. 테스트용 로그인/fixture/probe HTTP endpoint는 없다. `@TestConfiguration`은 기존 통합 테스트의 component scan에서 제외된다.
- DB probe는 loopback 임의 포트의 capability 보호 로컬 통신이다. 정해진 안전 컬럼 조회·서버 세션 invalidate·종료만 지원하며 임의 SQL을 받지 않는다. 암호·쿠키·CSRF·장소 토큰·멱등키·인증 헤더는 반환하지 않는다.
- `vite.e2e.config.ts`: 127.0.0.1:18173 → 동일 origin `/api` → 127.0.0.1:18090. `envDir: false`, 지도 키 공란. runner는 환경변수를 허용 목록으로 전달하여 실제 `.env`, DB·외부 키·관리자 비밀번호·Spring/JVM 옵션을 상속하지 않는다. 점유 포트는 재사용하지 않는다.
- `scripts/run-e2e.mjs`: readiness polling, 시작 180초·테스트 90초·전체 20분 상한. 소유한 프로세스·라벨 컨테이너만 정리한다. 준비 파일은 원자적으로 교체한다. graceful 종료 실패 후 라벨 컨테이너를 정리해야 하면 정상 PASS로 처리하지 않는다.
- 제품 Controller/DTO/보안/공개 조건·Frontend 소비 코드·스키마·migration은 수정하지 않았다. API 양쪽 계약은 실제 Controller·`api.ts`·등록/정정/공개제어 소비 코드·통합 테스트와 대조했다. API 호환성 변경은 없다. DB Migration Skill은 저장 모델 변경이 없어 미적용이다.

## 설치와 검증 분리

2026-11-07 [공식 Playwright 설치 문서](https://playwright.dev/docs/intro)의 Node 지원 범위(22/24/26)를 확인하고 npm registry에서 `@playwright/test` **1.65.0**을 확인해 고정했다. package engines의 `>=20`과 공식 검증 환경 범위를 구분한다. 기존 Node 20.19.6 대신 공식 Node 22.23.4 Windows x64 zip을 `.tools/e2e-tools`에 준비하고 공식 SHASUMS256의 SHA256과 대조했다. 시스템 Node는 교체하지 않았다.

실행한 준비:

1. frontend에서 `npm install --save-dev --save-exact @playwright/test@1.65.0 --no-audit --no-fund`.
2. 로컬 Node 22로 `frontend/node_modules/@playwright/test/cli.js install chromium` (Chromium/headless shell 1254).
3. Docker Linux Engine·캐시된 `postgres:17-alpine`, `testcontainers/ryuk:0.14.0`, `alpine:3.17` 확인. Docker 시작·이미지 pull은 하지 않았다. Windows의 별도 OS 패키지 설치도 없다.
4. 테스트 launcher 컴파일 `backend/gradlew.bat --gradle-user-home C:\workspace\sedanji\.tools\gradle-home --offline --no-daemon -Porg.gradle.java.installations.auto-download=false testClasses`.

다른 환경의 명시적 준비 예:

```text
# frontend: 지원 Node 준비 후
npm ci --no-audit --no-fund
node node_modules/@playwright/test/cli.js install chromium
# Linux CI에서 OS 패키지 준비도 허용된 경우에만 --with-deps 사용

# Docker 이미지 준비: 준비 단계에서만
docker pull postgres:17-alpine
docker pull testcontainers/ryuk:0.14.0

# backend: 준비 단계에서만 온라인 의존성 해석
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false prepareE2eDependencies
```

검사 명령은 패키지·Chromium·이미지·Gradle distribution을 설치하지 않는다. runner는 캐시를 사전 확인하고 Gradle을 offline으로 실행하며 테스트 전용 `E2eCachedImagePolicy`가 helper를 포함한 pull을 금지한다. 임시 Backend·Vite·DB 시작은 명시적으로 선택한 E2E의 부작용이다.

## 현재 실행

실행 검증 당시 HEAD는 위 기준 SHA이고 테스트 코드는 미커밋 작업 트리였다. 아래 두 명령은 준비한 Node 22.23.4 경로를 해당 PowerShell 프로세스의 PATH 앞에 추가한 뒤 실행했다. 샌드박스의 Docker/로컬 daemon 접근 제한은 정책을 변경하지 않고 권한 확장 실행으로 처리했다.

```powershell
powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Backend -Frontend -Python
powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -E2E
```

| 검사 | 현재 결과 | 종료 코드 | 근거 |
| --- | --- | --- | --- |
| Backend | PASS | 0 | 새 JUnit XML 218개 사례, 실패/skip 0 |
| Frontend typecheck | PASS | 0 | 기존 strict tsc --noEmit |
| Frontend Vitest | PASS | 0 | 16개 파일, 110개 테스트. E2E spec 수집 안 함 |
| Frontend build | PASS | 0 | 기존 타입 검사 후 Vite 빌드 |
| Python | PASS | 0 | 모의 API unittest 30개 |
| 위 회귀 전체 | PASS | 0 | wrapper 요약/프로세스 종료 코드 |
| E2E 별도 타입 검사 | PASS | 0 | `tsc -p tsconfig.e2e.json` 및 runner 시작 전 검사 |
| E2E 최종 실행 | PASS | 0 | 외부 실행기 추가 후 최종 통합 검증: 8개 PASS, 실패/skip 0, cleanup true |
| 실제 Kakao/YouTube/지도 SDK/출처 이동 | PASS | 0 | 후속 승인된 대상·예산의 실제 연결 검증. 아래 상세와 정제 JSON 참조 |
| GitHub 새 E2E Job 실행 | NOT RUN | 해당 없음 | CI 파일만 추가, Push/PR/원격 실행 미수행 |

실행 이력은 실패를 지우지 않는다:

1. 일반 샌드박스 `testClasses`: Gradle daemon loopback 접속 실패, 종료 1. 권한 확장으로 동일 컴파일은 PASS/0. assertion 실패가 아니다.
2. 첫 E2E(08:16:03 UTC): 6 PASS/2 FAIL, 종료 1, cleanup true. B1 이력에서 중첩 summary 선택이 모호했고, 보안 테스트는 만료로 해제된 장소를 재선택하지 않은 상태에서 관계 입력란을 찾았다. 제품 변경 없이 선택자·재선택 절차를 수정했다.
3. 두 번째 E2E(08:18:14 UTC): 7 PASS/1 timeout, 종료 1, cleanup true. A3 직접 승인 응답 직후 공개 목록 갱신 전에 이탈을 시도했다. 요청을 재전송하거나 timeout을 늘리지 않고 화면 이동 버튼의 enabled를 기다리도록 수정했다.
4. 세 번째 E2E: 시작 중 ready JSON의 부분 쓰기를 읽어 실패, 테스트 0개, 종료 1. finalizer도 같은 부분 파일을 파싱해 정리가 중단됐다. 해당 실행 전용 capability로 Backend stop을 보내고 실행 라벨 컨테이너와 18090/18173 listener 부재를 확인한 뒤 실행 폴더만 삭제했다. 준비 파일의 atomic move와 finalizer의 parse 보호를 추가했다. `.tools/e2e-reports/third-startup-20261107T082230Z.json`에 수동 정리 이력을 보존했다.

5. 네 번째 E2E: 2026-11-07 17:24:44~17:25:36 KST(보고서 UTC 08:24:44.095~08:25:36.098), 8 PASS/실패·skip 0, 전체 종료 0, cleanup true. 관련 소스 실행 중 변경 없음. 테스트 소스 지문 `755bb19f35baa829c921dcd65fd09fa9ea822bd3a0d01907b3da2e865b899366`. 보고서 `.tools/e2e-reports/2026-11-07T08-24-44-095Z.json`. 첫 세 번의 실패와 최종 성공을 별도 보존했다.

6. 최종 다섯 번째 E2E: 2026-11-07 17:30:07~17:30:56 KST, 8 PASS/실패·skip 0, 전체 종료 0, cleanup true. CI의 숨김 폴더 내 정제 JSON 업로드 설정, 설치 버전 대조, CI 시작 시각/잘못된 시각 가드, 종료 후 포트 확인, 충돌용 추가 context의 외부 자원 차단을 최종 검토에서 보강해 재실행했다. 관련 소스 실행 중 변경 없음. 지문 `6d941fa8b59526ccbc53aedce3f23bbf8a95a2f8a60dcdccda763c93274b34a2`, 보고서 `.tools/e2e-reports/2026-11-07T08-30-07-769Z.json`. 기본 CSS에 외부 글꼴 import가 있어 추가 context도 동일하게 차단한다. 이 실행이 최종 작업 트리의 E 근거다.

정제 보고서 가드도 별도 검사했다. 원본 PASS는 수락하고 테스트 0건/skip/실패/정리 실패/종료 코드 비정상/오래된 실행/잘못된 시각/빈 CI 시작 표식은 모두 비성공 종료로 거부했다. 검사 후 원본 최신 보고서를 복원했으며 실행별 이력은 수정하지 않았다.

회귀 실행은 2026-11-07 17:19~17:21 KST였으며 JUnit의 실제 테스트 시작/완료 시각은 각 XML에 남아 있다. 이후 제품/기존 회귀 테스트 코드는 변경하지 않았다. E2E 전용 launcher/pull policy/probe/브라우저 테스트의 후속 변경은 최종 E2E의 컴파일·별도 타입 검사·실행으로 검증했다.

후속 최종 작업 트리 전체 검증: 위 외부 실행기 추가 후 `powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Backend -Frontend -Python -E2E`를 실행하여 전체 종료 **0**을 확인했다. Backend **218개**(실패/skip 0), Frontend **110개/16개 파일·타입 검사·빌드**, Python **30개**, 기본 E2E **8개** 모두 PASS/0. 기본 E2E는 2026-11-07 **18:56:00~18:56:55 KST**, cleanup true, 지문 `aa064334f5e15e560b5c6ed745a03d243a6fa68e50b4f5702f32c8e0dc521607`이다. 실제 외부 API는 이 회귀에서 추가 호출하지 않았다. [정제된 현재 검증 근거](harness/evidence/issue-21-current-20261107.json)에 명령·종료 코드·새 JUnit 집계/시각/지문·E2E 전체 결과를 저장했다. 이 결과가 R/E의 최신 공통 근거이며 이전 실패/통과 기록도 보존한다.

## 시나리오별 assertion

| 범위 | 브라우저·응답·DB assertion |
| --- | --- |
| A1 | 실제 장소 검색 후 명시적 지점 선택, 식당만 POST 201. DB chef/video/type/time null, DRAFT version 0, 해당 Mention 0, 공개 식당 없음 |
| A2 | URL의 서버 draftId로 reload·재개, 관계 입력 PATCH 200/version 1, 저장된 초안 승인 200, APPLIED의 appliedMentionId 일치, 승인 Mention·감사·영수증 증가, 공개 응답과 관리자 상세에서 null 시점·기본 영상 URL 일치 |
| A3 | 초안 미생성 직접 POST 201, 식당별 관계 1개, DB와 공개 JSON의 0, 상세 00:00와 `&t=0s`, 성공 영수증 1개 |
| B1 | 정정 저장 후 전체 원본 행·공개 JSON 동일. 재승인 뒤 유형/version만 기대 변경, evidence/candidate/셰프/식당/영상/시점/메모 보존, 후보 원본 동일 |
| B2 | 영향 관계 2개·공개 2개 표시를 확인하고 이름 재승인. 연결된 공개 출처 두 개만 이름 변경, 무관 셰프·Mention 행 전체 동일 |
| B3 | 같은 Kakao ID의 공식 검색 결과 명시 선택. 저장 중 원본·공개 JSON 불변. 승인 뒤 이름/두 null 주소/좌표/URL/version 전체 일치 |
| B4 | 승인 이력의 전후 값·사유·처리자 표시, 승인 당시 영향 확인 표시. 모든 공개 JSON에서 허용 key 집합과 내부 필드/메모 부재 검증 |
| C1 | 대상 관계만 suspend 200·다른 출처 1개 유지, 별도 식당 공개 유지 |
| C2 | 마지막 중단 뒤 공개 JSON에서 식당 없음, 공개 화면 검색 결과 0개, 관리자 상세 전체 관계 2개/공개 0개 유지 |
| C3 | 필수 재확인 누락을 화면에서 차단, DB SUSPENDED 유지. 사유·내용·체크 후 restore 200, version 2·감사 ID/사유·영수증 3개·보존 필드 일치, 공개 JSON과 검색 화면 재등장 |
| 보안 | 익명 GET 401 → 실제 로그인 → CSRF 없는 POST 403/자료 미생성 → 실제 로그아웃/401 → 로그인 → 서버 세션 invalidate → 401 UI 만료 → 동일 계정 로그인·장소 재선택 뒤 작성 메모 유지 |
| 충돌 | 두 BrowserContext에서 서로 다른 실제 계정 로그인, 같은 draft version 읽기. 첫 PATCH 200, 후속 PATCH 409, 둘째 UI 입력 유지·DB version 1/첫 메모 보존 |

DB 제약·원자성·같은 영수증 정확 재생·교차 경합·오류 UI는 아래 기존 PostgreSQL/RTL 테스트에 배분한다. 모든 오류를 E2E에 중복하지 않는다. 서버 session invalidate 검사는 인증 만료 경로를 재현하며 실제 유휴 타이머/장시간 만료 검증으로 표현하지 않는다.

## M1 전체 9개 이슈 근거표

현재 실행 공통 근거 **R**: 위 HEAD + 미커밋 작업 트리에서 실행한 `verify.ps1 -Backend -Frontend -Python` 전체 PASS/0. E2E는 별도 **E** 결과를 따른다. 과거 PR head SHA는 Git 객체와 GitHub PR metadata를 대조했고, 아래 명시한 PR 모두 merged임을 읽기 전용 확인했다. 과거 실행 상세는 [구현 기록](ADMIN_IMPLEMENTATION_PLAN.md)에 보존되어 있으며 PR body의 과거 통과 수를 이번 PASS로 옮기지 않았다.

| Issue·완료 조건 | 구현 파일·병합 PR/과거 기준 | 구체적 테스트 assertion·현재 실행 | 미실행·추가 근거 |
| --- | --- | --- | --- |
| #13 현재/예정/미확인·양쪽 계약·문서 정합성 | `ADMIN_API.md`, `ADMIN_IMPLEMENTATION_PLAN.md`, `DIRECT_REGISTRATION_MIGRATION.md`; [PR #22](https://github.com/Huk9uri/sedanji/pull/22)의 head `d2c44a16f203c3468cceb3bd1b4bd1c02ed57bd4`, 후속 지침 기준 `5d76d4e9b4e557bc3eaf8486987ccc4ca1399137` | Controller/DTO/Security와 `api.ts`·소비 UI 대조. 서버 선택 메모와 기존 후보 UI 필수 메모 차이를 현재 지침에서도 명시. R의 양쪽 회귀 유지 | 독립 런타임 테스트가 아닌 정적 근거. 오래된 AGENTS의 #20 미구현 표기는 현재 코드와 다름(아래 한계 참조) |
| #14 최소 초안·공개 미생성·null/0·version·보안 | `mentiondraft/MentionDraftController`, Service/Input; [PR #23](https://github.com/Huk9uri/sedanji/pull/23), `ffd5efc74830b58f5d54a7a005fba3bc76abc69a` | `MentionDraftIntegrationTest.restaurantOnlyCreateHasExplicitNullsAuditAndNoPublicRelationship`, `patchPreservesOmittedFieldsClearsNullsAndIncrementsUnchangedValuesOnce`, `concurrentSameVersionHasExactlyOneWinnerAndOneUpdateAudit`, `accessControlAndCsrfProtectReadsWritesAndReplays`; R PASS, E A1/A2/충돌 | 실제 운영 DB 적용·장시간 세션 수명은 검증하지 않음 |
| #15 필수 검증·중복·동시 승인·원자성·멱등 | `MentionDraftApprovalController/Facade`, `RegistrationService.approveDraft`; [PR #24](https://github.com/Huk9uri/sedanji/pull/24), `01638fda56e6db584be9ecf640ba40e95f97d342` | `MentionDraftApprovalIntegrationTest.everyMissingReferenceFieldIsReportedWithoutWrites`, `sameKeyConcurrentReplayAndDifferentAdminsSameDraftSerialize`, `everyLateFailureRollsBackAllApprovalWritesAndExpiredReceiptDeletion`, `commitsApprovalAuditReceiptAndPublicSourceAndReplaysSnapshot`; R PASS, E A2 | 강제 PG 경합/롤백은 기존 통합 테스트 근거. E2E 성공만으로 원자성을 대체하지 않음 |
| #16 검색·필터·정렬·페이지·관계/초안 분리·비공개 | `restaurant/AdminRestaurantController/QueryService/QueryRepository`; [PR #26](https://github.com/Huk9uri/sedanji/pull/26) `c150b5149e2c1a6e161e1577c6d35bdd899f1466`, [#27](https://github.com/Huk9uri/sedanji/pull/27) `f5e9ae8e6d435ff5a13a43ab7ad1621d661d3199` | `AdminRestaurantIntegrationTest.searchesAllActualRelationshipsAndCombinesIndependentPredicates`, `filtersSummariesAndStableRestaurantPages`, `detailPreservesRelationshipIdentityNullZeroAndPrivateFields`, `repeatableReadKeepsCountPageAndSummaryOnOneSnapshot`; R PASS, E 목록→상세 | needsSourceReview=null은 정상 판정 아님. 실제 영상 접근 문제 탐지 producer는 후속 |
| #17 장소→저장→재개→승인·직접 승인·오류 입력 보존 | `registration/RegistrationForm/PlaceSearchStep/useRegistration`; [PR #28](https://github.com/Huk9uri/sedanji/pull/28), `1529eb74cec5cc4b0d5781f3e7942d40ee71c878` | `RegistrationForm.test.tsx`의 초안/직접 승인·실패/멱등·이탈·인증 입력 보존, `PlaceSearchStep.test.tsx`의 검색 실패/빈 결과. R PASS, E A1~A3/보안/충돌 | 외부 대역과 실제 Kakao/YouTube 연결을 구분. 실제 외부 NOT RUN |
| #18 목록→상세·관계별 표시·0초·갱신·가짜 기능 없음 | `AdminWorkspace`, `restaurants/RestaurantList/RestaurantDetail`; [PR #29](https://github.com/Huk9uri/sedanji/pull/29) `1904df52dab99f0afb106cb4d0e9c87fae435449`, [#30](https://github.com/Huk9uri/sedanji/pull/30) `7f36acd3873be4f9a3628f75b972fb110b2c15fa` | `AdminWorkspace.test.tsx`, `RestaurantList.test.tsx`의 성공 후 GET 실패·GET 전용 재시도·검색 입력/실행 조건 분리·페이지/복귀·0초 링크. R PASS, E A2/A3/C의 상세/공개 검색 갱신 | 제보 메뉴는 준비 중. 모바일·실제 지도 SDK는 이 회귀의 근거 아님 |
| #19 공개본 분리·공유 정정·재승인·감사·충돌/롤백 | `correction/*`, `CorrectionPanel`; [PR #31](https://github.com/Huk9uri/sedanji/pull/31) `13b9b14b5561e3af48869ea47aff79f6e9d37380`, [#32](https://github.com/Huk9uri/sedanji/pull/32) `28bbdd786708522e7174c48def5976501814cf2e` | `CorrectionIntegrationTest.savingAndPatchingPreserveOriginalAndPublicJsonThenApprovalPreservesEvidenceAndZero`, `confirmationUsesAllImpactRowsRatherThanTheFirstPageAndRejectsWrongHashWithoutWrites`, `failedApprovalAuditRollsBackConfirmationTargetDraftAndReceiptTogether`, `restaurantRebaseKeepsTheEntireOfficialProposalIncludingPreviouslyUnchangedAndNullFields`; R PASS, E B1~B4 | 과거 #19 v3의 후보 radio 최초 FAIL/1와 수정 없는 Frontend 재실행 PASS/0를 모두 보존. 원인 미확정·간헐적 비동기 가능성을 이번 PASS로 삭제하지 않음 |
| #20 일부/마지막 중단·재확인 복구·자동 복구 금지·비공개 | `publication/*`, `PublicationDialog`, 공개 RestaurantRepository/MentionService; [PR #33](https://github.com/Huk9uri/sedanji/pull/33), `6c7c6e8d8ba017379f0cf9b4e67439d38005d516` | `PublicationIntegrationTest.suspendsOnlyTheTargetAndHidesTheLastPublicRestaurantWithoutLeakingInternalData`, `restorationReapprovesAndAuditsTheValidatedPreApplyHashThenOldReceiptCannotUndoIt`, `expiredReceiptDeletionAndEveryWriteStageRollBackTogether`, `correctionOnSuspendedStaysSuspendedAndPublicationInvalidatesAnOlderCorrection`; R PASS, E C1~C3 | 과거 개발 DB 관계 #1 브라우저 검증은 구현 기록의 별도 승인 당시 근거. 이번 개발 DB 접속·중단/복구는 없음 |
| #21 브라우저 연결·외부 결과 구분·모든 이슈 판정·한계 | 이번 launcher/runner/E2E/verify/CI/본 문서. 실행 검증 당시 미커밋, 이후 커밋 기록은 아래 참조. 아직 병합 PR 없음 | R PASS/0. E의 시나리오별 새 보고서·DB probe·자원 정리 판정 참조 | 실제 외부 승인 범위 PASS(후속 기록). 새 CI Job NOT RUN, 원격 Issue/PR/마일스톤은 변경하지 않음 |

## 결과물·비밀정보 정책

`.tools/e2e-report.json`은 최신 실행의 정제된 상태/테스트 수/종료 코드/정리 상태/HEAD/테스트 소스 SHA256을 담는다. `.tools/e2e-reports/*.json`은 실행별 이력을 보존한다. 실행 중 관련 소스가 변경되면 결과를 정상 PASS로 처리하지 않는다. CI는 실행 시작 이후의 보고서·정확히 8개 PASS·실패/skip 없음·종료 0·정리 성공을 함께 요구하고 정제 JSON만 업로드한다.

trace/HAR/video/storageState는 생성하지 않는다. Playwright 기본 실패 출력은 `preserveOutput: never`로 보존하지 않는다. 실패 시의 사용자 정의 screenshot만 `.tools/e2e-masked`에 입력/비밀번호/메모/pre/오류 영역을 mask하여 저장하며 CI upload 대상이 아니다. raw Backend/브라우저 오류·요청/응답·인증값은 runner가 출력/저장하지 않는다. 정제 reporter의 오류는 timeout/locator 모호성/assertion 분류와 테스트 코드 위치로 제한한다. 실행용 임시 비밀번호/capability 파일은 `.tools/e2e/<실행>/ready.json`에만 두고 정상 종료/정리 시 실행 디렉터리를 삭제한다.

## 후속 승인과 실제 외부 연결 검증

사용자가 영상 `RMTJPFKRbEk`/793초, 모리본/서울 광진구 긴고랑로15길 6, `.env`의 필요한 키 이름, 등록 도메인 `http://localhost:5173`을 지정하고 다음 실행을 승인했다: Kakao 검색 1회(기존 구현상 최대 HTTP 3회), 해당 영상 메타데이터 조회·격리 DB 저장/재사용, 임시 관리자, 실제 지도 SDK·마커 표시/갱신, 기본·0초·13:13 출처 링크 이동, 영상 재생 제외. 키 값·개발 DB 설정·실제 관리자 자격정보는 출력하거나 전달하지 않았다.

2026-11-07 **18:52:27~18:53:18 KST**, 공식 Node 22로 `.tools/run-external-check.mjs`를 실행했다. 결과 **PASS/0, cleanup true**. 재현 가능한 증거로 [정제된 외부 결과 JSON](harness/evidence/issue-21-external-20261107.json)에 기준 HEAD·실행 시각·단계별 assertion·허용 대상·런타임·관련 소스 SHA256·한계를 저장했다. 실행 오케스트레이션은 일회성 로컬 `.tools` 스크립트이며 기본 E2E/CI/verify에서 자동 호출하지 않는다. 테스트 런타임의 `ExternalConnectionLauncher`와 명시적 `externalConnectionBackend` task는 별도로 추가했다.

- 기존 `[::1]:5173` 서버를 보존했다. 별도 검증 서버를 `127.0.0.1:5173`에 바인딩하고 검증 Chromium에만 localhost→IPv4 해석을 적용했다. 브라우저 origin은 등록된 `http://localhost:5173`이다.
- 개발 `.env` 전체를 Backend에 상속하지 않고 승인된 세 키만 허용 목록으로 전달했다. Backend는 매번 새 Testcontainers PostgreSQL과 Flyway/JPA validate를 사용하고 임시 계정 `external-check`를 생성했다. 비밀번호는 무작위이며 종료 시 runtime 파일을 삭제했다.
- 테스트 전용 Primary `ExternalApis`는 대상 검색어 `모리본` 1회와 영상 ID 1개의 메타데이터 외부 조회 1회만 허용하고 나머지는 실제 호출 전에 거부한다. 제품 어댑터의 HTTP 경로/파싱/타임아웃을 그대로 호출했다. Kakao 실제 HTTP 횟수는 별도로 계측하지 않았으며 검색 1회와 어댑터의 최대 3회 상한을 구분한다.
- 실제 Kakao 응답은 후보 2개·complete=true였다. 첫 결과 자동 선택 없이 이름과 도로명 주소가 일치하는 모리본, **Place ID 652535611**, 서울 광진구 긴고랑로15길 6(지번 서울 광진구 중곡동 39-64)을 명시적으로 선택했다. 좌표는 longitude 127.083176428393 / latitude 37.5622828427727이다.
- 공식 YouTube 메타데이터의 ID·제목·채널·게시 시각을 실제 API 응답과 DB에서 확인했다. 같은 URL의 메타데이터 등록을 UI에서 다시 수행했지만 외부 조회 수는 1, 저장 Video는 1이었다. 영상의 실제 추천 사실·셰프 관계·시점은 검증하지 않았다.
- 지도 표시용 관계에는 `외부 연결 검증 전용 · 실제 셰프 관계 미검증`이라는 테스트 셰프와 명시적 내부 메모를 사용했다. 이는 임시 DB에서만 존재한 기술 fixture이며 실제 셰프의 근거 관계 생성이나 추천 정확성 입증으로 사용하지 않는다. 실제 장소·영상·관계 저장/공개 호출은 제품 API와 보안을 통과했다.
- 실제 지도 SDK HTTP 200과 실제 Map/Marker를 관측했다. constructor/method 관측은 원래 SDK로 그대로 위임하며 성공 응답이나 가짜 마커를 만들지 않았다. 브라우저 필터로 실제 활성 마커 **1→0→1**, 식당 선택 시 실제 panTo를 확인했다. 마스킹 screenshot을 로컬 결과 폴더에서 시각 확인했다. 지도 타일 전체의 로딩·장시간 안정성은 별도 검증이 아니다.
- 기본 URL, `t=0s`, `t=793s`의 실제 YouTube 페이지 이동은 각각 HTTP 200, 대상 ID 일치였다. HTMLMediaElement.play를 차단하고 미디어/Googlevideo 요청 19건을 차단했다. 재생 중인 media 없음도 검사했다. HTTP 200 이동은 재생 가능성·영상 내용·삭제/비공개 자동 판정의 근거가 아니다.
- 부작용은 승인된 외부 API 조회·지도/YouTube/글꼴 등 브라우저 자원 요청·임시 DB fixture/Video 저장·마스킹 이미지/정제 보고서 생성이다. 제품/개발 DB 접속·변경·중단/복구는 없다. 종료 후 자신이 만든 서버·DB를 정리하고 기존 IPv6 서버는 유지했다.

## 남은 한계와 외부 검증 범위

- 실제 Kakao Local: 식당명·지점·주소가 포함된 검색어 1개와 검색 1회/최대 HTTP 3회 예산.
- 실제 YouTube: 영상 URL 1개, 격리 DB의 메타데이터 등록·재사용 허용 범위.
- 지도 SDK: 등록된 테스트 도메인·브라우저 키의 로컬 설정 위치, SDK 자원 요청과 마커 표시/갱신 허용 범위.
- 계정: 계정명·자격정보를 읽을 로컬 설정 위치. 비밀번호/API 키 값을 대화·문서·Git에 넣지 않는다.
- 출처: 기본/null 시점 URL과 `t=0s` 링크의 허용 탐색 범위, 재생 허용 여부. 링크 형식 검사와 실제 영상 근거 정확성은 구분한다.

실제 실행은 쿼터·브라우저 자원 요청·격리 DB Video 저장·결과 파일 생성의 부작용을 별도 기록해야 한다. 이번 후속 승인은 위 대상과 예산으로만 보류를 해제했으며, 개발 DB를 이용한 중단·복구는 여전히 제외다.

기본 E2E는 지도 키가 없으므로 SDK 연결·마커 실제 렌더링을 입증하지 않는다. 마커 수명/갱신은 기존 `KakaoMap.test.tsx`의 모의 SDK 회귀 근거다. 장시간 안정성·실기기/모바일·운영 배포·부하·운영 DB/이관·실제 영상 재시청·출처 자동 탐지는 미검증이다. 하위 AGENTS.md에는 #20 공개 제어가 아직 없다는 과거 설명이 남아 있으나 현재 `publication` 코드/테스트/구현 기록을 근거로 현황을 판단했다. 이번 하네스 범위에서 제품 규칙이나 지침 문서 전체를 재작성하지 않았다.

기본 E2E 구현 당시 정적 점검: 변경 파일 18개(기존 8개·신규 10개), staged 0. 기존 dependency 버전 변경·패키지 삭제 0, 추가 패키지는 @playwright/test·playwright·playwright-core 3개뿐이다. 신규 포함 공백/마지막 개행·문서 링크 문제 0, `git diff --check` 종료 0. 최종 전용 포트·E2E 라벨 컨테이너·임시 자격정보 디렉터리 잔존 없음. 과거 실패에 대한 무제한 제품 수정 없이 하네스/fixture/선택자/대기만 보완했다.

후속 최종 정적 점검: 변경 파일 21개(기존 8개·신규 13개), staged 0. 외부 전용 launcher/task와 정제 근거 JSON 2개를 추가했으며 제품 소스·migration 변경은 없다. 신규 포함 공백·개행·문서 링크·키 값 비노출을 검사하고 `git diff --check` 종료 0을 확인했다. 기본/외부 검증 컨테이너·전용 서버·임시 자격정보는 정리했고 기존 `[::1]:5173` 서버는 보존했다.

## 후속 커밋 승인

사용자가 3개 단위 커밋을 승인했다. `3a936ba`는 E2E/격리 환경·실제 외부 전용 launcher, `060a990`은 명시적 verify 선택·독립 CI Job을 저장한다. 세 번째 문서 커밋은 본 기록·검증 안내·정제 근거 JSON 2개를 포함한다. 위 검증 후 실행 소스 내용은 변경하지 않았으며 커밋 단계에서는 문서의 시점 표현만 바로잡았다. 커밋 후 테스트를 다시 실행했다고 주장하지 않는다. Push·PR 생성·원격 CI 실행·Issue/마일스톤 변경은 수행하지 않았다.

## PR #34 최초 CI 실패와 이력 선택 경쟁 수정

[CI run 42362625629](https://github.com/Huk9uri/sedanji/actions/runs/42362625629)의 실제 artifact `13570720101`을 읽었다. PR head `e9fc70ecfbdc44925962c66b75c8f409872d97b7`, 실제 checkout merge SHA `93f2cfc71fe1234db97487ed034da01c9e83ce2d`. Backend/Frontend/Python Job은 success, E2E는 **7 PASS/1 FAIL**이며 cleanup true/exit 1이었다. 실패는 B1/B4의 이전 `admin.spec.ts:151`, 펼친 이력의 “변경 전” 표시 대기 timeout이다. 뒤의 `--validate-report` 오류는 앞선 실제 테스트 실패를 올바르게 거부한 후속 결과다.

`CorrectionPanel.write`는 승인 응답으로 APPLIED 편집 상태를 먼저 갱신하고 후속 history GET을 기다린다. 기존 테스트는 APPLIED만 기다린 뒤 동일 사유의 첫 `details`를 선택했다. 생성 이력을 펼친 후 승인 이력이 목록 앞에 삽입되면 `.first()`가 새로 삽입된 닫힌 승인 이력을 가리킬 수 있다. 제품의 승인·감사 저장 문제로 해석하지 않는다.

수정은 E2E 선택자와 보고서 실패 안내만 포함한다. B1에서 실제 후속 history GET을 gate로 지연하고 기존 CORRECTION_CREATED가 표시되는 동안 승인 이력이 아직 없음을 확인한다. gate 해제 후 `CORRECTION_APPROVED · B1 근거 재확인`이라는 특정 이벤트가 나타날 때까지 기다려 그 항목만 펼친다. 원래 전후 값·사유·처리자·승인 당시 확인값·DB/공개 JSON assertion은 유지하고 승인 action assertion도 추가한다. 응답은 `route.continue()`로 실제 서버에서 받으며 성공 응답을 mock하지 않는다. worker/retry/timeout/공개·보안·DB 규칙을 바꾸지 않았다.

`--validate-report`의 기존 거부 조건을 유지하면서 원시 오류/요청 데이터 대신 통과/전체 건수·skip·exit·cleanup을 출력한다. 실제 실패 CI 보고서는 `passed=7/8; skipped=0; exit=1; cleanup=true`로 계속 거부했다. 별도 임시 경로에서 정상/0건/skip/정리 실패/비정상 종료/잘못된 시각/오래된 결과/빈 CI 시작 표식/잘못된 JSON 등 10가지 진입점 검사를 수행했고 모두 예상 결과였다.

로컬 검증 명령은 `powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Frontend -E2E`, 전체 종료 **0**이다. Frontend 타입 검사·16개 파일/110개 테스트·빌드 PASS/0, E2E 별도 타입 검사와 지연 GET 회귀를 포함한 **8개 전부 PASS/0**, 실패/skip 0, cleanup true. E2E 실행은 **2026-11-07 19:46:53~19:47:49 KST**, 기준 HEAD는 e9fc70e 위 미커밋 수정, 소스 지문은 `60d5de0ad1360f49992635db026f68abc8a6068180011cc9bf2efca4e63b1b25`이다. Backend/Python은 실행 코드 변경이 없어 이번 로컬 수정 검증에서 NOT RUN이며 최초 CI success와 구분한다. 실제 외부 API 재호출·개발 DB·원격 재실행·커밋·Push는 하지 않았다.

[CI 실패와 로컬 수정 근거 JSON](harness/evidence/issue-21-ci-fix-20261107.json)에 최초 FAIL과 수정 후 PASS를 함께 보존했다. 수정본의 GitHub Ubuntu CI 통과는 아직 확인하지 않았으며 로컬 성공으로 대체하지 않는다.
