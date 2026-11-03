# Sedanji GitHub Actions CI

## 실행 범위와 현재 상태

[workflow](../../.github/workflows/ci.yml)는 모든 Pull Request와 `main` Push에서 Backend·Frontend·Python을 독립 Job으로 실행한다. `needs`와 경로 필터는 없다. 문서 변경도 세 Job을 실행한다. 같은 PR/ref의 이전 실행은 취소한다. 취소된 실행은 검증 성공이 아니다.

현재 로컬 `origin/HEAD`는 `origin/main`이다. GitHub 저장소의 실제 기본 브랜치 및 Actions 정책은 첫 실행 전에 확인한다. 이 문서 작성 단계에서는 의존성 설치, Docker, 테스트, DB 작업 또는 실제 GitHub Actions 실행을 하지 않았다. 과거의 Backend 59개·Frontend 11개·Python 30개 통과 기록은 이번 CI 결과가 아니다. 테스트 수는 실행 로그와 새 보고서로 확인하며 고정 수치를 성공 조건으로 사용하지 않는다.

GitHub Actions가 검사하는 것은 checkout된 커밋이다(PR은 기본 checkout 동작에 따른 merge ref). 로컬 미커밋·미추적 파일은 업로드되지 않는다. 현재 TS 전환의 package/lock/index 변경, 기존 JSX 삭제 및 TS/TSX·tsconfig·Vite 설정 추가를 함께 커밋해야 Frontend가 현재 구조로 실행된다. AGENTS·Skills·verify.ps1·하네스 문서도 현재 미커밋 상태이므로 공유할 하네스 구성과 새 CI 두 파일을 사용자가 검토하여 포함해야 한다. 이 작업은 stage·stash·commit·push를 수행하지 않는다.

## 실행 환경과 준비·검증 분리

모든 Job은 GitHub-hosted `ubuntu-24.04`, Bash를 사용한다. 서로의 결과를 기다리지 않는다.

| Job | 환경 준비(네트워크 다운로드 허용) | 실제 검증 |
| --- | --- | --- |
| Backend | Temurin Java 17, Wrapper 검증·Gradle 캐시 복원, Docker Engine 확인, 명시적 이미지 pull, Gradle 의존성 준비 | Gradle offline test 및 새 JUnit XML 검사 |
| Frontend | Node 22, npm 다운로드 캐시 복원, lock 기반 npm ci | typecheck, test, build 각각 별도 Step |
| Python | Python 3.11 설치/선택 | stdlib unittest 두 모듈, pip 설치 없음 |

Frontend 잠금 Vite 7의 Node 요구사항과 Vitest 4를 고려해 Node 22를 선택했다. 준비 단계의 설치는 일회성 CI runner에 한정된다. 로컬 Verification Skill의 자동 설치 금지 정책을 변경하지 않는다. 환경 준비 실패 뒤 검증을 PASS로 표시하지 않는다.

### Backend

`backend/gradlew`는 Git에 실행 비트가 설정되어 있다. Wrapper 8.14.3의 distribution checksum은 기존 properties를 사용한다. 별도 시스템 Gradle을 설치하지 않는다.

현재 build.gradle에는 의존성 전용 준비 태스크가 없다. workflow는 runner 임시 디렉터리에 작은 init script를 작성하여 `compileClasspath`, `runtimeClasspath`, `testCompileClasspath`, `testRuntimeClasspath`를 명시적으로 resolve하는 `ciPrepareTestDependencies`를 등록한다. Wrapper·플러그인과 현재 앱/테스트의 컴파일·런타임 의존성을 온라인으로 준비한다. compile-only 의존성은 런타임 classpath만으로 준비되지 않을 수 있다. 단순 dependency 보고 태스크의 출력에 FAILED가 있어도 종료 코드가 성공일 수 있는 문제를 피하고, 실제 resolve 예외는 준비 실패로 전달한다. 준비 중 컴파일·테스트·개발 DB migration을 실행하지 않는다.

