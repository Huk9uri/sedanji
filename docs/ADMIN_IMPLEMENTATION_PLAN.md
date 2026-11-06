# 관리자 식당 관리 1단계 구현 계획

작성일: 2026-11-02. 현황 갱신일: 2026-11-07. 상태: #19 작업 트리와 과거 구현·검증 기록을 구분한다. 실제 개발 DB·외부 API는 실행하지 않았다.

## #19 Handoff v3 — 승인 확인 감사·공식 장소 rebase 보완

기준 `feat/AdminPage` / `13b9b14b5561e3af48869ea47aff79f6e9d37380`, 시작 staged·unstaged·untracked 없음. 2026-11-07 사용자가 v3 구현과 기존 `verify.ps1 -Backend -Frontend` 실행을 승인했다. Implementer·API Change·DB Migration·Verification 및 루트·Backend·Frontend 지침을 확인했다. 기존 사용자 변경·V1~V5·공개 DTO·생성/PATCH/rebase 감사·과거 감사는 보존한다.

- `CorrectionService`: 승인 전 전체 영향 hash와 건수·대상/정정안 version을 불변 record로 캡처하여 감사에 전달한다. 적용 후 조회 스냅샷과 분리하며 새 감사의 approvalConfirmation에만 저장한다. Restaurant rebase는 저장된 공식 제안 전체를 보존하고 기준·변경 필드만 다시 계산한다. Mention·Chef rebase·잠금·영수증·원자성은 유지한다.
- `CorrectionPanel`·타입: 선택적 승인 확인 객체를 처리하고 “승인 당시 영향 확인”을 표시한다. 필드 없는 과거 이력은 기존대로 표시하며, Restaurant 기준 갱신/재선택·구버전 혼합 제안의 수동 재선택을 안내한다. 새 UI 구조·의존성·메뉴는 추가하지 않는다.
- PostgreSQL assertion: 세 종류 승인 H0 저장과 적용 후 H1 구분, 26개 전체 영향(첫 페이지 20개)·공개 14개, 잘못된 hash 무부작용, 같은 키 재생·이후 참조 변경에도 감사 불변, 감사 실패의 원본/정정안/확인 객체/영수증 롤백을 보강한다. Restaurant의 이전 기준과 같던 주소·좌표·URL 및 null 주소 보존, stale version 거부, 최신 기준·version·감사 반영과 공개 JSON 불변, 승인 시 전체 공식 값 적용·Kakao ID/다른 식당 보존, 공식 토큰 PATCH만 전체 제안을 교체하는 사례를 검증한다. 기존 Mention rebase·GET 전용 재시도 회귀는 보존한다.

기존 Backend 199개·Frontend 97개 PASS는 v2 실행 기록이다. 이번 v3 작업 트리의 `powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Backend -Frontend` 실행에서 Backend 본체/테스트 컴파일·207개 테스트(실패/skip 0), Frontend 타입 검사·빌드는 각각 PASS/0이었다. 기존 App 후보 승인 테스트 1건이 Kakao 검색 뒤 radio 대기에서 실패해 첫 전체 종료 코드는 1이다. 해당 후보 경로의 초기 GET/검색 POST 비동기 흐름을 확인한 뒤 코드·assertion·설정·timeout 변경 없이 동일 진입점의 `-Frontend`만 재실행했다. 재실행은 13개 파일·99개 테스트·타입 검사·빌드 PASS/0, 전체 종료 0이며 실패는 재현되지 않았다. 최초 실패를 삭제하거나 v2 PASS로 대체하지 않는다. 기존 후보 테스트의 간헐적 비동기 실패 가능성은 별도 검증 한계로 남긴다.

Backend 패키징은 backend 디렉터리의 `.\gradlew.bat --gradle-user-home C:\workspace\sedanji\.tools\gradle-home --offline --no-daemon '-Porg.gradle.java.installations.auto-download=false' bootJar`로 PASS/0이다. 기존 실행 중인 Docker Engine·캐시된 이미지와 Testcontainers 임시 DB만 사용했다. Python은 변경 영향이 없어 NOT RUN(미선택), 실제 브라우저·개발/운영 DB·외부 API·배포 검증은 미실행이다. 실제 DB·외부 API·Docker/서버 시작·설치·다운로드·Git fetch/브랜치/커밋/Push/PR 편집은 수행하지 않는다. 신규 migration·과거 감사 수정·자동 백필·혼합 제안 자동 복구도 없다.

정적 검토: 변경 추적 파일 7개·신규 파일 0개, 상대 import 14개·로컬 문서 참조 22개 누락 0, 공백/마지막 개행 문제 0, `git diff --check` 종료 0이다. 감사/공식 제안 보존 이외의 런타임 API·공개 DTO·migration·의존성 변경이 없으며 staged 변경도 없다.

## #19 전체 구현 — Handoff v2 D1~D9

2026-11-07 사용자가 전체 1~3단위 구현을 승인했다. 기준은 `feat/AdminPage` / `7f36acd3873be4f9a3628f75b972fb110b2c15fa`, 시작 작업 트리는 깨끗했고 기존 #18 후속 3개 커밋을 보존했다. 루트·Backend·Frontend 지침과 Implementer·API Change·DB Migration·Verification Skill을 확인했다. 최초 실행 금지는 이후 사용자 검증 승인으로 해제됐다. 설치·실제 DB/외부 API·Docker/서버 시작·Git 변경 금지는 유지한다.

1. V5는 기존 CORRECTION의 기준/제안/변경 필드/참조 JSON·적용 정보를 확장하고 Restaurant/Chef Revision과 DRAFT 부분 UNIQUE·FK·상태/사유/version 제약·조회 인덱스·감사 Revision FK를 추가한다. JDBC 저장/조회와 기존 JPA 대상 Entity의 제한된 적용 메서드를 사용한다. V1~V4·과거 자료·append-only trigger는 수정하지 않는다. 기존 스냅샷 없는 CORRECTION은 백필 없이 명시적 제안 보완/rebase 전 승인 차단한다.
2. CorrectionService/Store·ReapprovalFacade·공통 Coordinator는 엄격 입력·버전/참조/영향 검증·재승인·감사·24시간 성공 영수증을 담당한다. 후보·직접·CREATE 승인에는 공통 잠금 연결만 추가했다. 공개 다중 조회의 read-only REPEATABLE_READ와 원본 candidate/evidence·SUSPENDED 보존을 적용했다. 실패 트랜잭션 안에서 UNIQUE 후 추가 조회하지 않는다.
3. CorrectionPanel·전용 타입/래퍼와 Workspace/상세 진입은 비교·영향·이력·충돌·입력/인증 세대·단일 이탈 가드·멱등 재확인을 제공한다. 저장은 정정안/이력만 갱신하고 재승인은 기존 목록·상세·공개 조회 갱신 흐름에 연결한다. 숨겨진 이전/새 상세는 재진입 시 조회한다. 조회 실패는 쓰기 실패로 전파하지 않는다.

