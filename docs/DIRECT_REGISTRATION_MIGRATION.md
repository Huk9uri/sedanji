# 직접 등록 기반 V3 적용 결과

적용일: 2026-11-02. 대상: 로컬 개발 PostgreSQL `localhost:15432`, public 스키마.

> 이 문서는 2026-11-02 당시 적용·검증 기록이다. 아래 건수·통과 수·다음 작업·커밋 여부는 당시 상태로 보존한다. 2026-11-05 현재 코드에는 직접 검색·메타데이터 등록·직접 승인 API가 추가돼 있다. 최신 구현과 남은 범위는 [관리자 구현 계획](ADMIN_IMPLEMENTATION_PLAN.md), 현재/목표 계약은 [관리자 API](ADMIN_API.md)를 따른다. 이번 문서 정리에서 DB 적용 상태나 테스트를 재확인하지 않았다.

## 적용 범위

현재 코드 보충(2026-11-05, #14 작업 트리): V3 초안 테이블을 Entity·Repository에 매핑하고 CREATE 초안 생성·조회·목록·부분 수정 및 감사·생성 멱등 저장을 추가했다. 새 migration이나 실제 DB 적용은 하지 않았다. 이번 검증은 최초 샌드박스 실행 차단 후 동일 명령을 재실행했다. Frontend 타입 검사·테스트 11개·빌드는 PASS(각 0), Backend는 Docker Engine 접근 실패로 NOT RUN, 전체 종료 코드 1이다. 이후 Docker 실행 후 verify.ps1 -Backend 재검증에서 컴파일·테스트 137개 PASS(실패·skip 0, 전체 종료 코드 0)를 확인했다. 실제 개발 DB는 변경하지 않았다. 아래 적용·통과 기록은 2026-11-02 당시 기록이다.

`V3__prepare_direct_registration.sql`을 적용하고 V1·V2·V3 Flyway 검증을 완료했다. 기존 V1·V2는 변경하지 않았다. 정정·복구용 V4는 미적용이다.

- 셰프 동명이인 허용, Chef·Restaurant·Mention version과 기본 생성 시각 추가.
- 셰프·식당·영상 3항목 중복 방지. 유형이 달라도 동일 조합은 중복이다.
- 검증 메모 선택, 과거 evidence·메모 보존. 신규 직접 등록용 evidence는 NULL 허용.
- 검증 상태와 publication_status 분리. 기존 APPROVED는 PUBLISHED, 미승인 관계는 SUSPENDED로 보존하며 새 승인을 만들지 않는다.
- 식당만 선택한 초안용 mention_drafts, 추가 전용 admin_audit_events, admin_request_receipts 생성.
- 기존 승인 API를 선택 메모·새 중복 기준에 맞추고 공개 조회에서 중단된 관계를 제외했다.
- 동일 장소 재사용 과정에서 기존 이름을 upsert로 덮어쓰지 않도록 변경했다.

직접 등록·임시 저장 API와 승인한 새 화면은 아직 미구현이다. 기존 후보 검토 UI는 이전 화면이며 메모 입력 제약도 새 UI 구현에서 맞출 예정이다. 테이블 생성을 기능 완성으로 표현하지 않는다.

## 백업·데이터 보존

V1·V2 성공 이력, 새 기준의 중복 관계 0건, 실제 제약 이름을 확인했다. 백엔드가 실행되지 않는 상태에서 Git 제외 경로 `results/before-v3-20261102-201640.dump`에 백업했다(35,427 bytes). `pg_restore --list`로 아카이브 목록을 읽었으며 실제 복원 시험을 수행한 것은 아니다.

영상·후보·장소 스냅샷·수집 기록·legacy 전체 행을 정렬한 JSON 지문과 건수로 적용 전후 비교했으며 모두 동일했다. 비교 파일도 results에만 보관한다.

| 대상 | 적용 전 | 적용 후 |
| --- | ---: | ---: |
| Video | 6 | 6 |
| Chef / Restaurant / Mention | 각 0 | 각 0 |
| RestaurantCandidate | 3 | 3 |
| PlaceCandidate | 4 | 4 |
| ImportJob | 6 | 6 |
| MentionDraft / AdminAuditEvent / AdminRequestReceipt | 없음 | 각 0 |

Flyway V1·V2·V3 모두 success=true다. 실제 YouTube/Kakao API 호출과 개발 DB의 샘플 승인 자료 생성은 하지 않았다.

적용 후 동일 마이그레이션 명령을 다시 실행해 추가 적용 0건과 이력 검증 통과를 확인했다.

## 검증

전체 백엔드 **42개 통과, 실패·오류 0개**. Testcontainers 임시 PostgreSQL에서 실행했으며 개발 DB를 테스트 DB로 사용하지 않았다.

- 신규 이관 테스트 6개: 기존 근거·공개 상태 보존, 중복 충돌 시 원본 유지 및 이관 중단, 동명이인·선택 메모·승인 제약, 최소 초안, 정정 초안 제약, 감사 이력 변경 차단·요청키 중복 방지.
- 신규 통합 테스트 3개: 동명 셰프 독립 생성, API 승인 요청의 메모 생략, 중단 관계 비노출과 다른 유형으로 중복 생성 불가.
- 기존 33개 회귀 확인. 최초 실행에서 기존 테스트의 ‘미승인/PUBLISHED’ 자료가 새 CHECK에 걸렸다. 미승인/SUSPENDED로 수정한 뒤 전체 재실행을 통과했다.
- 실행 도구의 최초 사전 validate가 pending V3를 거부하여 DB 변경 전에 종료됐다. migrate 내장 검증·적용·사후 validate로 수정해 실제 실행에 성공했다.

## 실행

Docker Desktop과 .env를 준비한 뒤 프로젝트 루트에서 실행한다.

```powershell
docker compose up -d db
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -Migrate
```

`-Migrate`는 앱을 시작하지 않고 로컬 DB에 Flyway만 실행하며 원격 URL을 거부하고 clean을 비활성화한다. 일반 앱 시작도 새 Flyway 파일을 적용하므로 사전 점검·백업·검증 없이 변경 파일을 추가하지 않는다.

Windows 예약 범위 55430~55529와 충돌해 DB 접속 포트를 15432로 변경했다. compose·백엔드 기본 URL·.env.example·README를 맞췄다. 실제 .env에는 DATABASE_URL이 없어 기본 URL을 사용하며 비밀 값은 변경하지 않았다.

다음 작업은 직접 장소 검색·선택과 초안 저장·재개·승인 API다. 새 UI와 정정·복구 API는 후속 범위다. 커밋·푸시는 하지 않았다.
