# AGENTS.md 기준 단계별 구현

작성일: 2026-10-22. 이번 요청부터 루트 AGENTS.md를 개발 기준으로 적용한다.

## 최신 제품 방향에 따른 후속 계획 — 2026-11-02

아래 단계와 결과는 기존 PoC·기반 전환의 이력이다. 현재 제품 범위와 개발 순서는 [최신 요구사항](REQUIREMENTS.md)을 우선한다. 사용자 앱은 React Native이며 기존 사용자 웹 화면을 그대로 제품으로 확장하지 않는다. 첫 단계인 관리자 식당 관리의 코드 점검 결과와 구현 순서는 [관리자 구현 계획](ADMIN_IMPLEMENTATION_PLAN.md)에 정리했다. 해당 계획 작성 시 실행 코드·DB 변경이나 신규 테스트 수행은 없었다.

## 전환 전 상태와 차이

기존 코드는 동작을 검증한 초기 구현이다. Maven / Spring JDBC / JavaScript를 사용하고 있어 지침의 Gradle / Spring Data JPA / TypeScript와 차이가 있다. 프론트엔드의 Tailwind CSS, shadcn/ui, TanStack Query, React Router와 백엔드의 Testcontainers도 아직 적용하지 않았다.

추출 후보와 Mention이 하나의 테이블에 섞여 있으며, 언급 유형·ImportJob·DB 기반 AdminUser가 없다. 이 상태를 지침에 맞는 서비스 설계가 완성된 것으로 표현하지 않는다. 기존 테스트 통과와 실제 API 성공은 해당 구현의 검증 이력으로 보존한다.

## 단계와 완료 조건

| 단계 | 작업 | 완료 조건 |
| --- | --- | --- |
| 1. 모델 설계 | ERD, 후보와 근거 관계 분리, 상태·제약·전환 방침 | `DOMAIN_MODEL.md` 작성. 실행 코드·DB 변경 없음 |
| 2. 백엔드 기반 | Maven→Gradle 전환, JPA·도메인 패키지·Testcontainers 기반 | Gradle 빌드와 기존 동작 회귀 검증. 적용된 V1 보존 |
| 3. 도메인 저장 | Chef/Restaurant/Video/Mention/ImportJob/RestaurantCandidate/AdminUser 구현, 후속 Flyway | FK·유일성·승인 조건·기존 데이터 전환 검증 |
| 4. 수집·매칭 | 공식 API 어댑터 분리, ImportJob 상태, 설명란 근거와 장소 상태 저장 | API 실패와 NOT_FOUND 분리, 재시도·중복 방지 테스트 |
| 5. 관리자 검증 | 셰프·장소·언급 유형·시점·근거 검토 및 승인/반려 | 승인 전 비공개, 관계별 검토 이력, 동시 승인 방지 |
| 6. 프론트엔드 | TypeScript, Router, Query, Tailwind/shadcn/ui, 기능별 구조 전환 | 기존 화면 동작 유지, 로딩·실패·빈 결과·권한 처리 검증 |
| 7. 공개 지도와 E2E | 승인 관계만 지도/식당/셰프/영상으로 연결 | 핵심 승인→공개 흐름 E2E, 키 비노출, 실제 지도 확인 |

각 단계에서 변경·검증 결과와 남은 한계를 보고한다. 단계별로 필요한 범위만 구현하고 다음 단계의 기능을 미리 확장하지 않는다. 지침에 정해진 기술로 전환하므로 이를 다른 기술로 대체하기 위한 승인 요청은 하지 않는다.

## 1단계 결과

1단계 문서인 [도메인 ERD와 제약](DOMAIN_MODEL.md)을 작성했다. 장소 매칭과 셰프–식당 관계 검증을 분리하고, 미확인 시점은 NULL로 보관하도록 정의했다. 기존 DB·실행 코드·적용된 V1은 변경하지 않았다. 문서 변경이므로 실행 테스트를 새로 돌리거나 기존 52개 통과를 이번 단계의 신규 검증으로 계산하지 않았다.

## 2단계 결과

사용자의 승인으로 전환 전 구현을 기능별 6개 커밋으로 나눠 `origin/main`에 푸시했다. 이후 Gradle Wrapper, 기존 V1 테이블을 매핑하는 JPA 엔티티·도메인별 저장소, 임시 PostgreSQL을 생성하는 Testcontainers 통합 테스트로 전환했다. [전환 기록](BACKEND_TRANSITION.md).

목표 모델을 위한 후속 Flyway, 후보/Mention 분리, ImportJob·AdminUser는 3단계다. 기반 전환과 섞어 구현하지 않았다. 이후 사용자 승인으로 전환 변경을 빌드·저장 계층·테스트·문서 단위로 커밋하고 원격 푸시를 완료했다.

## 3단계 중 후보·관계 분리

사용자의 후속 요청으로 V2에서 RestaurantCandidate와 Mention을 분리하고 기존 후보·장소 스냅샷을 보존했다. 관계 승인에는 셰프·식당·영상·언급 유형·확인 근거·검토 메모를 요구한다. 필요한 ImportJob 성공 기록과 DB 관리자 FK를 함께 도입했다. [상세 기록](CANDIDATE_MENTION_SEPARATION.md).

수집 작업의 실패·재시도 추적과 나머지 목표 모델 정리는 후속 작업이다. 사용자 승인에 따라 분리 변경을 작업별로 커밋하며, 원격 푸시는 수행하지 않는다.