작성한 테스트: V4→V5 업그레이드·기존 근거/감사/legacy 기준 null 보존·부분 UNIQUE/FK/append-only·상태/사유 제약, 원본/공개 JSON 보존, null/0·SUSPENDED·후보 원본 출처 보존, 재승인/영수증 재생·만료·버전/참조/영향/중복 충돌, 명시적 rebase·legacy 처리, 대상/정정안/감사/영수증 단계별 테스트 DB 실패 주입 롤백, 동일 키 동시 승인, 기존 CREATE 승인과 공유 정정 경합, UI 비교·충돌·입력 보존·후속 GET 실패·동일 키 재확인·PATCH 재전송 금지·상세 진입·API 라우팅을 작성했다. 기존 후보/직접/CREATE 경합 테스트의 장벽을 공통 잠금 앞으로 이동했고 원본/감사/영수증/중복 assertion은 보존한다. 잠금 비참여 쓰기의 DB UNIQUE 충돌을 별도 연결로 주입해 facade가 롤백 뒤 조회하는 사례도 유지한다.

검증: 최초 작성 당시에는 사용자 실행 제한으로 NOT RUN이었다. 2026-11-07 사용자가 컴파일·테스트·빌드 검증을 승인했다. `powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Backend -Frontend`의 첫 실행은 Backend 199개 중 21개 실패로 종료 1, Frontend 타입 검사·13개 파일의 97개 테스트·빌드는 각각 PASS/0이었다. Spring Repository 프록시의 필드 직접 접근을 접근 메서드로 수정하고, 공유 감사 nullable ID의 삼항식 unboxing 오류를 수정했다. 신규 migration fixture의 승인 필수값과 잠금 spy의 실제 target 설정을 바로잡았으며, 저장소 인터페이스 spy는 실제 JPQL 조회로 대체했다. 영수증 동시 재생 테스트는 Jackson 숫자 노드 구현 타입 대신 전체 직렬화 JSON을 비교한다. 기대 필드·원자성·중복 assertion을 완화하거나 검증 설정을 변경하지 않았다.

Backend 수정 후 동일 진입점 `-Backend` 재실행은 6개 실패/종료 1, 원인 수정 뒤 최종 `-Backend` 실행은 본체·테스트 컴파일 및 **199개 테스트 PASS(실패/skip 0), 전체 종료 0**이다. Frontend는 첫 실행 후 코드 변경이 없어 통과 결과를 유지하며 불필요하게 재실행하지 않았다. Backend 패키징은 backend 디렉터리에서 `.\gradlew.bat --gradle-user-home C:\workspace\sedanji\.tools\gradle-home --offline --no-daemon '-Porg.gradle.java.installations.auto-download=false' bootJar`로 PASS/0이다. 최초 인용하지 않은 -P 인수가 PowerShell에서 분리되어 작업명 오류/종료 1이었고, 인수 인용 후 동일 오프라인 패키징에 성공했다. 기존 캐시·실행 중인 Docker Engine과 임시 Testcontainers DB만 사용했으며 설치·다운로드·이미지 pull·Docker/서버 시작은 하지 않았다. Python은 변경 영향이 없어 NOT RUN(미선택)이다. 실제 브라우저·개발/운영 DB·외부 API·배포도 미실행이다. 이전 85개 PASS는 #18 결과다. PostgreSQL 테스트 코드는 임시 Testcontainers DB 전용이며 실제 DB에서 fixture/실패 주입 SQL을 실행하지 않는다. 환경이나 검증 설정을 우회하지 않는다.

정적 검토는 기존 추적 15개·신규 18개(총 33개)의 diff/실제 내용, 상대 import 43개·로컬 문서 참조 39개, 신규 파일 포함 공백·마지막 개행을 확인했다. 참조 누락·공백 문제 0, `git diff --check` 종료 0이다. staged 변경 없음. V1~V4·의존성·설정·공개 DTO·무관한 화면 변경 없이 승인 범위를 대조했다. 이 결과는 컴파일/실행 성공이나 운영 데이터 안전성의 증거가 아니다. PostgreSQL 경합·실패 주입·공개 다중 조회 스냅샷 및 UI assertion의 실행 결과는 위 검증 기록을 따른다.

배포는 V5 확장 스키마 후 새 앱 순서다. ALTER/FK는 강한 테이블 잠금과 기존 행 검증, 일반 인덱스는 쓰기 차단/스캔 비용이 있어 운영 적용 시간은 미확인이다. 실제 데이터 분포·Flyway 적용 상태·락 대기·구/신 앱 혼재·부하·백업 복원은 미확인이다. 기존 앱은 추가 컬럼을 무시할 수 있으나 공통 잠금에 참여하지 않으므로 정정 승인 기능을 구/신 쓰기 앱과 혼재 운용하지 않는다. 실제 이관·백필·repair·clean·데이터 정리를 실행하지 않는다. 현재 계약은 [ADMIN_API.md](ADMIN_API.md)의 #19 절을 따른다.


## #18 후속 보완 — Handoff v4 P1~P3

기준: `feat/AdminPage` / `1904df52dab99f0afb106cb4d0e9c87fae435449`. 시작 시 staged·unstaged·untracked 없음. #18 본 기능은 인계 기록상 PR #29로 병합됐으며 새로 구현하지 않는다. 인계의 main `ecd92a6564a1e5a50d7d8b8255bbb7da7c31ffa6`은 로컬 객체가 없어 파일 동일성을 재확인하지 못했다(`git diff --name-status` 종료 128). fetch는 하지 않았으며 현재 HEAD는 인계 기준과 일치한다. 승인된 Frontend·테스트 코드·문서만 보완한다.

- P1: 검색 제출 전 trim 후 Unicode code point 100/101 경계를 검사한다. 잘못된 검색은 요청·실행 조건·페이지·결과를 바꾸지 않는다. 전체 빈 목록/실행 조건 0건 안내를 구분하며 입력 중 검색어·로딩·조회 실패로 빈 상태를 판단하지 않는다.
- P3: Workspace의 별도 확인 GET을 제거했다. 성공 이벤트의 이전/새 식당·초안·관계 ID를 보관한 뒤 revision·페이지를 갱신한다. 활성 hook만 서버 조회를 소유하고 숨겨진 목록·이전/새 상세·초안은 재진입 시 새로 읽는다. 실제 활성 조회와 공개 조회의 오류를 표시하며 관련 조회 재시도는 실패한 GET만 수행한다. 쓰기·승인 요청을 재전송하지 않는다. 요청 취소·선택·조건·인증 세대와 기존 단일 이탈 가드·멱등 재확인은 유지한다.
- P2: 목록 100/101 code point(보조 평면 문자 포함), 빈 안내·실행 조건/페이지/결과 보존, 직접 승인 R10/M301·초안 D201 APPLIED·0페이지, 승인 후 조회 실패와 GET 전용 재시도, 숨겨진 대상 중복 조회 방지·재진입, R10→R20 초안 이동 후 양쪽 재조회, 늦은 401 무시 assertion을 작성·보강했다. 기존 필터 이탈·페이지 단일 보정·0초/null·인증 입력 보존·후보·직접·초안 승인 assertion을 보존한다.

