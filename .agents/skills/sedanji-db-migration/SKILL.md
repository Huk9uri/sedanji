---
name: sedanji-db-migration
description: Sedanji PostgreSQL·JPA·Flyway의 테이블·컬럼·인덱스·제약·저장 모델 변경을 분석하거나 승인된 신규 migration을 작성할 때 적용한다. 기존 데이터·배포 순서·DDL 트랜잭션과 잠금·JPA/Repository/API·검증 공백을 확인한다. 분석 요청은 분석만 수행하며 실제 개발·운영 DB 변경이나 데이터 정리 실행을 허용하지 않는다.
---

# Sedanji DB Migration

## 적용 조건과 실행 경계

- 스키마·인덱스·제약·JPA 저장 모델 변경, 기존 버전 업그레이드 또는 데이터 이관 영향 분석에 적용한다. 스키마 변경이 필요 없는 일반 코드 작업에 migration을 임의 추가하지 않는다.
- [루트 지침](../../../AGENTS.md), [Backend 지침](../../../backend/AGENTS.md)을 읽고 사용자 요청의 분석·구현·검증 범위를 확인한다. 문서 작성 승인, SQL 작성 승인, 테스트 실행, 실제 DB 적용은 서로 다른 범위다.
- 이 Skill은 실제 개발·운영 DB 접속·변경, 데이터 삭제·백필 실행을 하지 않는다. 분석만 요청되면 파일도 변경하지 않는다. 승인된 migration·매핑·테스트 구현만 수행하며 이번 작업이 문서 전용이면 그 문서만 작성한다.
- 기존 Flyway 파일을 수정·삭제·이름 변경하지 않고 신규 버전을 추가한다. 실행 코드·SQL·테스트가 현재 구현의 근거이며 설계·과거 적용 문서는 별도로 구분한다. 실제 DB를 조회하지 않았다면 **적용 버전·Flyway 상태·현재 데이터 정합성은 미확인**으로 보고한다.
- [Verification Skill](../sedanji-verification/SKILL.md)의 변경 수집·검증 선택·실행·보고 규칙을 그대로 따른다. API 의미가 바뀌면 [API Change Skill](../sedanji-api-change/SKILL.md)도 적용한다. Skill 생성만으로 모든 작업의 자동 실행을 보장하지 않는다.

## 1. 현재 저장소 근거 확인

- [migration 디렉터리](../../../backend/src/main/resources/db/migration)를 다시 열어 전체 버전·파일명·Git 변경을 확인한다. 작성 시점에는 [V1](../../../backend/src/main/resources/db/migration/V1__initial_schema.sql), [V2](../../../backend/src/main/resources/db/migration/V2__separate_candidates_and_mentions.sql), [V3](../../../backend/src/main/resources/db/migration/V3__prepare_direct_registration.sql)만 있다. 다음 번호를 V4로 고정하지 말고 새 파일·다른 작업과의 충돌을 재확인한다. 미확인 파일을 미적용이라고 간주하지 않는다.
- [application.properties](../../../backend/src/main/resources/application.properties)의 Flyway·JPA 설정을 확인하고 `ddl-auto=validate`, `open-in-view=false`를 유지한다. update/create로 스키마 변경을 대신하지 않는다. 환경변수 실제 값·인증정보는 출력하지 않는다.
- PostgreSQL 17은 [Compose](../../../compose.yaml)·Testcontainers 설정의 근거다. [build.gradle](../../../backend/build.gradle)의 의존성 관리와 실제 해석된 Flyway·PostgreSQL 모듈 버전을 확인한다. 로컬 캐시 버전만으로 배포 환경 버전을 확정하지 않는다.
- [DOMAIN_MODEL.md](../../../docs/DOMAIN_MODEL.md), [ADMIN_MIGRATION_PLAN.md](../../../docs/ADMIN_MIGRATION_PLAN.md), [V3 이관 기록](../../../docs/DIRECT_REGISTRATION_MIGRATION.md)은 모델·계획·과거 수행 증거로 읽는다. 문서의 건수·버전·백업 기록을 현재 DB 상태로 인용하지 않는다.

## 2. 사전 점검 체크리스트

