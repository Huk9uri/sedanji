# 검증 자동화

`scripts/verify.ps1`은 기존 검사를 실행하고 통과·실패·미실행을 구분하는 진입점이다. 애플리케이션, 기존 테스트 설정, Flyway, AGENTS.md를 변경하지 않는다. Windows PowerShell 5.1 이상을 사용한다.

## 실행

저장소 루트에서 실행한다. 스크립트 내부 경로는 `$PSScriptRoot`로 계산하므로 다른 작업 디렉터리에서 절대 경로로 호출해도 된다.

```powershell
# 기본값: Backend, Frontend, Python 전체
powershell.exe -NoProfile -File .\scripts\verify.ps1

# 영역별 또는 조합
powershell.exe -NoProfile -File .\scripts\verify.ps1 -Backend
powershell.exe -NoProfile -File .\scripts\verify.ps1 -Frontend
powershell.exe -NoProfile -File .\scripts\verify.ps1 -Python
powershell.exe -NoProfile -File .\scripts\verify.ps1 -Frontend -Python

# 명시적 선택: 실제 React/Backend + 실행별 임시 PostgreSQL + Chromium
# 패키지/브라우저/이미지 준비는 검사와 별도이며 검사에서 자동 설치하지 않음
powershell.exe -NoProfile -File .\scripts\verify.ps1 -E2E

# 호출 직후 보존한다. 후속 명령의 종료 코드로 덮어쓰지 않는다.
$verificationExit = $LASTEXITCODE
```

기존 실행 정책이 스크립트를 차단하면 조직의 실행 정책을 따른다. 이 스크립트는 실행 정책을 변경하지 않는다. 호출한 프로세스가 종료 코드를 받으므로 대화형 셸에서는 별도 `powershell.exe -File` 프로세스 사용을 권장한다.

## 검사와 전제 조건

| 항목 | 실제 명령·설정 근거 | 전제 조건 |
| --- | --- | --- |
| Backend/test | `backend/gradlew.bat ... test`, `backend/build.gradle` | Java 17 toolchain, 캐시된 Gradle Wrapper·의존성, 접근 가능한 Linux Docker Engine·기존 이미지 |
| Frontend/typecheck | `frontend/package.json`: `npm run typecheck` → `tsc --noEmit` | 기존 Node.js·npm·정상 node_modules |
| Frontend/test | `npm run test` → `vitest run` | 위 조건 및 jsdom. `frontend/vite.config.ts`, `src/test-setup.ts` 사용 |
| Frontend/build | `npm run build` → `npm run typecheck && vite build` | 위 조건. 이 명령에도 타입 검사가 포함됨 |
| Python/test | 루트 `python.exe -B -m unittest -v test_youtube_poc test_kakao_local_poc` | Python 3.8 이상. 표준 라이브러리와 명시한 두 테스트 모듈 |
| E2E (명시 선택만) | `node scripts/run-e2e.mjs`, `frontend/tsconfig.e2e.json` | 지원 Node 22/24/26, 설치된 Playwright 1.65.0/Chromium, Java 17/캐시 Gradle·의존성, Linux Docker/준비된 이미지, 빈 전용 포트 |