최초 보완 작성·리뷰 단계의 타입 검사·테스트·빌드는 NOT RUN(사용자 실행 제한)이었다. 2026-11-07 사용자가 컴파일·실행 검증 및 통과 후 커밋을 승인했다. `powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Frontend` 실행에서 최초 샌드박스의 PowerShell 권한 확인이 차단되어 종료 1이었다. 실행 정책·검증 설정을 변경하지 않고 권한 확장으로 동일 명령을 재실행했다. 타입 검사·11개 파일의 85개 테스트·빌드는 각각 PASS/0, 전체 종료 코드와 직후 `$LASTEXITCODE`는 0이다. Backend·Python은 변경 영향이 없어 NOT RUN(미선택)이다. 실제 브라우저 확인은 수행하지 않았고 아래 69개 PASS는 최초 #18 당시 기록이다. 모의 API·jsdom 검증을 Backend 쿼리나 실제 서비스 E2E 검증으로 표현하지 않는다. Backend API·DB·Flyway·공개 DTO·의존성 변경은 없다. 설치·외부 API·서버·Docker·실제 DB·fetch·브랜치 변경·Push·PR 생성은 수행하지 않는다. 승인된 커밋은 검색 보완·갱신 구조 보완·문서 정리의 세 단위로 분리한다.

정적 검토: 변경 파일 7개(기존 추적 6·신규 테스트 1), 상대 import 25개·로컬 문서 참조 20개 누락 0, 신규 파일 포함 공백·마지막 개행 문제 0. 실제 diff의 범위·비밀정보를 검토했으며 `git diff --check` 종료 0이다. staged 변경은 없다.

## #18 최초 구현 — 당시 작업 트리·검증 기록

기준: `feat/RegistrationService` / `1529eb74cec5cc4b0d5781f3e7942d40ee71c878`. 시작 시 staged·unstaged·untracked 없음. 로컬에 있는 main 기준 `73d4757fc9b75ff432a39c6a9fe63b24f65070df`와 파일 비교(`git diff --name-status`)는 차이 없음·종료 0이다. fetch·브랜치 변경은 하지 않았다. 이슈 #18은 조회 시 open·댓글 0, 선행 #4·#5 번호 표기는 실제 #16·#17과 불일치하며 이슈를 수정하지 않았다.

사용자는 Handoff v2의 D1~D7과 Frontend·테스트 코드·문서 변경을 승인했다. 최초에는 실행 검증을 승인하지 않았고, 이후 “실행 검증도 해봐”로 실행을 허용했다. 루트·Backend·Frontend AGENTS 및 Implementer·API Change·DB Migration·Verification의 실제 본문을 읽고, 서버 계약·저장 모델 보존과 실행 제한을 적용했다. DB 변경 필요성은 발견하지 않았다.

### 구현 책임과 승인 범위

- `App.tsx`: 공개 지도 보존, 관리자 세션·로그인/로그아웃·인증 세대와 외부 이동 가드. 다른 계정은 작업 영역을 새로 만들고 동일 계정 재로그인은 등록 입력을 유지한다.
- `AdminWorkspace.tsx`: 두 업무 메뉴·목록/상세/등록/기존 후보 화면·복귀 조건/스크롤·선택 식당·진입 의도·완료 이벤트와 관련 GET 갱신 조정.
- `restaurants/types.ts`·`restaurantApi.ts`: #16 전체 DTO와 nullable 출처 판정·페이지, query 직렬화·signal. 목록/상세 hook은 취소·세대 검증과 독립 오류·페이지 보정을 담당한다.
- 목록·상세 컴포넌트: 실제 서버 건수·셰프 미리보기·공개/출처 상태, 장소·관계별 필드·0/null 링크와 초안 목록. 미구현 업무 실행 UI는 없고 제보 메뉴는 비활성이다.
- #17 hook·Form: 선택 식당 고정 GET·restaurantId 초안 생성·초안 승인, 서버 초안 재개, 결과 ID 기반 완료 이벤트·성공 유지·명시적 상세 이동. 기존 키·요청 스냅샷·dirty·version·APPLIED 보호를 유지한다.
- `LegacyCandidateReview.tsx`: 기존 후보 보조 화면을 옮겨 보존했다. `CandidateReviewCard`에는 인증 실패 전달만 추가했으며 승인 필수값·본문·시점·메모 규칙은 바꾸지 않았다.

### 작성한 테스트와 검증 한계

Vitest·RTL 코드에 R10/R20, 관계 M101/M102(0초/null), 초안 D201을 사용한다. 기본 목록·두 메뉴·입력/실행 조건 분리·필터 페이지 초기화·복귀 조건, 관계 카드별 필드·공식 링크, 관계/초안 독립 페이지·오류, 늦은 검색/상세 응답·인증 세대 폐기, 범위 밖 페이지의 단일 보정, ID 기반 관계 추가·POST 없음·명시적 초안 저장·재개, 성공 후 GET 실패와 GET 전용 재시도·필터 제외 유지, 단일 이탈 dialog를 assertion으로 작성했다. 기존 등록 테스트에는 성공 결과 ID·완료 이벤트 실패 격리와 이전/새 식당 ID 전달을 보강했다. 기존 공개·후보·멱등 재확인 assertion은 보존하고 후보 진입 동선만 새 메뉴 구조에 맞췄다.

최초 정적 검토 시 타입 검사·테스트·빌드는 NOT RUN(사용자 실행 제한)이었다. 이후 실행 승인에 따라 `powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Frontend`를 실행했고 직후 `$LASTEXITCODE=0`을 확인했다. Frontend 타입 검사·10개 파일의 69개 테스트·빌드는 각각 PASS/0이며 전체 종료 코드도 0이다. 기존 샌드박스의 PowerShell 실행 정책 레지스트리 접근 제한에 맞춰 권한 확장으로 동일 진입점을 실행했으며 실행 정책·검증 설정은 바꾸지 않았다. 설치·다운로드·서버 시작은 수행하지 않았다. Backend·Python은 관련 변경이 없어 NOT RUN(미선택), 실제 브라우저·외부 API·운영 DB·배포 검증은 수행하지 않았다. #17의 Backend 184개·Frontend 51개 PASS는 과거 기록과 구분한다.

Backend API·DB·Flyway·의존성·설치·실제 외부 API·서버·배포·Git 이력 변경은 수행하지 않았다. Handoff의 인증 만료 문장은 주변의 입력 보존·동일 계정 재로그인 요구에 맞춰 “양식을 즉시 unmount하지 않음”으로 적용했다.