각 변경에 `현재 정의 → 제안 정의 → 기존 데이터 조건 → 코드/배포 영향 → 검증 assertion → 미확인 사항`을 연결한다.

| 점검 | 확인 사항 |
| --- | --- |
| 버전·대상 | 기존 이력·체크섬 보존, 신규 버전 충돌, 대상 DB/스키마·search_path·명명 규칙. 실제 조회 여부 |
| 기존 데이터 | null·중복·고아 참조·길이/범위·상태 값, 행 수·분포·증가량, 승인·공개·과거 근거 보존 조건 |
| 제약·참조 | NOT NULL·DEFAULT·CHECK·UNIQUE·FK, null 비교 의미, 삭제·갱신 정책과 참조 테이블 영향 |
| JPA·조회 | 컬럼 타입·길이·nullable·enum·identity/sequence·@Version·연관관계, JPQL·derived query·native SQL·upsert |
| DDL·동시성 | SQL별 트랜잭션 허용 여부, 락 종류·유지 시간·장기 트랜잭션·동시 쓰기·데드락·테이블 재작성·디스크/IO |
| 배포 | 구 앱/신 스키마·신 앱/구 스키마 호환성, 쓰기 경로, migration과 앱 시작/JPA validate 순서, 중단·복구 방안 |
| 검증 | 빈 DB·기존 버전 데이터 업그레이드·충돌 중단·보존·JPA·서비스·API 회귀를 실제로 확인하는 assertion |

사전 데이터 확인 SQL의 작성과 실행은 구분한다. 이 Skill에서는 실제 개발·운영 DB에 조회조차 수행하지 않는다. 데이터가 확인되지 않았으면 필요한 확인 조건을 보고하고 안전하다고 확정하지 않는다. DB 내용을 추측해 빈 값·가짜 셰프·영상·승인 정보를 채우지 않는다.

## 3. 기존 데이터 호환성과 배포 순서

- **스키마 변경, 백필, 중복 정리를 별도 작업으로 분류**한다. 파괴 여부·대상 범위·시간/락·재실행·중단 조건을 각각 평가한다. 작은 SQL이나 nullable 컬럼 추가라는 이유만으로 무위험이라고 하지 않는다.
- 기존 데이터 삭제·병합·재작성·파괴적 SQL은 명시적 승인 없이는 구현하지 않는다. 승인된 SQL 작성도 실제 적용 승인이 아니며, 이 Skill에서 삭제·백필을 자동 실행하지 않는다.
- NOT NULL은 기존 null과 새 쓰기 경로를, UNIQUE는 중복 및 null 동등성 의미를, FK는 고아 참조·삭제 파급을 검토한다. 데이터 충돌을 자동 정리해 제약을 강제 통과시키지 않는다.
- 필요하면 확장 스키마 → 구/신 앱 호환 쓰기·읽기 → 별도 승인된 데이터 전환 → 검증 → 제약 강화/구조 축소 순서를 설계한다. 모든 변경에 같은 순서를 기계적으로 적용하지 않으며 백필·축소는 여기서 실행하지 않는다.
- 컬럼 삭제·이름/타입 변경은 구버전 앱·native SQL·DTO·기존 클라이언트 영향을 검토한다. 새 코드와 스키마의 원자적 동시 배포나 간단한 역변환을 가정하지 않는다.
- 실패 뒤 앱 롤백이 가능한지와 DB 데이터 복구가 가능한지는 별도로 평가한다. 새 데이터가 생긴 후의 역변환·후속 버전 수정·유지보수 중단 필요성을 확인하며 자동 down migration을 약속하지 않는다.

## 4. PostgreSQL DDL·Flyway 실행 호환성

