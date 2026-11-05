# #15 관리자 등록 초안 승인 Implementation Handoff v2

작성일: 2026-11-06. 대상: Thread C Implementer. 이 문서는 이전 대화 없이 구현 범위와 승인 결정을 이해하기 위한 인계서다.

## 상태·사용자 승인·실행 권한

- 요구사항 및 아래 6개 정책 결정: **APPROVED**. 승인 근거는 사용자의 2026-11-06 지시 “6번의 미확인 결정에 대해서 승인할게. 그리고 초안 문서와 다른 부분이 있다면 갱신해줘. 그리고 Thread C에 전달할 구현 계획서 작성해줘.”다.
- Handoff의 실행 상태: **PROPOSED / 구현 착수 전**. 위 승인은 설계 결정과 문서 갱신·계획서 작성 권한이다. 이번 Planner 스레드에서는 코드·테스트 구현 및 검증 실행을 하지 않는다.
- Thread C는 사용자가 전달한 구현 지시·실행 권한과 본 문서를 대조하고 `.agents/skills/sedanji-implementer/SKILL.md`를 실제로 읽는다. 문서의 APPROVED만을 코드·테스트 실행·Git 작업 권한의 증명으로 삼지 않는다.
- 원래 금지한 설치·Docker 시작·개발/운영 DB·실제 외부 API·배포·fetch·브랜치 변경·병합·커밋·Push·PR 생성은 이번 승인으로 허용되지 않는다. 검증 실행도 별도 명시된 Thread C 권한을 따른다.
- 필수 비즈니스 미결정은 없다. 구현 중 새 계약 결정이나 불변식 충돌이 드러나면 해당 부분만 보류·보고하며, 승인된 결정을 반복 확인하지 않는다.

| 승인된 결정 | 확정 정책 |
| --- | --- |
| 완료 초안 재승인 | 동일 관리자·키·동일 요청의 유효 영수증만 200 재생. 다른 키·관리자·만료 후 재승인은 409 DRAFT_ALREADY_APPLIED |
| 성공 계약 | 200 승인 결과 DTO, draftVersion과 mentionVersion 분리 |
| 오류 계약 | 아래 code/message·ID·version·missingFields, 승인 서비스 비활성 관리자 403 ADMIN_INACTIVE |
| 재시도 | 자동 업무 재시도 없음. 경합은 롤백 후 재조회 안내 |
| Frontend 범위 | #15는 영향 정리만. 타입·래퍼·UI 연결은 #17 |
| DB | 기존 V3 사용, 신규 migration·백필 없음 |

## 저장소 기준과 기존 변경 경계