이번 정적 검사: `git diff --check` 종료 0, 변경 문서/지침의 로컬 Markdown 참조 20개 누락 0, 변경 TS/TSX의 상대 import 경로 누락 0, 신규 파일 12개의 공백·개행 문제 0. 변경 파일은 기존 추적 파일 10개·신규 파일 12개이고 staged는 없다. 실제 diff와 추가 소스의 비밀정보·범위 검토에서 문제를 발견하지 않았다. 상대 경로·공백 검사 통과는 TypeScript 컴파일이나 테스트 통과를 뜻하지 않는다.

## #17 직접 등록 화면·초안 재개 — 승인된 작업 트리

기준 브랜치 `feat/RegistrationService`, HEAD `f5e9ae8e6d435ff5a13a43ab7ad1621d661d3199`, 시작 시 staged/unstaged/untracked 없음. #16 후속 보완 커밋과 기존 Backend·V4는 그대로 보존했다. 이슈 #17은 조회 당시 open·댓글 0이며 본문 선행 #2·#3 표기를 수정하지 않았다. 실제 선행 기능은 #14·#15다.

사용자는 계획 10번의 추천안 8개를 승인했다. 기존 hooks·CSS·중첩 장소 응답·metadata 전용 경로를 유지하고, dirty 초안은 저장 후 승인하며, 작은 식당→초안 선택기와 URL draftId로 재개한다. 입력·요청은 메모리에서 계정별로 관리한다. 신규 의존성·Router·Backend·DB 변경 없이 [현재 API 계약](ADMIN_API.md)의 #17 화면을 연결했다.

책임은 `registrationApi.ts`의 typed endpoint, `registrationInput.ts`의 null/0·시점·변경 필드 계산, `useRegistration.ts`의 baseline·요청 잠금·고정 멱등 요청·충돌, 장소/관계/재개 컴포넌트, `RegistrationForm.tsx`의 검토·이탈 확인, `App.tsx`의 진입·로그인·공개/관리자 갱신으로 나뉜다. 실제 출처 확인 producer와 #18 전체 목록·상세·정정·공개 제어는 제외한다.

### #17 이번 실행 검증 — 2026-11-06

최초 명령 `powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Backend -Frontend`, 직후 종료 코드 0. 기존 샌드박스의 실행 정책 레지스트리 제한을 피하기 위해 권한 확장으로 동일 명령을 실행했으며 정책·사전검사·설정은 변경하지 않았다. 기존 Engine·캐시 사전검사를 통과했고 설치·다운로드·이미지 pull·Docker/서버 시작은 하지 않았다.

- Backend 컴파일·테스트 184개 PASS, 실패·skip 0, 종료 코드 0. #16 후속 보강 6개를 포함한 이번 실행 결과이며 과거 178개 기록과 구분한다.
- 최초 Frontend 타입 검사·32개 테스트·빌드 PASS 후, 세션·이탈·경계 assertion을 보강했다. 동일 진입점 `-Frontend` 최종 재검증에서 타입 검사·51개 테스트·빌드 각각 PASS/0, 전체 종료 0이다. Backend 실행 코드·테스트는 변경하지 않아 재실행하지 않았다.
- 중간 재실행에서는 초안 입력값이 표시된 직후 아직 로딩 중인 비활성 검토 버튼을 클릭한 테스트 1개가 FAIL(전체 종료 1)이었다. 입력값 표시와 검토 버튼 활성화 모두를 기다리도록 테스트 동기화를 수정했고 최종 51개 PASS를 확인했다. 기대 결과·제품 로딩 차단을 완화하지 않았다.
- Python은 관련 변경이 없어 NOT RUN(미선택). Backend bootJar·실제 브라우저·지도·배포 E2E는 실행하지 않았다. 실제 개발·운영 DB·외부 API·서버·배포·Git 이력 변경은 하지 않았다.

최종 정적 검사: `git diff --check` 종료 0, 변경 문서·Frontend 지침의 로컬 Markdown 참조 18개 누락 0, 신규 파일 13개 공백·개행 문제 0. 추가 소스·diff의 비밀정보·범위 검토에서 문제를 발견하지 않았다. 실제 Git stage·커밋·Push·PR 생성은 수행하지 않았다.

assertion은 장소만 저장·필수값 승인 차단, 시점 null/0/204/3723과 범위 오류, 동명이인 ID·명시적 생성, metadata 경로·URL 변경 무효화, 검색 빈 결과/실패/일부 결과·순서 역전, 장소 410 복구, 저장→닫기→식당 선택기 재개→승인, PATCH 선택값 null/미변경 누락·최신 version, 중복 클릭·생성/승인 응답 소실의 동일 키 재확인, PATCH/셰프 생성 자동 재전송 금지·조회 비교, 중복/stale/APPLIED, 승인 성공 후 목록 갱신 실패, 401/403·계정 전환·이탈 가드와 기존 공개·후보·래퍼 회귀를 검사한다. 서버와의 실제 브라우저 세션 및 외부 서비스 동작은 검증하지 않았다.

## #16 병합 상태와 제한된 후속 보완

- 기준 브랜치 `feat/RegistrationService`, HEAD `c150b5149e2c1a6e161e1577c6d35bdd899f1466`. 이전 인계의 PR #26 병합 기준은 main의 `ce8ceeff27a8e7b7af9919ca05c69cab80ec66dd`이며 당시 파일 차이가 없었다는 기록이다. 이번에는 로컬에 병합 커밋 객체가 없어 동일성을 재확인하지 못했고 fetch하지 않았다. 선행 #13은 인계상 완료됐다.
- 관리자 식당 검색·공개/출처 필터·페이지·관계 상세와 V4 출처 저장 모델이 구현됐다. 실제 확인·갱신 producer·source-check API·배치와 Frontend 타입·UI는 제외 상태다. 현재 계약은 [ADMIN_API.md](ADMIN_API.md) #16 절을 따른다.
- 사용자가 문서 정합성과 제한된 테스트 보강을 승인했다. 이번 변경은 혼합 출처·거절 전용/중복 검색·상세 필드 대응/외부 호출 없음·페이지 경계·Unicode 길이·상세 권한 assertion과 지침/문서 정리에 한정한다. 기존 런타임 API·승인·V4는 변경하지 않는다.
- 이번 테스트·빌드·DB 실행은 기존 제한에 따라 NOT RUN이다. PR #26의 Backend 178개·Frontend 11개 등 과거 PASS와 구분한다. 아래 #15 이하의 작업 트리·이번 검증 표현은 각 작업 당시 기록이다.

## #15 구현 작업 트리

