# 2단계 — Gradle·JPA·Testcontainers 기반 전환

작업일: 2026-10-22. `AGENTS.md`와 `DOMAIN_MODEL.md`를 기준으로 진행했다.

## 전환 전 기준점

사용자의 명시적 승인으로 기존 구현을 아래 6개 커밋으로 나누고 `origin/main`에 푸시했다. 영어 접두어와 한국어 본문을 사용했다.

| 커밋 | 내용 |
| --- | --- |
| 674ed80 | docs: 장소 매칭 실제 결과와 추가 영상 검증 기록 |
| 2fad901 | build: 로컬 데이터베이스와 실행 환경 구성 |
| b248b7e | feat: 영상 수집과 관리자 승인 백엔드 구현 |
| f4e2cf6 | feat: 관리자 검토 화면과 승인 식당 지도 구현 |
| 2b65e18 | test: 실서버 연결 검증과 실행 안내 추가 |
| d66c959 | docs: 개발 지침과 단계별 도메인 전환 설계 |

푸시 전 Python 30개, 백엔드 17개, 프론트엔드 5개를 재실행해 모두 통과했고 프론트엔드 빌드도 성공했다. Git 대상 파일에서 실제 서버 키·관리자/DB 비밀번호가 포함되지 않았음을 확인했다.

## 변경 사항

- Maven POM을 제거하고 Gradle 8.14.3 Wrapper, `settings.gradle`, `build.gradle`을 추가했다. Spring Boot 3.5.16과 Java 17은 유지했다.
- Wrapper는 공식 배포본의 SHA-256을 확인한다. 로컬 실행 스크립트는 `.tools/gradle-home`을 캐시로 사용한다.
- 기존 V1 테이블을 매핑하는 ChefEntity, VideoEntity, RestaurantEntity, MentionEntity, PlaceCandidateEntity를 추가했다. 저장소는 `chef/`, `video/`, `restaurant/`, `mention/` 패키지에 배치했다.
- CatalogService의 JdbcTemplate 의존성을 제거하고 Spring Data JPA 저장소를 사용한다. 기존 요청/응답 DTO와 공개 승인 흐름은 유지한다.
- 여러 후보에서 같은 식당을 동시에 승인할 때 중복 생성을 막기 위한 PostgreSQL 원자적 upsert만 RestaurantRepository의 명시적 native query로 유지했다. 나머지 조회·저장·검토 상태 변경은 JPA를 사용한다.
- 후보 검토는 `PESSIMISTIC_WRITE` 잠금으로 보호한다. 승인·반려 상태는 영속 엔티티의 변경 감지로 저장한다.
- `ddl-auto=validate`, `open-in-view=false`로 설정했다. 기존 V1 마이그레이션과 개발 DB 스키마는 변경하지 않았다.
- Testcontainers 2.0.5가 통합 테스트 전용 PostgreSQL 17 컨테이너를 임의 포트에 생성한다. 테스트 종료 후 컨테이너를 정리하며, 개발 DB나 이전 `sedanji_test`에 연결하지 않는다.
- 테스트 실행 시 `.env`를 읽지 않는다. 관리자는 테스트 전용 설정을 쓰고 외부 API는 모의 응답으로 대체한다. Docker가 없으면 통합 테스트가 실패하도록 하여 건너뛴 테스트를 성공으로 표현하지 않는다.

현재 MentionEntity는 기존 V1 호환 매핑이며 목표 ERD의 최종 Mention이 아니다. 기존 이름 유일성 제약과 추출 후보가 섞인 구조 역시 아직 유지한다. 다음 단계에서 새 마이그레이션과 함께 전환한다.

## 검증 결과

| 항목 | 결과 |
| --- | --- |
| Gradle Wrapper 생성 및 배포본 해시 | 성공 |
| Java 추출/URL 회귀 테스트 | 6개 통과 |
| Testcontainers + JPA 통합 테스트 | 13개 통과 |
| 백엔드 합계 | 19개, 실패/오류/건너뜀 0개 |
| 추가한 JPA 회귀 | 영속성 컨텍스트 초기화 후 승인 관계 재조회, 재검색 시 이전 후보 스냅샷 제거 |
| 실행 JAR 생성 (`bootJar`) | 성공 |
| 실제 개발 DB에 JPA 서버 기동 | 성공, 스키마 검증 통과 |
| 전환 전후 API 응답 비교 | 동일: 영상 6개, 후보 3개, 셰프 0개, 공개 식당 0개 |
| 적용된 V1 파일 | Git 기준점과 동일 |

이 작업에서 앞서 통과한 Python 30개·프론트엔드 5개와 합산하면 54개다. 백엔드 외 코드는 변경하지 않았다. 전환 검증을 위해 실제 식당을 승인하거나 외부 API를 다시 호출하지 않았다.

실행 중인 Maven 서버를 종료하고 Gradle `bootRun` 서버로 교체했다. 저장된 영상·후보·셰프·공개 식당의 전체 API 응답을 읽기 전용으로 비교했으며, 비교 결과는 `results/before-jpa.json`, `results/after-jpa.json`에 저장했다(Git 제외).

## 재현

```powershell
# Docker Desktop만 실행되어 있으면 테스트용 DB는 자동 생성
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -Test

# 실행 JAR
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1 -Package

# 개발 DB와 .env를 사용하는 서버
docker compose up -d db
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1

# 실제 API의 저장 상태를 읽기 전용 비교 (.env의 로컬 관리자 계정 사용)
python scripts/check-backend-state.py --output results/before.json
# 서버 교체 후
python scripts/check-backend-state.py --compare results/before.json --output results/after.json
```

테스트 XML/HTML은 `backend/build/test-results/test/`, `backend/build/reports/tests/test/`에 생성한다. 실행 JAR는 `backend/build/libs/sedanji-0.1.0.jar`다. 캐시·빌드·로컬 결과·비밀 파일은 Git에서 제외한다.

## 다음 단계

3단계는 목표 ERD에 맞는 도메인 저장 구조와 후속 Flyway 전환이다. 후보와 근거 관계 분리, 언급 유형·검증 필드, ImportJob, DB 기반 AdminUser를 다룬다. API 어댑터·프론트엔드 전환은 각각 이후 단계다.

기준점 6개 커밋은 푸시했다. 이후 사용자 승인에 따라 이번 2단계 전환을 빌드·저장 계층·테스트·문서 단위로 커밋하며, 전환 커밋의 원격 푸시는 아직 수행하지 않는다.

공식 참고: [Spring Boot 3.5 Gradle 플러그인](https://docs.spring.io/spring-boot/3.5/gradle-plugin/index.html), [Testcontainers PostgreSQL 모듈](https://java.testcontainers.org/modules/databases/postgres/), [JPA 잠금](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