[settings.gradle](../../backend/settings.gradle)의 `pluginManagement.repositories`는 Maven Central, Gradle Plugin Portal 순서로 플러그인을 해석한다. build.gradle의 프로젝트 의존성 저장소와 플러그인 저장소는 별개다. Plugin Portal만 경유해 플러그인의 일반 라이브러리를 찾는 경로에 의존하지 않도록 Maven Central을 명시했으며, 다운로드 실패를 무시하거나 플러그인 버전을 변경하지 않는다. 근거: [Gradle 플러그인 저장소](https://docs.gradle.org/current/userguide/plugins_intermediate.html), [Plugin Portal 미러링 안내](https://plugins.gradle.org/docs/mirroring).

```bash
# backend/ 기준. init script 내용은 workflow의 준비 Step 참조.
./gradlew --no-daemon -Porg.gradle.java.installations.auto-download=false --init-script "$RUNNER_TEMP/sedanji-ci.init.gradle" ciPrepareTestDependencies

# 준비 성공 후 실제 검증
./gradlew --offline --no-daemon -Porg.gradle.java.installations.auto-download=false --rerun-tasks test
```

캐시가 비어 있어도 온라인 준비가 필요한 의존성을 받아야 한다. 현재 단일 Java Gradle 프로젝트의 컴파일·런타임 classpath에 맞춘 준비다. 향후 별도 configuration·플러그인·annotation processor 의존성이 생겨 offline 실행이 실패하면 준비/검증 로그로 원인을 보고한다. 온라인 테스트 재시도, offline 제거, 테스트 제외 또는 실패 무시는 하지 않는다.

최초 실행에서 PR #8은 `testCompileClasspath`의 `org.apiguardian:apiguardian-api:1.1.2` 캐시 누락으로 compileTestJava가 실패했고 JUnit XML이 생성되지 않았다. main의 CI #2는 Plugin Portal을 통한 Spring Boot 플러그인 전이 의존성 조회 실패로 준비 단계에서 중단되어 테스트가 미실행됐다. 전자는 준비 대상 누락이 확인됐고, 후자는 해당 실행에서의 해석 실패가 확인됐으며 registry/redirect 장애 여부까지 확정하지 않는다. 위 수정의 정적 검토와 실제 CI 재실행 성공은 구분한다.

테스트 시작 시각을 기록하고 `backend/build/test-results/test/TEST-*.xml` 중 그 이후 수정된 XML만 읽는다. `--rerun-tasks`로 테스트 재실행을 요구한다. 보고서 부재·XML 파싱 오류·누락/음수 카운트·테스트 0건·실패·오류·건너뜀·Gradle 비정상 결과는 실패다. Gradle 실패 후에도 보고서 검사는 실행되며 일부 보고서가 정상이어도 원래 Step 실패를 지우지 않는다. compile/environment 실패로 XML이 없으면 assertion 실패라고 단정하지 않고 로그를 확인한다.

JUnit XML과 HTML만 artifact로 7일 보관한다. artifact 부재 경고를 생략하는 설정은 보고서 검증을 대신하지 않는다. 보고서 검사 Step이 부재/0건을 비정상 종료시킨다. 워크스페이스·환경변수·캐시 전체를 업로드하지 않는다.

### Docker / Testcontainers

GitHub-hosted runner의 기존 Linux Docker Engine에 접근 가능한지 확인한다. daemon을 자동 시작하거나 Docker Compose·개발 PostgreSQL을 실행하지 않는다. 이미지 준비는 아래 두 명령으로 명시한다.

```bash
docker pull postgres:17-alpine
docker pull testcontainers/ryuk:0.14.0
```

현재 Backend 통합 테스트의 PostgreSQL 태그와 Testcontainers BOM 2.0.5의 Ryuk 기본 이미지를 기준으로 했다. 테스트 실행 시 Testcontainers가 만드는 임시 DB와 그 DB의 Flyway 적용만 허용한다. Ryuk을 비활성화하지 않는다. Engine 또는 pull 실패는 준비 실패이며 Backend 테스트는 NOT RUN, Job은 실패한다. 다른 Job은 계속 실행한다.

Docker layer를 Actions 캐시에 저장하지 않는다. 새 runner마다 준비 단계에서 명시적으로 pull하며 registry 장애·rate limit도 성공으로 처리하지 않는다. 이미지 태그는 upstream에서 바뀔 수 있어 digest 고정 수준의 재현성은 아니다. 향후 Testcontainers/테스트 이미지 변경 시 준비 목록을 함께 검토한다.

로컬 verify.ps1은 시작 검사 활성화 시 `alpine:3.17`까지 요구하는 사전검사가 있다. Testcontainers 2.0.5의 현재 기본 client 초기화는 Docker version을 확인하며, tiny Alpine은 별도 lazy 실행 경로에서 사용된다. 현재 테스트 기준 CI 준비 목록에 이를 무조건 복제하지 않았다. 로컬 스크립트는 수정하지 않는다. 참고: [Testcontainers 2.0.5 DockerClientFactory](https://github.com/testcontainers/testcontainers-java/blob/2.0.5/core/src/main/java/org/testcontainers/DockerClientFactory.java), [RyukContainer](https://github.com/testcontainers/testcontainers-java/blob/2.0.5/core/src/main/java/org/testcontainers/containers/RyukContainer.java).

Gradle `--offline`은 Maven/Gradle 의존성 해석 옵션이며 Docker 다운로드나 네트워크 전체 차단이 아니다. 현재 테스트의 외부 연동 mock과 준비 목록을 근거로 실행한다. 네트워크 격리·새 테스트의 외부 호출 방지까지 보장한다고 표현하지 않는다.

### Frontend

```bash
# frontend/ 기준
npm ci --no-audit --no-fund
npm run typecheck --offline
npm run test --offline
npm run build --offline
```

npm ci는 package-lock.json을 사용하며 불일치하면 실패한다. 캐시는 npm 다운로드 콘텐츠이고 node_modules를 재사용하지 않으므로 cold cache도 설치로 준비한다. 검증 Step에는 `!cancelled() && steps.install.outcome == 'success'` 조건을 사용한다. 설치 성공 뒤 typecheck 또는 test 실패가 있어도 나머지 검증을 실행하며 `continue-on-error`로 실패를 지우지 않는다. 설치/Node 준비 실패 시 검증은 NOT RUN이다. 취소 시 추가 검증은 실행하지 않는다.

기존 build 스크립트는 `npm run typecheck && vite build`이므로 build Step에서도 타입 검사가 반복되며 타입 오류가 있으면 Vite는 미실행이다. 기존 스크립트를 변경하거나 이를 우회하지 않는다. 테스트는 Vitest·jsdom의 모의 API/지도 SDK를 사용하고 실제 지도 SDK·외부 API를 호출하는 브라우저 E2E를 실행하지 않는다.

### Python

```bash
# 저장소 루트 기준
python -B -m unittest -v test_youtube_poc test_kakao_local_poc
```

두 테스트와 그 로컬 import는 stdlib만 필요하며 HTTP 호출·키 접근은 mock 처리된다. pip·requirements 설치는 없다. unittest 실패는 비정상 종료로 Job에 전달된다. 실제 외부 API를 호출하는 PoC CLI, validate_additional_videos.py, smoke-api.py, check-backend-state.py는 실행하지 않는다.

## 실패와 미실행 보고

| 상황 | 보고와 결과 |
| --- | --- |
| setup·다운로드·Docker 접근·의존성 준비 실패 | 준비 실패; 뒤 검증 NOT RUN; 해당 Job 실패 |
| 실제 검증 명령 실패 | FAIL; 원래 Step/Job 실패 유지 |
| Backend XML 실패/오류 | FAIL; 실제 카운트와 원본 로그 확인 |
| Backend XML 건너뜀, 0건, 부재 또는 오래된 보고서만 존재 | 검증 미충족 FAIL; 건너뜀/미실행 사유 별도 확인 |
| 사용자/동시성 취소 | cancelled/NOT RUN; PASS로 해석하지 않음 |
| 실행 성공 | 이번 명령 결과에 한해 PASS |

각 Job의 Summary는 준비와 검증의 실제 Step outcome을 구분한다. `success=PASS, failure=FAIL, skipped/empty=NOT RUN`이며 Backend의 테스트 성공은 JUnit guard까지 성공해야 한다. 준비 실패도 설정 오류·소스의 의존성 선언 문제일 수 있으므로 모두 인프라 장애라고 단정하지 않는다. 테스트 실행 중 환경 오류와 assertion 실패는 Gradle 로그/JUnit을 확인해 구분한다. Frontend build 성공 여부가 별도 typecheck/test 실패를 덮지 않는다. GitHub에서 Job마다 종료 상태를 확인하며 세 Job 중 하나라도 실패/취소/미실행이면 전체 통과로 보고하지 않는다.

## 캐시·권한·공급망

- workflow 권한은 `contents: read`만 부여한다. checkout은 `persist-credentials: false`다. secrets 입력, 배포, PR 쓰기, OIDC, dependency graph 제출, build scan 전송은 없다.
- `pull_request_target`을 사용하지 않는다. 신뢰하지 않는 PR 코드도 실행되므로 fork PR 제한·승인 정책과 저장소 Actions 설정을 확인한다. 운영 secrets나 개발/운영 DB 접속 설정을 CI에 추가하지 않는다.
- Gradle 액션의 `cache-provider: basic`은 GitHub Actions 캐시 기반이다. PR은 read-only, main Push는 쓰기 가능하다. setup-java의 중복 Gradle 캐시는 사용하지 않는다. npm은 lockfile 경로로 캐시 키를 구분한다.
- 캐시 miss는 설치/resolve로 보완한다. 캐시 복원·준비·검증 오류를 성공으로 바꾸는 fallback은 없다. 캐시는 성능 최적화이며 검증 결과의 근거가 아니다.
- 공식 GitHub 및 Gradle 조직 액션만 전체 40자리 commit SHA로 고정했다. 2026-11-03 GitHub 공개 API로 릴리스 태그를 commit까지 해석하여 해당 저장소 SHA와 대조했다. SHA 고정은 코드 불변 참조이며 안전성 감사 완료를 뜻하지 않는다.
- 액션의 하위 의존성·runner 이미지·Java/Node/Python patch·Docker 태그·Gradle artifact 전체를 digest로 잠근 구성은 아니다. 버전 갱신은 공식 release/tag/commit을 다시 확인해 검토한다. 캐시·artifact에는 비밀정보를 저장하지 않는다.

| 공식 액션 | 확인 릴리스 | 고정 commit 및 출처 |
| --- | --- | --- |
| actions/checkout | [v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1) | [`3d3c42e5aac5ba805825da76410c181273ba90b1`](https://github.com/actions/checkout/commit/3d3c42e5aac5ba805825da76410c181273ba90b1) |
| actions/setup-java | [v6.1.0](https://github.com/actions/setup-java/releases/tag/v6.1.0) | [`b843ebf9b913146b38da2eaa7aeddf76074ae1ad`](https://github.com/actions/setup-java/commit/b843ebf9b913146b38da2eaa7aeddf76074ae1ad) |
| actions/setup-node | [v7.1.0](https://github.com/actions/setup-node/releases/tag/v7.1.0) | [`949feb2413d6458794dcd2491c4babbbce0c15c1`](https://github.com/actions/setup-node/commit/949feb2413d6458794dcd2491c4babbbce0c15c1) |
| actions/setup-python | [v7.1.0](https://github.com/actions/setup-python/releases/tag/v7.1.0) | [`6d4fd22b20465ccc0f7354476588e8df7c9ca0e0`](https://github.com/actions/setup-python/commit/6d4fd22b20465ccc0f7354476588e8df7c9ca0e0) |
| gradle/actions/setup-gradle | [v6.4.1](https://github.com/gradle/actions/releases/tag/v6.4.1) | [`8da46b539cd8fe6ab27ca75128919a18d757604c`](https://github.com/gradle/actions/commit/8da46b539cd8fe6ab27ca75128919a18d757604c) |
| actions/upload-artifact | [v7.0.2](https://github.com/actions/upload-artifact/releases/tag/v7.0.2) | [`cf430e030ddbb5b0abf93d22962f4752f3646cd9`](https://github.com/actions/upload-artifact/commit/cf430e030ddbb5b0abf93d22962f4752f3646cd9) |

입력은 고정 commit의 action.yml을 확인했다. [Gradle setup-gradle](https://github.com/gradle/actions/blob/8da46b539cd8fe6ab27ca75128919a18d757604c/setup-gradle/action.yml)의 wrapper 검증·basic 캐시를 사용한다. 참고 정책: [GitHub Actions 보안](https://docs.github.com/en/actions/reference/security/secure-use). 액션 Node 런타임을 지원하는 최신 GitHub-hosted runner가 필요하며 self-hosted runner 지원은 이번 범위에 없다.

## 로컬 Windows 검증과의 관계

기준: [verify.ps1](../../scripts/verify.ps1), [검증 안내](verification.md), [Verification Skill](../../.agents/skills/sedanji-verification/SKILL.md).

| 항목 | 로컬 Windows | CI Linux |
| --- | --- | --- |
| 진입점 | Windows PowerShell 5.1 verify.ps1 | 각 Job의 Bash 명령 |
| Backend | gradlew.bat, .tools/gradle-home offline 캐시, Windows Docker 설정 | 실행 비트가 있는 ./gradlew, runner Gradle home, Linux Engine |
| 환경 준비 | 의존성·이미지 자동 설치 금지, 없으면 NOT RUN | 일회성 runner의 명시적 설치·pull·resolve |
| 검증 내용 | offline Gradle test, npm typecheck/test/build, 동일 unittest 모듈 | 같은 태스크/스크립트/모듈; Backend 새 XML·0건/건너뜀 검사 |
| 실패 전파 | 외부 명령 종료 코드 보존, 전체 비정상 종료 | Step 비정상 종료 및 각 Job 실패; Frontend 후속 독립 Step 계속 |
| 선택 범위 | Skill에서 변경 영향 기반 선택 | PR/main Push에서 매번 세 Job |
| 보고 | PASS/FAIL/NOT RUN 및 전체 종료 코드 | Step/Job 결과·Summary·Backend XML artifact |

Windows 전용 executable 탐색, named pipe, 캐시 사전검사 때문에 verify.ps1을 Linux pwsh로 직접 호출하지 않는다. 검증 태스크를 영역별로 실행하며 기존 AGENTS·Skills·verify.ps1은 그대로 유지한다. 개발 DB migration·실제 외부 호출·Docker 시작·로컬 자동 설치 금지는 유지한다. API 계약 변경은 Backend+Frontend, 공통 검증 변경은 전체 검증이라는 기존 선택 규칙과 CI 전체 실행은 충돌하지 않는다. 이번 구현의 실행 금지에 따라 애플리케이션 검증은 NOT RUN이다.

## 첫 GitHub 실행 전과 실행 후 확인

1. TS 전환 파일/삭제/lock/config 및 공유할 하네스 파일이 빠짐없이 커밋에 들어 있는지 검토한다. 신규 CI 두 파일만으로 로컬 미추적 소스가 전송되지는 않는다.
2. GitHub 기본 브랜치가 main인지, Actions 활성화·공식 액션 허용·fork 승인·runner/캐시/artifact 사용 정책을 확인한다. branch protection의 required checks는 Backend·Frontend·Python을 실제 첫 실행 후 별도로 설정한다.
3. secrets나 실제 DB 접속값을 workflow에 주입하지 않는다. 현재 테스트의 mock 유지와 Testcontainers 이미지 버전 변경 여부를 확인한다.
4. 첫 PR에서 cold cache 준비, Docker image pull, offline Gradle test, 새 XML/건수/건너뜀, Frontend 세 Step, Python 모듈을 확인한다. 현재 정적 검토만으로 Linux 실행 성공을 단정하지 않는다.
5. 준비 실패와 실제 테스트 실패를 구분하여 기록한다. XML 테스트 공백 및 검증 범위 한계는 통과 이력과 별도로 보고한다. 운영 데이터 migration 안전성·실제 브라우저 세션·지도 동작은 이 CI로 입증되지 않는다.