- 승인된 CREATE 초안 승인 API·strict 입력/전용 오류·NEVER facade와 RegistrationService 트랜잭션, apply 전이·상태 무관 관계 ID 조회·APPROVE_DRAFT 감사를 구현했다.
- 기존 V3·관리자→초안 잠금·MentionService MANDATORY 생성·범용 200 영수증을 재사용한다. 직접/후보/#14 계약, APPROVED AND PUBLISHED 공개 조건과 내부 정보 비공개를 유지한다.
- 기존 사용자 AGENTS.md·backend/AGENTS.md와 Planner 문서 변경을 보존한다. migration·Frontend·의존성·Git 이력 작업은 변경하지 않는다.
- 입력·PG/MockMvc 테스트는 필수값·null/0·전용 오류·멱등/만료·상태별 전체 행 불변, 저장 단계별 롤백·실제 UNIQUE 경합·직접/후보 양방향·PATCH 양방향·보안/공개를 검증한다. 아래 설계 인계와 #14 이하는 과거 기록이다.

### #15 이번 검증 결과 — 2026-11-06

최종 명령: `powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Backend -Frontend`, 직후 `$LASTEXITCODE=0`.
Backend 컴파일·테스트 168개(실패·skip 0, 신규 승인 통합 사례 30개와 입력 테스트 1개 포함), Frontend typecheck·테스트 11개·build는 각각 PASS/0이다. Backend bootJar는 요청 진입점에 없어 NOT RUN, Python은 관련 변경 없어 NOT RUN이다.

최초 sandbox 실행은 AuthorizationManager로 종료 코드 1이었다. 정책 변경 없이 같은 허용 명령을 sandbox 밖에서 실행했다. 개발 중 추가 테스트의 컴파일·spy/JSON 비교 오류는 수정 후 재검증했다. 최초 Frontend/test는 App.test.tsx:56의 radio 탐색으로 FAIL/1이었으나 Frontend 파일 수정 없는 최종 재실행은 PASS다. 원인은 확정하지 않았으며 타이밍 의존 가능성이 남는다. 과거 실패를 최종 결과로 덮어쓰지 않는다.

| 요구 범주 | 이번 실제 assertion |
| --- | --- |
| 정상 승인·입력 | 200 DTO·두 version·필수 참조·APPROVED/PUBLISHED, APPLIED/version+1/적용 ID, 생성자·생성 시각 보존, null과 0, missingFields·strict 정수·미지 필드·메모 길이·404/CORRECTION |
| 원자성 | Mention 삽입 뒤·초안 flush·감사·receipt 실패 각각 관계/초안 전체 행/감사/receipt 수 복원, 기존 식당·생성 이력 및 만료 receipt 삭제 롤백 |
| 감사·멱등 | actor/draft/mention/restaurant, 전후 스냅샷·receipt 결과 일치, 같은 키 순차/동시 재생, 다른 본문/초안/생성 경로 키 충돌, 다른 키/관리자/만료 후 APPLIED 충돌, 중단 후 재생이 복구하지 않음 |
| 중복·동시성 | 상태 4종·유형 차이에도 기존 Mention 전체 행 불변. 별도 관리자/트랜잭션으로 동일 초안 성공 1회, 두 초안 사전 조회 barrier로 실제 UNIQUE 충돌·롤백 밖 조회 확인 |
| 진입점 경합 | 초안 vs 직접/후보 각각 양방향 승리를 삽입 지점에서 제어. Mention 1개·식당 정보 보존, 패배 초안 전체 행/후보 disposition/감사/receipt 롤백. PATCH 양방향 stale/noneditable·version 1 |
| 장소·외부 호출 | 신규 장소는 저장 때 생성·승인 때 추가 없음. 저장 세션 소실 후 승인·추가 외부 호출 0. 초안 저장/직접/후보의 동일 Kakao ID 3자 생성 경합에서 식당 1개·정보 보존 |
| 공개·보안·DB | 상태 4종 중 APPROVED/PUBLISHED만 공개 식당/출처 포함, 내부 필드 비노출, 불법 공개 조합·잘못된 참조 FK 거부. 익명 401·USER/CSRF 403 빈 본문과 무쓰기·비활성 관리자 재생 거부 |
| 기존 회귀 | 직접·후보·#14·V1~V3 이관 보존 테스트 유지 및 이번 실행 PASS. 신규 migration 없음 |

한계: 실제 데드락·잠금 시간 초과/예상 밖 optimistic conflict의 강제 PG fixture, 장시간 부하·운영 데이터/배포·실제 외부 API·브라우저 E2E·모바일은 검증하지 않았다. 선택 입력의 DB 불법 음수/참조는 기존 제약을 해제하지 않는다. 저장 후 실제 시계 만료와 승인 연결은 별도 fixture가 없으며 세션 소실·외부 호출 0, 기존 토큰 만료 단위 테스트와 코드의 토큰 미참조가 근거다. Frontend 신규 계약 소비·멱등키/구조화 오류·UI는 #17 범위다.

git diff --check 및 신규 파일 공백·참조 검사 통과. 기존 사용자 지침·Planner 인계 자료 보존, staged 없음. 설치·다운로드·이미지 pull·Docker 시작·개발/운영 DB·서버·실제 외부 호출·배포·fetch·브랜치 변경·commit·Push·PR 생성은 수행하지 않았다.

## #15 설계 승인·Thread C 인계

- 2026-11-06 사용자가 Planner Handoff 6번의 추천안 전체를 승인했다. [구현 계획서](ISSUE_15_IMPLEMENTATION_HANDOFF.md)에 기준 커밋·승인 정책·책임·API·원자성·시나리오별 assertion·검증 계획을 기록했다.
- CREATE 초안 승인 성공/동일 유효 영수증 재생은 200, 다른 키·관리자·만료 후 APPLIED 재승인은 409다. 자동 재시도는 없고 직접 승인은 초안 없이 유지한다.
- 기존 V3 사용, 신규 migration 없음. #15는 Backend·테스트·관련 문서, Frontend 타입·래퍼·연결은 #17이다. 정정·공개 제어·새 UI·모바일은 제외한다.
- 이번 작업은 문서 갱신·계획서 작성만 수행한다. #15 코드 구현·테스트·빌드·DB·외부 API·Git 변경 작업은 실행하지 않는다. 설계 승인을 이번 스레드의 코드 실행 권한으로 확장하지 않는다.
- 아래 #14·#13의 브랜치·검증 수치·작업 트리 설명은 당시 기록이다. 현재 인계 기준과 제한은 위 계획서를 우선한다.

## #14 작업 트리 갱신