- 루트: `C:\workspace\sedanji`.
- 분석 및 문서 작업 시작 브랜치: `feat/RegistrationService`.
- 전체 HEAD: `ffd5efc74830b58f5d54a7a005fba3bc76abc69a`.
- 시작 시 staged 없음, unstaged `AGENTS.md`·`backend/AGENTS.md`, untracked 없음. 두 지침 변경은 기존 사용자 변경이며 보존한다.
- 이번 문서 작업: `docs/ADMIN_API.md`, `docs/DOMAIN_MODEL.md`, `docs/ADMIN_IMPLEMENTATION_PLAN.md` 갱신 및 이 문서 신규 생성. 아직 커밋되지 않은 인계 자료다.
- #14는 [PR #23](https://github.com/Huk9uri/sedanji/pull/23)으로 main의 `e9d431926d19b18ad481b4657451cc15ce6688f5`에 병합됐다. 이전 Planner 조회에서 현재 HEAD는 PR head이며 main과 파일 차이 없음, main은 3 commits ahead였다. 이번 문서 작성에서 원격을 다시 조회하거나 fetch하지 않았다.
- [Issue #15](https://github.com/Huk9uri/sedanji/issues/15)는 이전 분석 시 open·댓글 0개였다. Thread C는 최신 본문·댓글과 저장소를 다시 확인한다.
- Thread C 시작 시 HEAD와 staged/unstaged/untracked, 관련 diff를 대조한다. 기준 차이만으로 중단하지 않고 영향 분석한다. 기존 변경을 덮어쓰거나 stash/reset하지 않는다.

## 적용 지침·현재 구현 근거

루트 및 Backend·Frontend AGENTS.md와 다음 Skill 본문을 읽는다. 역할 Skill 외 API·DB·검증 전문 Skill은 권한을 확대하지 않는다.

- `.agents/skills/sedanji-implementer/SKILL.md`: 승인 범위와 실제 코드 대조, 최소 구현.
- `.agents/skills/sedanji-api-change/SKILL.md`: 양쪽 계약·기존 소비자·보안·공개 조건.
- `.agents/skills/sedanji-db-migration/SKILL.md`: 기존 제약·저장 모델·데이터 보존. migration을 임의 추가하지 않음.
- `.agents/skills/sedanji-verification/SKILL.md`: 검증 선택·기존 verify.ps1·실제 종료 코드·NOT RUN.

아래 Java 경로는 `backend/src/main/java/kr/sedanji/` 기준이다. 링크 문서의 현재 계약과 목표 절을 구분한다.

| 근거 | 현재 책임 및 보존 사항 |
| --- | --- |
| `mentiondraft/MentionDraftController`, `MentionDraftInput`, `MentionDraftService` | CREATE 생성·상세·식당별 페이지·PATCH. 엄격 파싱, 장소는 기존 ID 또는 토큰, 저장/PATCH에서 식당 확정. 승인 endpoint 없음 |
| `mentiondraft/MentionDraftEntity`, `MentionDraftRepository.findForUpdate` | appliedMentionId·status·@Version 및 초안 WRITE 잠금 존재. apply 전이는 미구현 |
| `MentionDraftService.patch` | 관리자 → 초안 잠금, CREATE/DRAFT·expectedVersion 검사, 동일 값도 version 증가. 성공 PATCH는 영수증 없음 |
| `registration/RegistrationService.approveDirect` | 활성 관리자 잠금, 세션 장소 검증, 식당·관계·감사·24시간 영수증 원자 저장. 초안 없음 |
| `registration/DirectRegistrationFacade.approve` | NEVER. mentions_relationship_key 실패 뒤 서비스 롤백 완료 후 기존 관계 조회 |
| `RegistrationService.approveFromCandidate` | 후보 잠금·확보 장소·필수 evidence·관계 생성·RESOLVED 원자성. 직접 승인식 감사/영수증은 현재 없음 |
| `mention/MentionService.approveDirect`, `MentionEntity.direct` | MANDATORY, 필수 참조·유형·활성 관리자·시점·메모 검증, APPROVED/PUBLISHED 생성 |
| `mention/MentionRepository` | 상태 무관 관계 중복 검사. 공개 출처는 APPROVED AND PUBLISHED |
| `restaurant/RestaurantRepository.upsert`, `RestaurantService.getOrCreate` | Kakao ID UNIQUE·ON CONFLICT DO NOTHING, 기존 이름·주소·좌표 보존 |
| `PlaceSelectionService.resolve` | 관리자·세션 결속, 15분/최근 5개 검색 묶음. resolve는 외부 API를 호출하지 않음 |
| `SecurityConfig`, `admin/AdminUserRepository.findForUpdate` | DB 관리자 username과 Principal 연결, 세션 ADMIN·CSRF, enabled 재확인·행 잠금 |
| `registration/AdminAuditEventEntity`, `AdminRequestReceiptEntity` | 감사 대상 FK·전후 JSON, append-only. 관리자별 키 UNIQUE, 범용 path/status 생성자, 24시간 영수증 |
| `backend/src/main/resources/db/migration/V3__prepare_direct_registration.sql` | 3항목 관계 UNIQUE, 초안 APPLIED 필수값 CHECK·applied_mention_id FK, 감사·receipt 스키마 |
| `frontend/src/api.ts`, `types.ts`, `App.tsx`, `features/admin/CandidateReviewCard.tsx` | 현재 후보 경로 소비. 헤더 사용자 지정·구조화 오류·초안 타입 없음. 기존 메모 필수 UI를 서버 규칙으로 확장하지 않음 |
| `scripts/smoke-api.py`, `scripts/check-backend-state.py` | 기존 조회 소비, 초안 승인 호출 없음. 검증 대신 실행하지 않음 |

제품 근거는 [REQUIREMENTS](REQUIREMENTS.md)의 ADM-07·10·11·13 및 [DOMAIN_MODEL](DOMAIN_MODEL.md), 계약은 [ADMIN_API](ADMIN_API.md)의 현재/#15 승인 계약이다. Revision·영상 접근 상태·정정·복구 목표를 이번 기능으로 확대하지 않는다.

## 목표·범위·불변식

저장된 CREATE/DRAFT를 검토한 관리자가 명시적으로 승인하여 Mention 한 개와 적용 연결을 만든다.

- 식당·셰프·영상·유형 필수. 시점·검증 메모 선택. null은 미확인, 0은 확인된 0초.
- 같은 chef/restaurant/video는 유형·검증·공개 상태와 무관하게 중복이다. 기존 관계 전체 값을 수정·자동 승인·복구하지 않는다.
- 관계·초안 전이·승인 감사·성공 영수증은 원자적이다.
- 공개는 APPROVED AND PUBLISHED. 내부 메모·관리자·초안·미검증 자료·영수증을 공개하지 않는다.
- 장소 매칭·후보 처리·관계 검증·공개 상태를 구분한다. 관계 유형을 근거보다 강화하지 않는다.
- 직접 승인·후보 승인·#14 저장/PATCH의 계약·감사·멱등 처리와 잠금을 유지한다.

구현 범위는 Backend·테스트·관련 계약 문서다. 초안 저장/조회/PATCH의 무관한 확장, CORRECTION 승인·정정·공개 제어, 새 UI·모바일, 의존성·기술 변경은 제외한다.

## 메서드별 변경 계획

신규 파일명은 책임을 나타내는 제안이며 동등한 작은 구조는 허용한다. 기존 Controller advice 범위를 확대해 #14 오류를 바꾸지 않는다.

| 파일·메서드 | 구현 책임 |
| --- | --- |
| 신규 `mentiondraft/MentionDraftApprovalController.approve` | POST 경로, strict JSON·헤더, Principal username 전달, 200 JSON. 세션 장소 토큰 입력 없음 |
| 신규 승인 request/result/exception/advice | expectedVersion만 파싱, 아래 DTO·오류·안전한 예외 변환. 기존 API Jackson coercion·오류를 일괄 변경하지 않음 |
| 신규 `MentionDraftApprovalFacade.approve` | NEVER, id/body/key 검증·해시, per-attempt 식별자 준비, 서비스 호출, 롤백 후 명명된 UNIQUE 충돌 조회·변환 |
| 기존 `registration/RegistrationService.approveDraft` | 승인 트랜잭션 소유, 아래 순서의 검증·잠금·관계 생성·초안 전이·감사·영수증 |
| 기존 `MentionDraftEntity` 승인용 접근·`apply` | 저장 필수값 조회, CREATE/DRAFT 방어, appliedMentionId·APPLIED·updatedBy/At 변경. createdBy/At 보존, @Version 직접 증가 금지 |
| 기존 `MentionDraftRepository.findForUpdate` | 재사용 |
| 기존 `MentionRepository` 관계 ID 조회 | chefId·restaurantId·videoId 기준 상태 무관 조회 추가. 사전 검사 및 롤백 후 조회 |
| 기존 `MentionService.approveDirect` | 기존 MANDATORY·MentionEntity.direct 재사용. 불필요한 서비스 재구성 없음 |
| 기존 `AdminAuditEventEntity.approveDraft` factory | APPROVE_DRAFT, actor/draft/mention/restaurant FK, 전후 스냅샷 |
| 기존 receipt Entity/Repository | generic path/status 생성자·matches 재사용. 직접 승인/초안 생성의 기존 영수증·해시 호환 보존 |
| 신규 `MentionDraftApprovalIntegrationTest` 및 입력 테스트 | 아래 PostgreSQL·MockMvc·엄격 입력 assertion |
| 기존 승인·초안 테스트 | 필요한 회귀/assertion 보강. 기존 테스트 완화 금지 |

## API 확정 계약

```http
POST /api/admin/mention-drafts/{id}/approve
Content-Type: application/json
Idempotency-Key: <비공백, 최대 128자>
<기존 CSRF 헤더>

{"expectedVersion":3}
```

id는 양의 정수. expectedVersion은 필수·null 불가·0 이상 long 정수. 숫자 문자열·소수·범위 초과·미지 필드·서버 관리 필드는 400. 저장된 내용만 승인하며 미저장 변경은 먼저 PATCH한다. CREATE만 지원하고 CORRECTION은 404다.

```json
{
  "draftId": 12,
  "draftVersion": 4,
  "draftStatus": "APPLIED",
  "mentionId": 34,
  "restaurantId": 56,
  "mentionVersion": 0,
  "verificationStatus": "APPROVED",
  "publicationStatus": "PUBLISHED"
}
```

성공·일치 영수증 재생은 200. 응답은 승인 당시 스냅샷이며 이후 관계의 최신 상태를 보장하지 않는다. 재생이 관계를 복구하지 않는다. 최신 초안은 기존 GET을 사용한다.

| HTTP | code | 추가 필드 |
| --- | --- | --- |
| 400 | INVALID_INPUT | 없음 |
| 400 | DRAFT_INCOMPLETE | missingFields: 저장된 필수 필드 중 누락된 이름 배열 |
| 404 | RESOURCE_NOT_FOUND | 없음 |
| 409 | STALE_VERSION | currentVersion |
| 409 | DRAFT_ALREADY_APPLIED | currentVersion, appliedMentionId |
| 409 | DUPLICATE_RELATION | existingMentionId |
| 409 | IDEMPOTENCY_CONFLICT | 없음 |
| 409 | CONCURRENT_CONFLICT | 없음 |
| 403 | ADMIN_INACTIVE | 없음 |
| 500 | INTERNAL_ERROR | 없음 |

오류는 code/message JSON이며 내부 예외·SQL·토큰·비밀값을 포함하지 않는다. Security 익명 401·권한/CSRF 403은 기존 빈 본문을 유지한다. 서비스 활성 관리자 검증 실패만 ADMIN_INACTIVE다. 작성자와 승인자를 분리하지 않으며 활성 관리자끼리 공유·본인 승인 가능하다.

## 트랜잭션·잠금·검증 순서

1. facade NEVER 경계에서 요청 형식·키를 검사한다. 해시는 POST + id 포함 전체 경로 + 정규화 expectedVersion으로 만든다.
2. 서비스 트랜잭션에서 Principal username으로 관리자 WRITE 잠금, enabled 확인. 재생도 이 검사를 통과해야 한다.
3. 관리자별 키 영수증 조회. 유효하고 path/hash/status=200이 같으면 결과 재생, 다르면 IDEMPOTENCY_CONFLICT. 만료 영수증 삭제·대체도 같은 트랜잭션이다.
4. 초안 WRITE 잠금, 존재·CREATE 확인. APPLIED면 version 검사보다 먼저 DRAFT_ALREADY_APPLIED. DRAFT면 expectedVersion 비교 후 STALE_VERSION.
5. 저장된 식당·셰프·영상·유형 필수값과 실제 Entity 참조를 확인한다. 시점 0..int max/null, 메모 최대 2000자/null도 서비스에서 방어한다. 후보 evidence를 요구하지 않는다.
6. restaurantId로 기존 식당 사용, 상태 무관 3항목 중복 조회. 있으면 DUPLICATE_RELATION. 유형·기존 상태는 중복 예외 조건이 아니다.
7. MentionService.approveDirect를 호출한다. candidate/evidence null, timestamp·review_note는 초안 값, reviewer는 현재 관리자, APPROVED/PUBLISHED 생성.
8. 초안 apply 및 flush. version은 JPA가 한 번 증가하며 최종 draftVersion을 응답·감사에 사용한다.
9. APPROVE_DRAFT 감사에 actor/draft/mention/restaurant와 승인 전 초안·승인 후 초안/관계 스냅샷 저장. 인증·장소 토큰은 제외한다.
10. 결과 200 영수증 저장 후 함께 commit. 중간 및 commit 실패는 전체 rollback한다.

관리자 → 초안 잠금 순서는 PATCH와 같다. 같은 관리자만의 병렬 테스트는 관리자 잠금에 가려지므로 서로 다른 활성 관리자도 사용한다. @Version/expectedVersion은 최신 내용 확인, WRITE 잠금은 동일 초안 전이 직렬화, 관계 UNIQUE는 서로 다른 초안·진입점 경합의 최종 보장이다.

Chef·Restaurant·Video에는 현재 비활성 판정 필드가 없다. 새 상태·외부 재확인을 도입하지 않는다. 초안 FK가 참조 삭제를 제한하며 필수 참조를 실제 조회한다. 이름·제목 변경을 유형·관계 근거로 추론하지 않는다. 영상 길이·현재 재생 가능 여부를 검증했다고 표현하지 않는다.

## 중복·재전송·경합 결과

| 상황 | 응답·최종 상태 |
| --- | --- |
| 같은 관리자·키·동일 요청, 유효 영수증 | 동일 200, 관계·전이·감사·receipt 추가 없음 |
| 같은 관리자·키, 다른 초안/version/경로 | 409 IDEMPOTENCY_CONFLICT, 업무 변경 없음. 직접/초안 생성과 키 공간 공유 |
| 같은 초안, 다른 키/관리자 또는 만료 영수증 | 409 DRAFT_ALREADY_APPLIED, 적용 연결·version·기존 관계 불변 |
| DRAFT 오래된 version | 409 STALE_VERSION, 변경 없음 |
| 같은 초안 동시 승인, 다른 키/관리자 | 승자 200, 후행 APPLIED 409. 관계·전이·승인 감사 1회 |
| 다른 초안, 같은 3항목 | 승자만 APPLIED, 패자 DUPLICATE_RELATION·DRAFT 유지, Mention 1개 |
| 초안 vs 직접 승인 | 승자만 생성, 패자 해당 경로 중복 409. 직접 승인 승리 때만 해당 감사·receipt 생성 |
| 초안 vs 후보 승인 | 승자만 생성. 후보 패배면 기존 disposition 유지, 초안 패배면 DRAFT 유지. 후보 오류는 기존 계약 유지 |
| PATCH 선행 | 승인 STALE_VERSION, 수정한 내용의 조용한 승인 없음 |
| 승인 선행 | PATCH DRAFT_NOT_EDITABLE, 갱신 유실 없음 |
| 기존 관계가 PENDING/REJECTED/APPROVED-SUSPENDED/APPROVED-PUBLISHED | 모두 DUPLICATE_RELATION, 유형·시점·메모·evidence·상태·승인자/시각·version·후보 연결 불변 |
| 같은 Kakao ID 생성 경합 | 저장/직접/후보의 기존 atomic upsert로 식당 1개·기존 정보 보존. 초안 승인 자체는 생성하지 않음 |

## 롤백·예외 변환·식당 경계

- Mention·APPLIED·version/updatedBy/At·승인 감사·성공 영수증·만료 영수증 삭제/대체는 하나의 성공/실패 경계다. 실패 성공 영수증은 없다.
- UNIQUE 위반으로 실패한 트랜잭션 안에서 조회하거나 계속 저장하지 않는다. 서비스 프록시의 rollback 완료 후 NEVER facade에서 정확한 mentions_relationship_key만 분류하고 별도 읽기로 기존 ID를 조회한다.
- attempt에는 검증된 chefId/restaurantId/videoId만 보관한다. rollback된 Entity/lazy 객체를 재사용하지 않는다. 다른 FK/CHECK/감사/receipt 오류를 중복으로 변환하지 않는다.
- 잠금 실패·데드락·예상 밖 optimistic conflict는 rollback 후 CONCURRENT_CONFLICT, 내부 저장 오류는 INTERNAL_ERROR. 자동 재시도 없음. 정상 version 불일치는 STALE_VERSION이다.
- 저장된 초안의 식당은 이전 생성/PATCH 트랜잭션에서 이미 commit됐다. 승인 실패 시 기존 식당·초안 생성 감사·생성 receipt는 보존하며 삭제/정리하지 않는다.
- 직접/후보 승인에서 그 트랜잭션이 신규 생성한 식당은 실패 시 기존 규칙대로 rollback한다.
- 승인 중 토큰 재검증·외부 API 호출 없음. 저장 후 토큰 만료·검색 세션 소실에도 승인 가능. 장소 변경 PATCH의 기존 만료 규칙은 유지한다.

## DB·ERD·배포 및 Frontend

ERD 변화는 기존 Draft.applied_mention_id → Mention을 채우는 것이다. Draft와 Mention의 Restaurant/Chef/Video는 일치하며 Audit은 Admin/Draft/Mention/Restaurant을 연결하고 Receipt는 Admin에 연결한다.

V3에 필요한 컬럼·APPLIED CHECK·FK·관계 UNIQUE·감사 append-only·범용 receipt가 있다. JPA 전이·조회만 추가하며 신규 DDL·migration·백필·데이터 정리는 없다. 기존 Flyway 파일·ddl-auto=validate·open-in-view=false 유지. 실제 DB의 적용 버전·checksum·정합성은 미확인이다.

기존 스키마와 구/신 앱은 호환되며 endpoint 배포 후 #17 소비자를 연결한다. 구 앱은 APPLIED 조회/수정 거부를 이미 지원한다. 신 클라이언트가 구 서버에서 실패해도 직접 승인 fallback·가짜 성공을 만들지 않는다.

#17 전달 사항: 초안/승인 DTO와 구조화 오류 타입, Idempotency-Key 헤더 지원, 기존 CSRF·폼·빈 응답 보존. 요청 중 중복 클릭 차단, 결과 불명확 시 같은 키/version 재전송, 내용 변경한 새 시도는 새 키. stale은 재조회·재검토, APPLIED는 초안 재조회, 중복은 기존 ID 안내. 성공 후 상세·DRAFT/APPLIED 목록·관계·공개 목록 갱신. 자동 최신 version 승인·정정·복구 금지. 이번 #15에서 Frontend 파일은 변경하지 않는다.

## 기존 테스트와 공백

테스트 경로는 `backend/src/test/java/kr/sedanji/` 기준이다. 기존 통과 이력은 이번 기능의 증거가 아니다.

| 기존 테스트 | 실제 근거·재사용 및 보강 |
| --- | --- |
| MentionDraftIntegrationTest | 식당만 저장·null·감사·비공개·재사용·PATCH version/경합/롤백. APPLIED는 SQL fixture이므로 승인 경로 assertion은 신규 필요 |
| MentionDraftInputTest | coercion 거부·누락/null/0·메모 정규화. 승인 expectedVersion 전용 입력 테스트 추가 |
| DirectRegistrationIntegrationTest | 참조·상태·null/0·감사·receipt·롤백·동시성. 상태별 중복은 전체 관계 행 불변 비교를 신규 초안 테스트에 추가 |
| RegistrationTransactionTest | 호출자 트랜잭션 없이 후보 승인 commit·마지막 단계 실패 rollback. 같은 방식으로 초안 승인 검증 |
| ApprovalIntegrationTest | 후보·공개 출처·선택 메모·인증/CSRF 회귀. 상태 조합·내부 정보 비노출 보강 |
| DirectRegistrationMigrationTest | V2→V3 보존·UNIQUE·불완전 APPLIED·감사·receipt 제약. target=3 기존 회귀이며 새 migration 검증으로 주장하지 않음 |
| CandidateSeparationMigrationTest | 이전 자료·legacy·출처 보존과 충돌 중단. 기존 회귀 유지 |
| frontend/src/api.test.ts, App.test.tsx | 기존 CSRF·폼·빈 201/204·message·후보/공개 흐름 회귀. 신규 헤더/오류 소비는 #17 공백 |

## 시나리오별 필수 assertion

기본은 실제 PostgreSQL Testcontainers + mock 외부 API + MockMvc, 호출자 트랜잭션 없이 완료 후 별도 조회다. Mockito만으로 UNIQUE·잠금·rollback을 입증하지 않는다. 아래 요청은 특별한 표기가 없으면 유효 세션·ADMIN·CSRF·새 키·현재 version이다.

| fixture/행위 | assertion | 계층 |
| --- | --- | --- |
| 기존 식당·완전한 DRAFT → 승인 | 200 DTO 참조/두 version 일치, 식당 수·정보 불변, Mention 필수 참조·유형·승인/공개 상태, APPLIED/적용 ID/version+1, 감사 actor/대상/전후 값 및 receipt path/hash/status/body 일치 | PG+MockMvc |
| 토큰으로 신규 식당 저장 → 승인 | 저장에서 식당 +1, 승인에서는 +0, 같은 식당 연결·정보 보존 | PG |
| chef/video/type 각각 누락 → 승인 | 400 DRAFT_INCOMPLETE·정확한 missingFields, 관계·초안 전체 행·승인 감사·receipt 불변 | PG+MockMvc |
| 없는 초안/CORRECTION/잘못된 참조 | 404 계약·승인 부작용 없음, 실제 FK 위반 저장도 거부. 고아 참조 fixture를 위해 제약을 해제하지 않음 | 서비스/PG+MockMvc |
| 비활성 관리자 → 최초/영수증 재생 | 403 ADMIN_INACTIVE, 업무 불변·유효 receipt도 재생하지 않음 | PG+MockMvc |
| timestamp/note 생략·null, timestamp=0 | 성공, review_note/evidence null, 미확인 timestamp null 및 확인된 0 구분 | PG |
| 잘못된 본문/키/version, 저장값의 잘못된 선택 입력 | strict 400, 음수/범위/메모 길이 거부, 성공 부작용 없음 | 입력 단위+PG |
| 다른 유형·동일 3항목 | 409 DUPLICATE_RELATION·정확한 기존 ID, Mention 수 불변·기존 전체 행 동일, 초안 DRAFT/version 불변 | PG+MockMvc |
| 기존 상태 4종 중복 | PENDING/REJECTED/APPROVED-SUSPENDED/APPROVED-PUBLISHED 각각 위 전체 불변 assertion | PG 매개변수 |
| 같은 키·동일 요청 순차/동시 | 동일 200 결과, 관계/승인 전이/감사/receipt 1회, replay 새 감사 없음 | PG |
| 같은 키·다른 version/초안/경로 | IDEMPOTENCY_CONFLICT, 직접 승인·초안 생성과 양방향 키 충돌, 전체 상태 불변 | PG+MockMvc |
| 다른 키/관리자·만료 영수증 재승인 | DRAFT_ALREADY_APPLIED·적용 ID/현재 version, 기존 관계/초안/감사/receipt 불변 | PG |
| 다른 관리자·키로 같은 초안 동시 승인 | 성공 1·APPLIED 충돌 1, 관계·승인 전이·감사 1회 | PG 병렬 |
| 다른 초안·같은 조합 동시 승인 | 성공 1·중복 1, Mention 1개, 승자만 APPLIED, 패자 초안/version/감사 불변 | PG 병렬 |
| 초안 vs 직접/후보 승인, 각각 양방향 승리 | 관계 1개·식당 1개, 패배 초안 DRAFT·패배 후보 disposition 유지, 직접 감사/receipt는 승리 시에만 증가, 응답은 각 기존/신규 계약 | PG 병렬 |
| PATCH vs 승인 양방향 | PATCH 선행 시 STALE_VERSION, 승인 선행 시 PATCH DRAFT_NOT_EDITABLE, 오래된 내용 승인/갱신 유실 없음 | PG 병렬 |
| 같은 Kakao ID 초안 생성·직접·후보 경합 | 식당 1개·기존 정보 불변. 서로 다른 조합은 각각 승인 가능 | PG 병렬 |
| Mention 저장 뒤/초안 flush/감사/receipt 실패 주입 | 새 Mention 0, 초안 전체 행·version·수정자/시각 복원, 승인 감사/receipt 0, 기존 식당·생성 이력 유지. 만료 receipt 삭제도 rollback | PG 실패 주입 |
| 실제 관계 UNIQUE 위반 | 실패 트랜잭션 밖 기존 ID 조회로 중복 오류, 부분 저장 없음. 단순 사전 검사 실패만으로 대체하지 않음 | PG 제약 경합 |
| 저장 후 토큰 만료·세션 소실 | 승인 성공, 추가 외부 호출 0 | PG+mock assertion |
| 공개 상태 조합 | APPROVED/PUBLISHED만 식당·출처 포함, 나머지 유효 조합 제외, PENDING/PUBLISHED 등 불법 조합 DB 거부. 메모/actor/초안/receipt 비노출 | PG+MockMvc |
| 익명·USER·CSRF 없음 | 기존 401/403, 쓰기 없음, 직접·후보·#14 회귀 유지 | PG+MockMvc |

병렬 테스트는 서로 다른 관리자·독립 트랜잭션·제한 시간을 사용한다. 필요하면 검사/삽입 지점을 제어해 양방향 순서와 실제 UNIQUE 경합을 관측한다. 시작 latch만으로 경합 경로가 입증됐다고 단정하지 않는다. PK sequence 간격은 rollback 실패로 판정하지 않는다.

## 검증 계획·보고·완료 조건

API 의미 변경이므로 Backend+Frontend를 선택한다. 실행이 허용된 Thread C에서만 다음 기존 진입점을 사용한다.

```powershell
powershell.exe -NoProfile -File C:\workspace\sedanji\scripts\verify.ps1 -Backend -Frontend
$verificationExit = $LASTEXITCODE
```

기존 Java/Gradle/Node 캐시·이미 접근 가능한 Linux Docker와 이미지가 전제다. 임시 Testcontainers PostgreSQL·테스트 Flyway·보고서/캐시/dist 생성과 개발 DB 변경을 구분한다. 환경이 없으면 NOT RUN이며 설치·이미지 pull·Docker 시작·검증 설정 완화로 우회하지 않는다. Python은 관련 변경이 없으면 미선택, 공통 검증 설정 변경 시 Skill에 따라 범위를 다시 판단한다.

현재 문서 작성 단계의 Backend·Frontend 타입 검사/테스트/빌드·Python은 전부 NOT RUN(사용자 실행 제한), 테스트 종료 코드 해당 없음. 실제 DB·외부 API·배포도 미검증이다. 정적 문서 링크·계약·diff 검토와 git diff --check만 수행한다.

완료 조건:

1. 승인된 endpoint·DTO·오류·멱등/상태 우선순위가 실제 HTTP assertion과 일치한다.
2. PostgreSQL에서 관계 UNIQUE·동일 초안 잠금·수정 경합·롤백·경로 간 경합을 입증한다. 미실행을 PASS로 표시하지 않는다.
3. 기존 직접/후보/#14 동작과 감사·멱등·공개·보안 불변식을 보존한다.
4. 신규 migration·Frontend 구현·무관한 확장·비밀정보가 없고 현재 계약 문서를 구현 결과와 맞춘다.
5. staged/unstaged/untracked·기존 변경·이번 변경을 구분하고 git diff --check 및 신규 파일 공백·참조를 확인한다.
6. 실제 검증 명령·전체 종료 코드와 Backend, Frontend typecheck/test/build, Python 각각 PASS/FAIL/NOT RUN 및 한계를 보고한다. 테스트 성공을 실제 DB 적용·운영 안전·배포 확인으로 확대하지 않는다.

Thread C 전달 시에는 이 문서와 함께 실제 구현 지시 및 검증 실행 허용 범위를 제공한다. 새 스레드 생성·자동 전달·Git 작업은 이 계획서에 포함되지 않는다.