- SQL별로 트랜잭션 가능 여부와 잠금 특성을 확인한다. PostgreSQL이 트랜잭션 DDL을 지원한다는 이유로 모든 명령·전체 migration 묶음이 원자적이라고 가정하지 않는다. 일반 DDL의 강한 테이블 락과 동시 쓰기·장기 트랜잭션의 영향을 검토한다. 락은 보통 트랜잭션 종료까지 유지되므로 대량 DML을 같은 단위로 묶을 때 유지 시간을 고려한다. 근거: [PostgreSQL 17 잠금 문서](https://www.postgresql.org/docs/17/explicit-locking.html).
- **`CREATE INDEX CONCURRENTLY`는 일반 트랜잭션 블록에서 실행할 수 없다.** 동시 인덱스 생성도 대기·추가 스캔·부하가 있고 실패 시 INVALID 인덱스가 남을 수 있다. 무잠금·무위험·실패 시 완전 복구로 표현하지 않는다. 근거: [PostgreSQL 17 CREATE INDEX](https://www.postgresql.org/docs/17/sql-createindex.html).
- 이런 명령을 쓸 때는 실제 Flyway 버전·에디션·PostgreSQL 지원 모듈·호출 경로, migration의 트랜잭션 분류, script-level `executeInTransaction`, `group`/`mixed`, PostgreSQL용 Flyway 잠금 설정과의 호환성을 별도로 확인한다. 설정이 실제로 지원·적용되는지와 별도 migration 분리 필요성을 검토한다. 일괄 `executeInTransaction=false`나 검증 완화로 해결하지 않는다. 근거: [Flyway 트랜잭션 처리](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/migration-transaction-handling).
- 실행 방식 변경이 필요하면 근거·비원자성·잔여 객체 처리·실패 테스트·승인 범위를 먼저 제시한다. 현재 작업이 문서 작성이면 설정 파일·SQL을 추가하지 않는다. Flyway 기본 동작·SQL 자동 감지를 실행 검증 없이 확정하지 않는다.

## 5. JPA·Repository·서비스·API 영향과 불변식

`신규 SQL → 기존 데이터 → Entity 매핑 → Repository 쿼리/native SQL → 서비스 트랜잭션 → DTO/API·Frontend`를 추적한다. JPA가 모든 DB 테이블·제약·인덱스를 표현한다고 가정하지 않는다. V3의 초안·감사·멱등 테이블만으로 대응 Entity·API가 완료됐다고 표현하지 않는다.

- [RegistrationService](../../../backend/src/main/java/kr/sedanji/registration/RegistrationService.java)의 승인 원자성을 보존한다. 후보 잠금·셰프/관리자/장소 검증·식당 재사용·관계 저장·후보 해결이 함께 커밋/롤백되어야 한다.
- [RestaurantCandidateRepository](../../../backend/src/main/java/kr/sedanji/restaurantcandidate/RestaurantCandidateRepository.java)의 PESSIMISTIC_WRITE와 호출자 트랜잭션, [MentionService](../../../backend/src/main/java/kr/sedanji/mention/MentionService.java)의 MANDATORY 경계, Chef/Restaurant/Mention의 @Version을 고려한다. 애플리케이션 사전 존재 검사만으로 DB 유일성·동시성 보호를 대체하지 않는다.
- Kakao Place ID 유일성, YouTube Video ID 식별, 셰프·식당·영상 조합 중복 방지를 유지한다. 관계 유형이 다르다고 같은 관계를 중복 생성하거나 이름만으로 동명이인 셰프를 합치지 않는다.
- [RestaurantRepository](../../../backend/src/main/java/kr/sedanji/restaurant/RestaurantRepository.java)의 `ON CONFLICT ... DO NOTHING`이 의존하는 제약·컬럼을 확인한다. 재사용 과정에서 기존 장소를 덮어쓰는 변경을 임의 도입하지 않는다.
- 식당과 출처 모두 `APPROVED AND PUBLISHED`를 유지한다. 공개 상태 CHECK·부분 인덱스·조회 predicate를 함께 검토하고 변경 시 [API Change Skill](../sedanji-api-change/SKILL.md)을 적용한다. 내부 후보·메모·관리자 정보가 공개 응답에 섞이지 않게 한다.
- 후보 매칭 MATCHED/REVIEW_REQUIRED/NOT_FOUND, 후보 처리 OPEN/RESOLVED/DISMISSED, 관계 검증·공개 상태는 별개다. API 실패를 NOT_FOUND로 변환하거나 데이터 전환으로 자동 승인·관계 유형·미확인 시점을 만들지 않는다. null과 확인된 0초를 구분한다.
- V2의 기존 승인 자료 보호·읽기 전용 legacy 보존, V3의 중복 관계 중단 조건·감사 이력 추가 전용·초안/FK/공개 제약을 우회하지 않는다. 순번·생성 키를 유지하며 이관 뒤 새 삽입 충돌 가능성도 검토한다.
- V3 스키마의 nullable/선택 메모, 기존 승인 서비스의 evidence 검증, Frontend의 메모 필수 조건은 층별 차이가 있다. 스키마 완화만으로 서비스·UI 비즈니스 규칙을 임의 변경하지 않는다.

## 6. 실패 중단·Flyway 상태·데이터 복구 구분

다음을 서로 독립적으로 기록한다. 실패를 감지했다는 사실만으로 롤백·이력 정리·복구가 완료됐다고 보고하지 않는다.

| 구분 | 확인·보고할 사항 |
| --- | --- |
| 중단 조건 | null·중복·고아 참조·불명확한 승인 자료, 예상 밖 행 수/공개 상태 변화, 락 대기·타임아웃·DDL 실패 시 멈출 지점과 후속 작업 중단 |
| Flyway 상태 | 대상 버전·실제 schema history·checksum·pending/failed 여부. 트랜잭션 내 실패와 비트랜잭션 실패가 같은 이력을 남긴다고 가정하지 않음 |
| DB 잔여 상태 | 실제 커밋·부분 적용·잔여 객체/INVALID 인덱스·이전 migration의 이미 완료된 커밋 여부. 트랜잭션 단위와 실제 관측 근거 |
| 복구 가능성 | 검증된 백업·복원 환경·복원 시험·복구 시점·데이터 손실 허용 범위·후속 쓰기 영향·담당 승인. 확인하지 못했으면 미확인 |

- 실제 DB 상태를 조회하지 않았으면 이력·잔여 객체·정합성을 확인한 것으로 보고하지 않는다. 이 Skill에서는 필요한 조사·판단 조건만 정리하고 실제 DB 조사·정리·재시도를 수행하지 않는다.
- Flyway 실패를 없애기 위한 자동 `repair`, checksum 재작성, schema history 조작, `clean`·검증 생략을 하지 않는다. 이력 정리와 실제 객체/데이터 복구는 다른 작업이며 원인·범위·별도 승인이 필요하다.
- 백업 파일 존재, `pg_restore --list` 성공, 과거 문서 기록만으로 **복원 가능·복구 가능이라고 가정하지 않는다.** [V3 기록](../../../docs/DIRECT_REGISTRATION_MIGRATION.md)도 실제 복원 시험 미수행을 명시한다. 운영 백업·복원 절차를 실제 확인하지 않았다면 미확인으로 남긴다.
- non-transactional 실패나 파괴적 변경은 원본이 자동 복구된다고 약속하지 않는다. 복구·전진 수정 계획을 제안할 수 있으나 데이터 삭제·백필·기존 migration 수정으로 우회 실행하지 않는다.

## 7. 대표 사례와 필요한 검증

| 사례 | 대응 규칙 | 필요한 assertion·검증 공백 |
| --- | --- | --- |
| nullable 컬럼·기본값 추가 | 기존 행·새 쓰기·JPA·구 앱 호환과 락/재작성 평가 | 기존 행·신규 저장 값·JPA 매핑·API 노출 영향 |
| NOT NULL 강화 | 기존 null 및 새 쓰기 경로 확인, 백필/제약 순서 분리 | 위반 데이터 중단·정상 자료 통과·기존 자료 보존 |
| UNIQUE 변경 | 중복·NULLS DISTINCT/NOT DISTINCT·동명이인 의미 확인, 자동 병합 금지 | 충돌 시 이관 중단·원본 보존·정상 중복 거부·upsert |
| FK·삭제 정책 변경 | 고아 참조·삭제 파급·연관 매핑 확인, 무승인 CASCADE 금지 | 참조 무결성·보존 정책·잘못된 참조 거부 |
| 일반/동시 인덱스 | 락·쿼리 predicate·부하·Flyway 실행 방식과 실패 잔여 상태 검토 | 생성·유효성·제약 효과. 소규모 통과로 운영 성능/락 시간을 확정하지 않음 |
| 이름/타입 변경·삭제 | 구/신 앱·native SQL·역변환·파괴성·단계적 전환 검토 | 이전 자료 업그레이드·경계값·API 호환·손실 여부 |
| 백필·중복 정리 | 스키마와 분리, 대상·보존·중단·재실행·승인 평가. 자동 실행 금지 | 대상/비대상 자료·의미·부분 실패·재실행 시나리오의 공백 보고 |
| 승인·공개 상태 변경 | 매칭/승인 분리, 이중 공개 조건·과거 근거 유지 | 미승인 자동 공개 없음·중단 관계 제외·출처·감사 보존 |

## 8. 기존 Skill 연계와 검증 완료 판단

- [Verification Skill](../sedanji-verification/SKILL.md)의 선택·실행·종료 코드·PASS/FAIL/NOT RUN 보고를 그대로 사용한다. 스키마/JPA 변경은 Backend가 기본이다. API 계약 영향은 [API Change Skill](../sedanji-api-change/SKILL.md)과 Backend + Frontend, 공통 설정·검증 스크립트 변경은 전체 규칙을 따른다. 변경 없음·문서 전용·실행 제한도 그 Skill의 목적별 판단을 따른다.
- 실행이 허용된 작업에서만 기존 [verify.ps1](../../../scripts/verify.ps1)로 임시 Testcontainers PostgreSQL 검증을 수행한다. 개발 DB용 `migrateDevelopmentDatabase`, `start-backend.ps1 -Migrate`·기본 서버 실행을 대신 사용하지 않는다. Docker 부재·캐시·권한 문제는 NOT RUN이며 임의 설치·다운로드·Docker 시작·검증 우회를 하지 않는다.
- [CandidateSeparationMigrationTest](../../../backend/src/test/java/kr/sedanji/CandidateSeparationMigrationTest.java), [DirectRegistrationMigrationTest](../../../backend/src/test/java/kr/sedanji/DirectRegistrationMigrationTest.java), [ApprovalIntegrationTest](../../../backend/src/test/java/kr/sedanji/ApprovalIntegrationTest.java), [RegistrationTransactionTest](../../../backend/src/test/java/kr/sedanji/RegistrationTransactionTest.java)를 읽는다. 빈 DB 전체 구축뿐 아니라 이전 버전 fixture→신규 버전 업그레이드·충돌 중단·근거 보존·JPA·서비스 경계를 검토한다.
- **신규 migration을 실제 검증하는 assertion이 있는지 확인한다.** 테스트 이름·기존 전체 통과·테이블 생성만으로 새 제약·데이터 보존·실패 중단·동시성 검증 완료를 단정하지 않는다. 버전 target 고정 테스트와 최신 버전까지 실행하는 테스트도 구분한다.
- `변경 요구 → 테스트 fixture/이전 버전 → 신규 적용 경로 → assertion → 남은 공백`을 보고한다. 추가 테스트 필요성을 명시하고 승인된 범위에서만 구현한다. 과거 기대값·기존 migration·설정을 고쳐 단순히 통과시키지 않는다.
- 테스트 통과와 실제 운영 데이터 안전성은 다르다. 실제 분포·대용량·동시 부하·장기 락·배포 혼재·백업 복원·실패 복구 미검증을 별도로 보고한다. 테스트용 임시 DB의 롤백 성공으로 운영 복구 가능성을 주장하지 않는다.

## 안전 경계와 최종 보고

의존성·도구·이미지 자동 설치, Docker/서버 자동 시작, 실제 외부 API 호출, 자동 stage·커밋·푸시를 하지 않는다. 존재하지 않는 Expo 앱을 가정하지 않는다. 신규 SQL 작성도 현재 승인 범위를 벗어나지 않는다.

최종 보고에는 변경 파일·신규 버전 선정 근거, 데이터/DDL 위험, JPA·Repository·API·구/신 앱 영향과 배포 순서, 보존한 불변식, 실패 중단·Flyway 상태·복구 가능성의 확인/미확인, assertion·테스트 공백, Verification Skill의 실행 결과·미실행 사유를 포함한다. `git diff --check`, 신규 파일 참조·공백·범위도 확인한다. 문서 정적 검토만 했다면 앱 테스트·DB 작업은 NOT RUN이며 해당 실행 종료 코드는 없다.