`-E2E`만 선택하면 기존 세 영역을 선택하지 않는다. 기본값은 기존 Backend·Frontend·Python이며 E2E를 자동 포함하지 않는다. E2E는 테스트 전용 Backend·Vite·새 임시 DB를 시작하고 소유 자원만 정리한다. 실제 `.env`·개발 DB·외부 키·실제 지도 SDK를 사용하지 않는다. 자세한 준비·정제 보고서·실패/정리 이력·M1 판정은 [#21 검증 기록](../ISSUE_21_VERIFICATION.md)을 따른다.

Frontend는 독립적인 실패를 보고하기 위해 타입 검사·테스트·빌드를 각각 실행한다. 기존 build 스크립트를 그대로 사용하므로 성공 경로에서 타입 검사가 한 번 더 수행된다. 현재 Vite 7의 Node 범위는 20.19 이상인 20.x 또는 22.12 이상의 22.x 및 이후 주 버전이며, 실제 의존성 요구사항이 바뀌면 사전 점검도 함께 갱신한다. `npm ls --all --offline --json`으로 설치 트리를 점검하고 npm 실행은 offline 모드로 한다. 설치 명령과 npx를 사용하지 않는다.

Python은 자동 검색 범위를 넓히지 않고 두 모듈만 지정한다. 테스트의 외부 API·키 로딩은 mock으로 대체되어 실제 YouTube·Kakao 키가 필요 없다. `-B`는 이 프로세스의 새 바이트코드 생성을 억제하며 기존 `__pycache__`를 삭제하지 않는다.

### Backend: 오프라인과 Docker

- 기존 `scripts/start-backend.ps1 -Test`와 같은 `.tools/gradle-home`을 사용한다. 일반 사용자 전역 Gradle 캐시를 자동 복사하거나 다른 배포본으로 대체하지 않는다.
- Wrapper 설정에서 버전을 읽고 해당 캐시의 Gradle 실행 파일·완료 표시와 Wrapper JAR를 확인한다. 없으면 `NOT RUN`이며 Wrapper 다운로드를 시작하지 않는다.
- Gradle에는 `--offline --no-daemon -Porg.gradle.java.installations.auto-download=false --rerun-tasks test`를 전달한다. 라이브러리·JDK를 설치하지 않는다. 캐시 누락은 환경 문제로 보고한다. `--rerun-tasks`로 UP-TO-DATE를 실제 테스트 실행으로 오인하지 않는다.
- 서버·Docker Desktop·Docker Engine·Compose를 시작하지 않는다. 기존 Engine을 읽기 전용으로 확인한 뒤 테스트가 자체 임시 PostgreSQL·보조 컨테이너를 생성·정리한다. 테스트 DB에 Flyway를 적용하는 것은 허용된 테스트 부작용이다.
- Gradle 진입 전 `docker info`로 Engine 접근과 Linux 모드를 확인하고 필요한 이미지에 `docker image inspect`를 수행한다. 이미지가 없으면 pull하지 않고 `NOT RUN`이다.
- 이미지 사전 점검은 현재 Testcontainers **2.0.5**의 `postgres:17-alpine`, `testcontainers/ryuk:0.14.0`, `alpine:3.17`을 기준으로 한다. Ryuk·startup checks의 기존 비활성화 및 이미지 override를 고려한다. 버전이 바뀌면 자동으로 추측하지 않고 사전 점검 갱신을 요구한다.
- 외부에서 검사 도중 이미지·권한·Engine 상태를 바꾸면 사전 점검 이후에도 실패할 수 있다. 전용 격리·네트워크 차단 도구를 구현한 것은 아니다.

### Testcontainers 환경 설정 보존

사전 점검은 기존 환경을 덮어쓰지 않는다. `DOCKER_HOST`, Docker TLS 환경변수, `TESTCONTAINERS_*`, 사용자 홈 `.testcontainers.properties`, 프로젝트 main/test classpath의 `testcontainers.properties`를 고려한다. 설정 우선순위와 host 동작 근거는 [Testcontainers 공식 설정 문서](https://java.testcontainers.org/features/configuration/)다.

- 기본 Windows named pipe와 `NpipeSocketClientProviderStrategy`, `EnvironmentAndSystemPropertyClientProviderStrategy`를 지원한다. 현재 Docker CLI context만으로 Testcontainers 사용 가능 여부를 단정하지 않는다. host가 없으면 Testcontainers의 Windows 기본 named pipe를 점검한다.
- TLS·인증 경로 등 실제 값과 Docker 오류 원문은 사전 점검 보고에 출력하지 않는다. CLI-only `DOCKER_CONTEXT`는 점검 동안만 제외하고, property 기반 TLS 설정도 CLI 점검에만 반영한다. 원래 프로세스 환경을 `finally`에서 복구한 뒤 Gradle에 기존 Testcontainers 환경을 전달한다.
- `TESTCONTAINERS_HOST_OVERRIDE`는 노출 포트 접속용, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`는 보조 컨테이너 소켓용이다. 이를 Engine host로 오용하지 않으며 기존 값을 유지한다.
- 임의 사용자 전략, 사용자 정의 pull policy·이미지 치환, 복잡한 Java properties 인코딩처럼 이 점검이 안전하게 해석하지 못하는 설정은 성공·접속 불가로 단정하지 않는다. `NOT RUN / ENVIRONMENT`로 수동 확인을 요구한다. 의존 JAR 안의 임의 설정까지 재현하는 Testcontainers 탐색기를 구현한 것은 아니다.
- Engine 사전 점검 성공은 Java 클라이언트·Ryuk·네트워크·임시 DB 준비 성공을 보장하지 않는다. 테스트 중 인식 가능한 인프라 실패도 테스트 assertion 실패와 구분한다.

## 부작용 분류

| 명령·스크립트 | 부작용 | 자동 검증 포함 |
| --- | --- | --- |
| Backend `test` | 임시 컨테이너·DB·테스트 데이터·테스트 DB migration, Gradle 캐시·컴파일·보고서 | 전제 조건 충족 시 포함 |
| Frontend 타입 검사 | noEmit 정적 검사 | 포함 |
| Frontend 테스트 | 로컬 프로세스·Vitest/Vite 임시 파일·캐시. API·지도는 mock | 포함 |
| Frontend 빌드 | `frontend/dist` 생성·갱신, Vite 캐시. Vite가 루트 envDir에서 빌드 환경 설정을 읽음 | 포함 |
| Python 두 단위 테스트 모듈 | mock API·인메모리 처리. 새 pyc 생성 억제 | 포함 |
| `scripts/smoke-api.py` | 실행 서버 로그인·로그아웃, 실제 YouTube/Kakao 호출, 개발 DB에 영상·후보·검색 결과 저장, `results/api-smoke.json` 생성 | 제외 |
| `scripts/check-backend-state.py` | 실행 서버 로그인·로그아웃·카탈로그 조회, 출력 디렉터리·JSON 생성. 후보·관계 저장은 하지 않음 | 제외 |
| `validate_additional_videos.py` | 실제 YouTube 검색·메타데이터 호출, `results/additional_videos.json` 생성 | 제외 |
| `youtube_poc.py`, `kakao_local_poc.py` CLI | 실제 외부 API 호출. Kakao CLI는 옵션에 따라 출력 파일 생성 | 제외 |
| `scripts/start-backend.ps1` 기본 실행 | env 로드, 서버 시작·Flyway 자동 적용 | 제외 |
| `scripts/start-backend.ps1 -Migrate`, Gradle `migrateDevelopmentDatabase` | 개발 DB migration·검증 | 제외 |
| `scripts/start-backend.ps1 -Package` | env 로드, JAR 생성 | 제외 |
| `scripts/start-backend.ps1 -Test` | 기존 테스트 전용 진입점. `.env`는 로드하지 않으나 offline·종합 보고 기능은 없음 | 별도 호출하지 않고 동등한 test 태스크를 제한 옵션과 함께 사용 |

테스트·빌드는 무부작용 명령이 아니다. 허용된 로컬 산출물과 임시 테스트 DB를 개발 DB 변경과 구분한다. 검증 wrapper 자체는 `.env` 값을 읽거나 별도 결과 파일을 생성하지 않는다. `dist`에는 원래 앱 설정상 브라우저에 공개되는 `VITE_` 값이 포함될 수 있으므로 서버 비밀정보에 이 접두어를 붙이지 않는다.

## 결과와 종료 코드

각 항목과 마지막 표에서 상태, 분류, 원본 종료 코드(있을 때), 사유를 출력한다.

| 상태·분류 | 의미 | 전체 성공 여부 |
| --- | --- | --- |
| `PASS` | 선택한 명령이 0으로 완료. Backend는 새 JUnit 보고서의 실행·실패·skip도 확인 | 다른 선택 항목도 성공해야 성공 |
| `FAIL / TEST` | 테스트 명령 실패 | 실패 |
| `FAIL / TYPECHECK` | 타입 검사 실패 | 실패 |
| `FAIL / BUILD` 또는 `BUILD/COMMAND` | 빌드·컴파일·기타 명령 실패. assertion 실패로 단정하지 않음 | 실패 |
| `NOT RUN / ENVIRONMENT` | 필수 도구·의존성·Engine·이미지 부재, 식별 가능한 인프라·프로세스 권한 문제, 검증 미완료 | 실패 |
| `NOT RUN / NOT SELECTED` | 사용자가 선택하지 않은 영역 | 실패 아님 |

- 외부 프로세스 종료 직후 `$LASTEXITCODE`를 보존하며, Windows PowerShell 5.1의 native stderr를 assertion 실패나 PowerShell 예외로 오인하지 않는다.
- 첫 선택 항목 실패의 원본 비정상 종료 코드를 전체 결과로 유지한다. 원본 코드가 없는 환경 부재·검증 미완료는 1이다. 이후 성공이 이 코드를 0으로 덮어쓰지 않는다.
- 실패 후에도 독립적인 다음 항목·영역을 계속 실행한다. 필수 공통 환경이 없으면 해당 영역 항목들을 미실행으로 보고하고 다른 영역으로 진행한다.
- 기본 Backend 테스트에서 skip이 있거나 새 실행 보고서가 없으면 전체 성공으로 처리하지 않는다. 과거 XML 보고서를 이번 통과 근거로 사용하지 않는다.
- 명령이 0이 아니라고 모두 assertion 실패인 것은 아니다. 알려진 인프라 오류는 환경 문제로 표시하며, 미분류 오류는 출력·JUnit 보고서를 확인한다. npm 테스트는 원래 Vitest의 실패·빈 테스트 정책을 유지한다.
- 로그 파일·JSON 요약 파일을 자동 생성하지 않는다. 테스트 도구 자체 보고서는 원래 위치에 남는다.

## 하네스 자체 검증 방법

1. 실제 Windows PowerShell 5.1에서 구문 검사와 영역별 실행을 한다.
2. 원래 검증 함수로 종료 코드 7의 제어된 외부 프로세스를 실행하고, 뒤의 성공 검사 후에도 전체 코드가 7인지 확인한다.
3. 프로세스 내부에서만 필수 실행 파일 부재를 모의 처리하여 `NOT RUN`, 전체 코드 1, 다른 영역 계속 실행을 확인한다.
4. 모의 실패를 위해 실제 앱·테스트·도구 설치 파일을 수정·삭제하지 않는다. 이 검증은 저장소 파일을 추가하지 않고 AST/인메모리 scriptblock과 자식 프로세스로 수행할 수 있다.
5. `git diff --check`와 신규 파일의 공백·참조·변경 범위를 확인한다. 과거부터 존재한 작업 트리 변경은 이번 변경으로 보고하지 않는다.

## Phase 1-2 실행 기록

2026-11-03, Windows PowerShell **5.1.26100.9711**에서 실행했다. 다음은 이번 작업의 기록이며 다른 환경의 현재 상태를 보장하지 않는다.

| 실행 | 결과 | 프로세스 종료 코드 |
| --- | --- | --- |
| PowerShell 5.1 파서 구문 검사 | 통과 | 0 |
| `-Python` | 30개 테스트 통과. 다른 영역은 미선택 | 0 |
| `-Frontend` | 타입 검사·테스트 11개·빌드 통과. 다른 영역은 미선택 | 0 |
| `-Backend` | `NOT RUN / ENVIRONMENT`: 기존 Npipe 전략의 Engine 연결 불가 | 1 |
| 기본 전체 실행 | Backend 미실행 후에도 Frontend 타입 검사·11개 테스트·빌드와 Python 30개 테스트 통과 | 1 |
| 제어된 native 실패 | 인메모리 복사본의 타입 검사 명령만 실제 `cmd.exe /d /c exit 7`로 대체. 이후 테스트·빌드·Python은 계속 실행·통과 | 7 |
| 필수 환경 부재 모의 | 인메모리 복사본에서만 Node 탐색을 부재로 처리. Frontend `NOT RUN`, Python은 계속 실행·통과 | 1 |

실제 애플리케이션 테스트의 assertion 실패가 발생한 것은 아니다. 실패·환경 부재 모의는 원본 스크립트·애플리케이션·테스트·설치 파일을 수정하지 않았다. Backend의 실제 Testcontainers 테스트 실행, 이미지 캐시·TLS·원격 Docker 연결 분기는 이번 환경에서 실행 검증하지 못했다. Docker를 시작하거나 새 의존성·이미지를 설치하지 않았다.