- 기준: feat/RegistrationService / d2c44a16f203c3468cceb3bd1b4bd1c02ed57bd4와 기존 문서·지침 변경 6개 파일을 보존한 작업 트리.
- Handoff 3절의 추천안 10개 전체를 사용자가 승인했다. CREATE 초안 생성·단건·식당별 페이지·부분 수정, V3 JPA 매핑, 감사·생성 멱등성·동시 수정 보호를 구현했다. 신규 migration은 없다.
- MentionDraftInput은 신규 API의 엄격 파싱과 누락/null 구분, MentionDraftService는 관리자 → 초안 잠금·상태·버전 검사·원자 저장을 담당한다. 상세 계약은 [ADMIN_API.md](ADMIN_API.md)의 #14 현재 계약을 따른다.
- 입력 단위 테스트와 MockMvc/Testcontainers 통합 테스트에 null·0·누락, 멱등 재생·경로 충돌, 권한·CSRF, 생성·수정 롤백, 동시 수정·동시 생성, 공개 제외 assertion을 추가했다. 기존 직접·후보 승인 테스트는 유지했다.
- 검증 명령 powershell.exe -NoProfile -File C:/workspace/sedanji/scripts/verify.ps1 -Backend -Frontend의 최초 샌드박스 실행은 AuthorizationManager에서 차단됐다. 정책 변경 없이 같은 명령을 샌드박스 밖에서 재실행한 결과 Frontend 타입 검사·테스트 11개·빌드 PASS(각 0), Backend는 Docker Engine 접근 실패로 NOT RUN, 전체 종료 코드 1이다. 이후 Docker 실행 후 동일 진입점의 -Backend 재검증에서 컴파일·테스트 137개가 모두 PASS(실패·skip 0, 전체 종료 코드 0)했다.
- 후속 #15: 별도 초안 승인, 불완전 승인 거부, 수정/승인 경합·반복 승인 원자성. #17: Frontend 타입·래퍼·UI와 멱등키·구조화 오류·초안 재개 연결. 기존 직접 승인은 유지한다.

아래 #13 점검은 당시 기록이다. 최신 초안 구현 상태는 이 #14 절을 우선하며 M1 전체 완료를 의미하지 않는다.

## 현재 진행 상태 — #13

기준 브랜치 `feat/RegistrationService`, HEAD `12ae955e92c33e98f24207b6e62ca35351e74b18`. 문서 작업 시작 시 staged/unstaged/untracked 변경 없음. 아래 현황은 코드와 테스트 assertion의 정적 확인이며 실제 DB·서버·CI 실행 결과는 미확인이다.

| 영역 | 현재 코드 | 남은 작업 |
| --- | --- | --- |
| 직접 등록 | `DirectRegistrationController`의 장소 검색·영상 메타데이터 등록·직접 승인, `PlaceSelectionService`의 세션 토큰 | 신규 UI, 기존 식당 ID 기반 관계 추가 |
| 승인 저장 | `RegistrationService.approveDirect`의 관계·감사·영수증 원자 저장, 중복·멱등 처리 | 초안 경유 승인 및 다른 생성 API로의 확장 |
| 초안·정정 | V3 초안 스키마는 있음 | 초안 Entity·Repository·서비스·API와 Revision 구현 |
| 조회·공개 | APPROVED AND PUBLISHED 공개 조건, 기존 관계·후보 조회 | 식당 중심 관리자 목록·상세·필터, 중단·복구·이력 조회 |
| Frontend | React·TypeScript·Vite, 기존 후보 기반 화면 | 직접 등록·초안·목록·상세·정정·공개 제어 UI. 메모 필수 조건 정합성 |
| 기반·품질 | 기존 이름·주소를 보존하는 upsert, 동명이인 생성, `.github/workflows/ci.yml` | 기능별 검증·E2E·실제 연결 확인. CI 파일 존재는 실행 성공의 증거가 아님 |

현재 API와 목표 계약의 상세 차이·D1~D6 결정 항목은 [ADMIN_API.md](ADMIN_API.md)의 현재 코드 계약을 따른다. 기존 `App.jsx`·메모 필수·덮어쓰기 설명은 아래 **과거 점검표**에만 해당한다.

### 후속 Issue 범위·선행 관계 — 제안

| Issue | 후속 범위 | 선행·결정 |
| --- | --- | --- |
| #14 | 최소 초안 저장·조회·수정·재개, 동시 수정 | #13 현황 기준, D1·D4·D5 설계 |
| #15 | 초안 승인·중복·동시성·감사·재시도 | #14, D1·D5. 기존 직접 승인 재구현이 아닌 초안 경로 연결 |
| #16 | 식당 목록·검색·필터·상세 API | #13, D6 출처 상태 설계 |
| #17 | 직접 등록·초안 UI, 헤더·구조화 오류 처리, 선택 메모 | #14·#15, D2·D3·D4·D5 |
| #18 | 목록·상세 UI·초안 재개·관계 추가 | #16·#17, D4·D6 |
| #19 | 정정안·재승인·이력 API/UI | #18, 정정 범위·공유 정보 영향·D5 |
| #20 | 관계 중단·복구 API/UI·감사 | #18·#19, 공개 불변식 유지 |
| #21 | 핵심 E2E·M1 완료 검증 | #17~#20, 실행 환경·부작용 별도 확인 |

이는 저장소 안의 계획이며 GitHub Issue 본문·Project Status·Milestone을 이번 작업에서 수정한 것은 아니다. D1~D6 미정은 후속 설계 전에 해결하며 계획 표 자체를 구현 승인으로 간주하지 않는다.

### 검증 근거와 공백

`DirectRegistrationIntegrationTest`는 0초·선택 메모·직접 승인 공개/내부 정보 제외, 기존 장소 보존, 상태별 중복 거부, 같은 키 재생/다른 요청 충돌, 저장 단계별 실패 롤백, 입력·참조·권한·CSRF·동시 승인 사례의 assertion을 포함한다. `PlaceSelectionServiceTest`는 관리자/세션 결속·만료·검색 묶음 퇴출·잘못된 외부 응답 후 기존 토큰 보존을 확인하도록 작성돼 있다. 이는 테스트 내용을 읽은 결과이며 이번 실행 PASS가 아니다.

Frontend의 기존 `App.test.tsx`는 후보 승인 입력과 공개 목록·0초 링크 등을 검증하지만 신규 직접 등록 소비 흐름은 없다. `api.ts`의 Idempotency-Key 전달과 오류 code/existingMentionId 보존도 후속 검증 대상이다. 미구현 초안·정정·복구·새 목록 계약은 기존 테스트로 충족했다고 볼 수 없다.

이번 Backend·Frontend 타입 검사/테스트/빌드·Python은 모두 NOT RUN(사용자 실행 금지). 실제 DB 적용 버전·외부 API·배포·실기기도 미확인이다. 문서 링크·사실·범위와 diff 정적 검토만 수행한다.

기준은 [최신 요구사항](REQUIREMENTS.md), [등록 시안](design/admin-restaurant-registration.png), [상세 시안](design/admin-restaurant-detail.png), 루트 AGENTS.md다. 기존 도메인 문서와 구현 기록은 과거 상태를 포함하므로 최신 사용자 합의와 구분한다.

