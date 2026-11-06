# 관리자 등록 모델 마이그레이션 계획

작성일: 2026-11-02. V3의 과거 개발 DB 적용 기록은 [적용 결과](DIRECT_REGISTRATION_MIGRATION.md)를 참조한다. 현재 작업 트리의 V4는 아래 #16 절이며 실제 대상 DB 적용 여부는 확인하지 않았다. [모델](DOMAIN_MODEL.md)과 [API 계약](ADMIN_API.md)을 함께 적용한다.

## #16 V4 작업 트리: Video 출처 상태만 확장

V1~V3 원본을 유지하고 `V4__video_source_check.sql`을 추가했다. 기존 계획의 정정·공개 제어 테이블은 이번에 만들지 않는다. 이후 후속 migration 번호는 그 시점에 다시 정한다.

Video에 NOT NULL DEFAULT UNCHECKED 상태와 nullable TIMESTAMPTZ 확인 시각·VARCHAR(20) 원인을 추가한다. 상태/시각/원인의 조합은 [API #16 계약](ADMIN_API.md)과 일치하는 CHECK로 제한한다. 기존 메타데이터·Mention·초안·감사·영수증은 변경하지 않고 AVAILABLE 백필도 하지 않는다. 기존 JPA 생성자는 UNCHECKED, 기존 native insert는 DB DEFAULT를 사용한다. 공개 DTO는 유지한다.

배포는 migration 후 새 앱 순서다. 확장 스키마는 기존 컬럼만 쓰는 구 앱을 수용하며 신 앱은 V4 전 스키마를 사용할 수 없다. `ddl-auto=validate`, `open-in-view=false`를 유지한다. ALTER TABLE은 강한 잠금과 CHECK 검증의 기존 행 스캔을 수반할 수 있다. 실제 데이터 규모·잠금 시간·혼재 배포·백업 복원·운영 성능은 미검증이다. 실제 DB 접속·적용·상태 보정은 실행하지 않는다. 적용 실패 시 자동 repair/clean/down migration을 하지 않고 원인과 대상 DB 상태를 별도로 확인해야 한다.

신규 테스트는 빈 DB→V4, V3 fixture→V4의 메타데이터·관계·공개 상태·초안·감사·영수증 보존, 기존 영상 UNCHECKED, 불법 상태 조합 거부와 기존 JPA/native 생성 호환을 assertion한다. 2026-11-06 최초 AuthorizationManager 거부 후 정책 변경 없이 권한 확장으로 실행했다. 새 테스트 mock 접근 범위를 수정한 뒤 기존 verify 진입점의 Backend 재검증은 신규 migration·조회 테스트를 포함하여 컴파일·178개 테스트 PASS(실패·skip 0, 종료 0)다. 실제 개발 DB 적용·운영 데이터 안전성 검증은 아니다.

## 원칙과 적용 전 점검

- 적용된 V1·V2와 legacy archive는 수정하지 않는다. 실제 Flyway 이력을 확인한 후 다음 버전 번호를 확정한다. 아래 V3·V4는 저장소가 V2까지인 현재의 예정 번호다.
- DB 상태와 건수는 현재 읽어 확인하지 않았다. 과거 개발 DB 상태로 이관 가능 여부를 단정하지 않는다.
- 기존 Chef·Restaurant·Video·Mention ID와 후보·설명란·근거·검토 메모·승인자·시각을 보존한다.
- DB 백업·복구 확인, Flyway checksum, 실제 제약 이름, 데이터 충돌과 영향 건수를 읽기 전용으로 점검한다. 보고한 범위 외 자료를 삭제하거나 자동 병합하지 않는다.

핵심 충돌 점검 SQL(실행 예정 예시):

```sql
-- 기존에는 유형이 다르면 등록 가능했으므로 새 3항목 기준의 충돌 확인.
SELECT chef_id, restaurant_id, video_id, count(*) AS relation_count,
       array_agg(id ORDER BY id) AS mention_ids
FROM mentions
GROUP BY chef_id, restaurant_id, video_id
HAVING count(*) > 1;

-- 생성/승인에 필요한 값과 기존 제약의 실제 정의 점검.
SELECT id, verification_status, mention_type, reviewed_by, reviewed_at
FROM mentions
WHERE verification_status = 'APPROVED'
  AND (mention_type IS NULL OR reviewed_by IS NULL OR reviewed_at IS NULL);

SELECT conrelid::regclass AS table_name, conname,
       pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid IN ('chefs'::regclass, 'restaurants'::regclass, 'mentions'::regclass);
```

충돌이 있으면 마이그레이션을 중단한다. 임의로 관계를 선택해 삭제·병합하거나 유형·검증 상태를 추정하지 않는다. 해소 대상과 처리 방침을 사용자에게 제시한다. 미승인 기존 Mention도 검사·보존 대상이며 새 CREATE 초안으로 묵시 변환하지 않는다.

## V3 적용 완료: 직접 등록과 승인 제약

1. Chef 이름 UNIQUE를 제거하고 일반 이름 검색 인덱스를 추가한다. JPA unique 선언·단일 결과 조회·이름 자동 재사용 서비스도 함께 변경한다. 기존 이름·ID는 그대로 둔다.
2. Restaurant·Chef·Mention에 version을 0으로 추가한다. Restaurant·Chef에 created_at을 추가한다. 과거 정확한 생성 시각을 복구할 수 없는 행은 이관 시각으로 채우고 이를 원래 등록 시각이라고 설명하지 않는다. 관리자 최근 등록순은 created_at 뒤에 id 내림차순으로 안정 정렬한다.
3. 주소 미제공을 지원하도록 Restaurant의 주소 컬럼 NULL을 허용한다. 기존 주소 문자열을 임의로 지우거나 재조회로 덮어쓰지 않는다. 신규 빈 주소의 정규화 방침을 DTO·JPA와 맞춘다.
4. Mention의 기존 4항목 UNIQUE를 3항목(chef_id, restaurant_id, video_id)으로 교체한다. 먼저 충돌 검사를 수행하고, 실패하면 전체 이관 트랜잭션을 롤백한다.
5. evidence를 신규 직접 등록에서 NULL 허용하도록 바꾸되 과거 evidence 값은 보존한다. APPROVED의 review_note 필수 CHECK를 제거하고 유형·승인자·승인 시각 필수 CHECK로 교체한다. 이를 DTO·서비스·엔티티·기존 후보 승인 경로에도 일치시킨다.
6. Mention.publication_status를 추가한다. 기존 APPROVED는 PUBLISHED, 기존 PENDING/REJECTED는 SUSPENDED로 이관하여 기존 공개 여부를 유지한다. 상태 유효값과 PUBLISHED이면 APPROVED라는 CHECK를 추가한다. 새 관계의 자동 승인을 생성하지 않는다.
7. mention_drafts 생성: 식당·작성자 필수, 셰프·영상·유형 선택, CREATE/CORRECTION 및 DRAFT/APPLIED, 대상 관계·base_version·version·시각·사유 저장. 식당만 고른 초안을 허용하고 생성 완료/정정 적용 시 최종 FK·유일성은 Mention에서 보장한다.
8. admin_audit_events와 admin_request_receipts를 생성한다. 새 승인 이후에는 작업과 이력을 함께 저장한다. 과거 승인·메모는 원본 컬럼에 남기고 백필 감사 이벤트가 필요하면 `MIGRATED_SNAPSHOT`으로 표시한다. 확인되지 않은 과거 변경·정정 이력을 만들어내지 않는다.
9. 승인된 공개 관계 조회 인덱스, restaurant_id·초안 상태·updated_at 인덱스, 관리자별 멱등키 UNIQUE와 expires_at 인덱스를 추가한다. 실제 쿼리 기준으로 필요한 인덱스만 둔다.

API 선택 토큰은 서버 세션 임시 캐시이므로 검색 스냅샷 업무 테이블을 새로 만들지 않는다. 멱등키 결과는 최대 24시간 보관하며 요청 시 만료 자료를 제한된 건수로 정리한다. 별도 Redis·분산 작업 큐를 도입하지 않는다.

## 과거 V4 제안: 정정·공개 제어·출처 확인 (미승인 후속 범위)

1. restaurant_revisions·chef_revisions를 생성한다. 대상·제안값·필수 정정 사유·base_version·자체 version·DRAFT/APPLIED·작성/수정자·시각을 저장한다. 정정 대기 중인 현재 기본 정보는 바꾸지 않는다.
2. MentionDraft CORRECTION과 각 Revision에 대상별 열린 초안 하나를 보장하는 부분 UNIQUE를 둔다. 기존 CREATE 초안의 미완성 허용과 구분한다.
3. 감사 이벤트 FK·action별 필수 사유 검증을 확장한다. 정정 승인·중단·복구와 이력 저장은 동일 트랜잭션이다. 감사 기록에는 비밀키·세션·선택 토큰·외부 인증 헤더를 저장하지 않는다.
4. Video에 source_check_status·source_checked_at·안전한 확인 사유 코드를 추가한다. 과거 자료는 UNCHECKED로 채우며 현재 조회 가능/삭제를 추정하지 않는다. 기존 설명란·메타데이터는 보존한다.
5. 공개 중단·복구는 검증 상태를 APPROVED→REJECTED로 대체하는 작업이 아니다. publication_status를 바꾸고 version·사유·처리자를 기록한다. 이전 공개 중단·정정 이력은 없으므로 임의 생성하지 않는다.

V3 모델을 쓰는 코드와 V4 기능은 배포 단위를 맞춘다. API가 없는 동안 버튼을 성공 처리하거나 기존 승인 경로가 새 제약을 우회하도록 두지 않는다.

## 전환과 복구

기존 실행 코드는 메모 필수·4항목 중복·APPROVED 단독 공개 조건을 사용한다. 새 제약과 상태 도입 후 구버전 서버가 계속 쓰기나 공개 조회를 하면 잘못된 결과를 만들 수 있으므로, 초기 단일 서버 환경에서는 **쓰기 중단·백업 → 마이그레이션 → 호환 코드 시작 → 검증** 순으로 전환한다. 무중단 배포가 검증됐다고 표현하지 않는다.

이관 SQL이 실패하면 해당 버전 트랜잭션을 롤백하고 이전 상태를 유지한다. 성공 후에는 신규 자료가 생길 수 있으므로 자동 down migration이나 새 테이블 삭제를 하지 않는다. 배포 직후 쓰기 재개 전 실패라면 백업 복구를 검토하고, 쓰기 재개 후라면 신규 데이터 보존·복구 범위를 먼저 확인하여 전진 수정 또는 통제된 복구를 선택한다.

## 구현 시 검증 기준

- V2 데이터가 새 스키마에서도 같은 ID·근거·메모·승인자·공개 결과를 유지한다.
- 3항목 중복 데이터가 있으면 이관이 실패하고 원본이 남는다.
- 기존 다른 유형의 관계를 묵시 병합하지 않는다. 새로운 동일 조합은 유형이 달라도 DB가 거부한다.
- 동명이인 셰프 신규 등록 가능, 식당 Kakao ID와 Video ID 중복 생성 불가.
- 메모 없이 승인 가능, 식당만 있는 초안 가능, 불완전한 관계 승인 불가.
- 원본과 수정안 격리, version 충돌 거부, 중단 상태 자동 복구 금지.
- 공개 중단 관계는 공개 응답에서 제외하되 다른 관계와 관리자 이력은 유지한다.
- 기존 후보·place snapshot·legacy archive가 보존되고 기존 승인 경로도 새 정책과 일치한다.
- DB 기반 관리자·권한·CSRF, 공개 DTO 비밀정보/내부자료 비노출 회귀 검증.

V3 관련 검증과 기존 회귀 테스트 42개를 Testcontainers에서 통과했고 개발 DB 적용 결과와 구분하여 [결과 문서](DIRECT_REGISTRATION_MIGRATION.md)에 기록했다. V4 기능의 검증은 해당 구현 시 수행한다.