## 이번 단계의 목표

관리자가 설명란 추출 후보 없이도 카카오 장소를 선택하고 셰프·출처 영상과 연결하여 임시 저장·승인할 수 있게 한다. 승인한 등록·상세 시안을 기준으로 식당 목록과 관계별 정정·공개 중단·복구까지 순차적으로 구현한다.

관리자 업무 메뉴는 제보 관리·식당 관리 두 개만 유지한다. 사용자 제보 접수·처리 기능은 전체 개발 순서의 4단계이므로 이번 단계에서 가짜 제보 데이터나 동작하는 것처럼 보이는 화면을 만들지 않는다. 메뉴의 실제 활성화는 기능 구현과 함께 진행한다. 모바일 앱·일반 사용자 인증·저장·제보는 이번 구현 범위 밖이다.

## 최초 코드 점검의 차이 — 2026-11-02 과거 기록

아래 표는 최초 점검 당시 기록이다. 현재 구현 상태는 위 최신 표를 우선하며 과거 설명을 신규 구현 요구로 중복 적용하지 않는다.

| 항목 | 현재 구현과 근거 파일 | 필요한 변경 |
| --- | --- | --- |
| 시작 흐름 | `frontend/src/App.jsx`: 영상 가져오기 → 설명란 후보 → 장소 검색·승인 | 장소 선택 → 관계 입력의 2단계 직접 등록 |
| 장소 검색 | `ApiController`, `CandidateMatchingService`: 추출 후보 ID에 종속 | 추출 후보 없이 관리자 장소 검색·선택 가능. 서버가 조회한 장소를 사용하고 브라우저의 임의 좌표를 신뢰하지 않음 |
| 장소 재사용 | `RestaurantRepository`: Kakao Place ID 유일성·upsert 존재 | 재사용 가능. 현재 upsert는 기존 이름을 덮어쓰므로 단순 재사용 과정에서 정정 절차를 우회하지 않게 변경 |
| 영상 | `ExternalApis.video`: 공식 snippet 조회. `CatalogService.importVideo`: videoId 재사용 | 어댑터·URL 파싱 재사용. 메타데이터 등록과 설명란 후보 추출을 분리해 직접 등록이 추출 작업에 종속되지 않게 함 |
| 셰프 | `ChefEntity`, V1: 이름 UNIQUE. `createChef`: 이름이 같으면 기존 항목 반환 | 동명이인 허용, 기존 검색·명시적 선택과 신규 생성을 구분 |
| 관계 생성 | `MentionEntity`: 후보에서 영상 참조, 생성 즉시 APPROVED | 직접 등록과 초안을 지원하고 승인 전 비공개 보장 |
| 최소 임시 저장 | V2: Mention의 chef·video·restaurant NOT NULL, 초안 저장 API 없음 | 식당만 선택된 작성 자료를 보존할 별도 초안 모델 설계 필요 |
| 검증 메모 | DTO·서비스·엔티티·V2 CHECK·화면 모두 메모 필수 | 검증 메모는 선택. 출처·실제 근거 확인과 승인자·시각 기록은 유지 |
| 중복 관계 | V2 및 Repository: 셰프·식당·영상·유형 조합으로 중복 판단 | 유형이 달라도 같은 셰프·식당·영상이면 새 관계 생성 금지 |
| 정정·복구 | 관련 API·저장 모델 없음 | 기존 승인본을 유지하는 수정안, 변경 이력, 관계 단위 공개 중단·재승인 후 복구 |
| 공개 조건 | `findPublished` 등: verification_status=APPROVED만 확인 | 공개 중단 여부도 공통 반영. 승인·검증 상태와 공개 상태를 구분 |
| 관리자 목록·상세 | 전체 후보·관계 조회 중심. 관리자 전용 식당 목록·상세 API 없음 | 확정한 목록 검색·필터와 식당 단위 관계 상세 제공 |
| 프론트엔드 | `package.json`, `App.jsx`: JavaScript, 사용자 웹 지도·관리자 화면 혼재 | 관리자 전용 React·TypeScript 구조로 전환, Router·Query·Tailwind/shadcn은 지침에 따라 필요한 범위에 적용 |
| 인증·기본 테스트 | `SecurityConfig`: DB 관리자·BCrypt·ADMIN·CSRF. PostgreSQL Testcontainers와 RTL/Vitest 테스트 존재 | 기반 재사용. 신규 기능 권한·트랜잭션·공개 조건을 추가 검증 |

확인 범위는 저장소 파일의 정적 검토다. 현재 개발 DB의 데이터 건수·마이그레이션 상태나 실제 API 가용성은 이번 점검으로 확인했다고 주장하지 않는다. 과거 테스트 통과 기록도 신규 기능의 검증 결과로 취급하지 않는다.

## 구현 순서와 완료 조건

### A. 저장 모델·API 상세 설계

2026-11-02 [도메인 모델](DOMAIN_MODEL.md)에 ERD·제약·상태 전이를 갱신하고, [API 계약](ADMIN_API.md)과 [마이그레이션 계획](ADMIN_MIGRATION_PLAN.md)을 작성했다. 이는 구현할 기술 설계이며 실제 DB 적용 완료를 뜻하지 않는다. 기존 설계는 archive에 보존한다.

- Restaurant은 검증된 Kakao 장소를 식별한다. 공개 여부는 공개 가능한 관계 유무로 결정한다.
- 식당만 선택된 작성 자료는 MentionDraft로 보관한다. 실제 관계를 나타내는 Mention에 가짜 Chef·Video를 넣지 않는다.
- 공개본과 정정 중인 수정안을 별도로 저장하여 미승인 변경이 공개 조회에 섞이지 않게 한다.
- 승인·정정·공개 중단·복구의 처리자·시각·전후 내용·해당 사유를 감사 이력으로 보존한다. 일반 검증 메모는 선택이며 정정·복구 사유와 구분한다.
- 독립된 검증 상태와 공개 상태를 정의하고 관계의 공개 조건을 조회 전반에 일관되게 적용한다.
- 직접 등록용 장소 선택 검증 자료는 추출 후보 없이 사용할 수 있게 한다. 검색 자체로 식당·관계를 생성하지 않고, 장소 미선택 중단 시 업무 자료를 남기지 않는다.
- 기존 evidence 필드와 선택 검증 메모를 어떻게 대응할지 정의한다. 승인 시 숨겨진 필수 자유 입력란을 추가하지 않고 원본 설명란 근거·기존 이력을 보존한다.

API는 관리자 장소 검색·선택, 영상 메타데이터 등록·재사용, 셰프 검색·신규 생성, 식당 목록·상세, 초안 저장·조회·승인, 정정안 저장·재승인, 공개 중단·복구·이력 조회의 책임으로 구분한다. 확정된 2개 메뉴 안에서 사용하며 API 책임 수를 화면 수로 늘리지 않는다.

완료 조건: 새 모델과 기존 PoC 모델의 경계, 승인·공개 조건, 중복·동시 처리, 오류 응답, 마이그레이션 대상과 보존 방침이 문서로 명확하다.

### B. 직접 등록·임시 저장·승인 백엔드

2026-11-02 V3 스키마·기존 승인/공개 조회 호환 변경과 테스트 42개, 로컬 DB 적용을 완료했다는 과거 기록은 [적용 결과](DIRECT_REGISTRATION_MIGRATION.md)에 보존한다. 현재는 직접 검색·메타데이터 등록·직접 승인 API가 추가됐지만 초안 API·신규 UI는 없으며 B 전체 완료를 의미하지 않는다. 아래 목록은 단계 전체 범위이며 이미 구현한 기능을 다시 만드는 지시가 아니다.

1. 후속 Flyway로 동명이인과 새 관계 중복 기준, 선택 메모, 직접 등록 초안의 저장 제약을 반영한다. 적용된 V1·V2는 수정하지 않는다.
2. 후보에 종속되지 않는 Kakao 장소 검색과 서버 측 선택 검증을 구현한다.
3. 공식 YouTube 메타데이터 조회·기존 영상 재사용을 추출 작업과 분리한다.
4. 셰프 검색·선택·동명이인 신규 입력을 구현한다.
5. 장소 재사용, 초안 저장·재개와 필수 정보 확인 후 승인을 구현한다. 승인에는 식당·셰프·출처 영상·유형 확인이 필요하고 시점·메모는 선택이다.
6. 관리자 식당 목록·상세와 관계 목록 조회를 제공한다.

기존 데이터에서 새 3항목 중복 기준에 충돌하는 관계가 발견되면 자동 삭제·병합하지 않는다. 실제 적용 전 충돌과 영향 범위를 확인하여 해결 방침을 제시한다. 이전 후보·근거·승인 자료를 보존한다.

완료 조건: 식당만으로 초안 저장 가능, 미완성 승인 불가, 선택 메모로 승인 가능, 동일 장소·영상 재사용, 동명이인 허용, 같은 관계 중복·동시 승인 방지, 관리자 외 접근 차단을 통합 테스트로 확인한다.

### C. 승인한 관리자 등록·상세 화면

관리자 전용 앱 구조와 인증 화면을 구성하고 식당 목록 → 등록·상세를 연결한다. 기존 사용자 웹 지도를 모바일 앱 완성물로 전환했다고 표현하지 않는다.

- 등록 화면은 승인한 2단계 시안을 적용한다. 메모·시점은 선택이고 셰프·영상·유형 미입력 시 임시 저장만 가능하다.
- 검색 결과 없음·지점 선택 불가는 직접 등록을 중단한다. API 오류는 별도 안내한다.
- 목록은 식당명·주소·셰프 검색과 공개 중·공개 관계 없음·출처 재확인 필요 필터, 최근 등록순을 적용한다.
- 상세는 장소 정보, 관계별 셰프·영상·유형·시점·상태, 관계 추가와 영상 확인을 표시한다.
- 미저장 이탈 확인과 명시적 임시 저장을 구현한다. 브라우저 종료 경고의 기술적 한계는 기존 명세를 따른다.
- 정정·공개 중단·복구 버튼은 해당 기능 API가 완료된 뒤 활성화한다. 시안에 있다는 이유로 미구현 동작을 성공 처리하지 않는다.

완료 조건: 승인한 등록·상세 구성으로 직접 등록 → 초안 재개 → 승인 → 공개 조회를 수행하고 입력·로딩·오류·중복·이탈을 UI 테스트로 확인한다.

### D. 정정·공개 중단·복구와 이력

수정안 임시 저장과 재승인, 관계 단위 공개 중단, 근거 재확인·사유 기록 후 복구를 구현하고 상세의 작업과 이력 펼치기에 연결한다. 공개본은 수정안 승인 전까지 유지한다. 중단된 관계와 초안·내부 메모는 공개 응답에서 제외한다.

공개 가능한 관계가 남은 식당은 유지하며 하나도 남지 않으면 공개 탐색에서 제외한다. 원본 영상 접근 불가만으로 관계를 자동 중단하지 않고 관리자 확인 필요로 분리한다. 확인 방법·시점은 상세 설계에서 정하며 임의의 주기나 별도 배치 시스템을 추가하지 않는다.

완료 조건: 정정 초안 비노출, 재승인 전후의 공개본 변경, 관계별 중단과 다른 관계 유지, 재승인 없는 자동 복구 금지, 사유·이력 보존을 검증한다. 저장 목록 숨김·복원은 향후 저장 기능 단계에서 이 공개 조건에 연결한다.

### E. 회귀·실제 연결 확인과 기록

외부 API는 모의 응답으로 인증·할당량·네트워크·응답 오류와 빈 결과를 재현한다. PostgreSQL Testcontainers로 마이그레이션·제약·권한·상태 전이를 검증하고 프론트엔드는 RTL/Vitest로 주요 동작을 검증한다. 핵심 흐름 완료 후 Playwright로 등록·승인·정정·공개 중단·복구를 확인한다.

환경이 준비된 경우 실제 API 조회와 브라우저 확인을 모의 테스트와 구분해 기록한다. 실제 API 호출만으로 관계를 승인하지 않으며 테스트용 관계를 개발 DB의 실제 승인 자료로 임의 등록하지 않는다. 로그·응답·결과물에 키나 인증 헤더를 남기지 않는다.

완료 조건: 구현 변경·검증 결과·남은 한계를 문서화하고 모바일 단계가 사용할 공개 조건을 확인한다.

## 변경·커밋 단위

작업은 모델·API 설계, 직접 등록 백엔드, 초안·승인, 관리자 기반·등록 UI, 목록·상세 UI, 정정·공개 제어·이력처럼 검토 가능한 기능 단위로 나눈다. 각 기능과 필요한 테스트는 함께 관리한다. 실제 경계는 구현 시 변경 범위에 맞춘다.

커밋·푸시는 별도 사용자 승인 없이 하지 않는다. 승인받은 커밋은 영어 접두어와 한국어 설명을 사용한다. 기존 Python PoC를 불필요하게 변경하지 않는다.

## 최초 정적 점검의 결과

- 최초 정적 점검 당시 실행 코드·DB·환경설정 변경 없음. 이후 V3 구현·적용은 B의 진행 기록과 적용 결과 문서를 따른다.
- 파일을 읽고 차이를 비교한 정적 점검이며 새 테스트나 실제 API 호출은 수행하지 않았다.
- 최초 점검 당시 A의 ERD·상태·API·이관 설계 문서 갱신을 완료하고 B를 후속 작업으로 기록했다. 현재 남은 범위는 문서 상단의 최신 표와 Issue 계획을 따른다.
