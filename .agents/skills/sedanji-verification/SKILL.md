---
name: sedanji-verification
description: Sedanji 코드 변경 후 또는 사용자가 검증을 요청했을 때 Git 변경 범위와 요청 목적을 분석해 필요한 Backend·Frontend·Python 검증을 선택하고 기존 verify.ps1로 실행·보고한다. 분석 전용 요청에서는 실행하지 않으며 변경이 없다는 이유만으로 전체 검증을 시작하지 않는다.
---

# Sedanji 검증 선택과 보고

## 적용 조건과 기준

- Sedanji 코드·설정 변경 후 검증을 수행하거나, 사용자가 검증 선택·실행·전체 회귀 검증을 요청할 때 적용한다. 분석·계획만 요청받았다면 선택 근거만 제시하고 실행하지 않는다.
- 이 파일은 검증 절차를 제공한다. 파일 생성만으로 모든 Codex 작업에서 자동 선택·실행된다고 가정하지 않는다. 실제 적용 여부는 호스트의 Skill 발견·선택과 현재 요청에 달려 있다. 명시적으로 `$sedanji-verification`을 호출할 수도 있다.
- 먼저 저장소 루트와 [루트 AGENTS.md](../../../AGENTS.md)를 확인한다. Backend·Frontend를 검토할 때는 [Backend 지침](../../../backend/AGENTS.md), [Frontend 지침](../../../frontend/AGENTS.md)도 읽는다.
- 실행 조건·부작용·종료 코드의 기준은 현재 [verify.ps1](../../../scripts/verify.ps1)과 [검증 문서](../../../docs/harness/verification.md)다. 현재 파일과 최신 사용자 합의를 확인하며 과거 기록을 이번 실행 결과로 사용하지 않는다.
- 이 Skill은 실행 권한을 확대하지 않는다. 사용자가 금지하거나 범위를 제한한 검사는 실행하지 않고 이유를 보고한다. 이미 승인된 범위는 반복 승인받지 않는다.

## 1. 변경 범위 확인

저장소 루트에서 아래 세 집합을 각각 확보하고 합집합을 검토한다. 한 명령이 실패하면 변경이 없는 것으로 처리하지 않는다.

```powershell
git status --porcelain=v1 --untracked-files=all -z
git diff --cached --name-status --no-renames -z
git diff --name-status --no-renames -z
git ls-files --others --exclude-standard -z
```

- `-z` 출력은 NUL로 해석한다. 공백·한글·특수문자가 있는 경로를 줄이나 공백으로 단순 분리하지 않는다.
- staged, unstaged, untracked를 모두 포함한다. `--no-renames`의 삭제·추가 경로를 모두 고려해 영역 간 이동도 놓치지 않는다. 삭제된 파일도 영향 분석 대상이다.
- 경로만으로 끝내지 않고 관련 diff와 untracked 파일 내용을 읽어 계약·의존 관계를 판단한다. staged 내용과 작업 트리가 다르면 구분해서 확인한다. 비밀정보·환경변수 실제 값은 출력하지 않는다.
- 기존 사용자 변경과 이번 작업 변경을 구분해 보고하되, 기존 변경이 현재 작업의 검증에 영향을 주면 선택 범위에 포함한다. 임의 stage·stash·reset·커밋·푸시는 하지 않는다.
- 검증은 현재 작업 트리에서 실행된다. staged 스냅샷만 별도로 검증했다고 표현하지 않는다. 실행 후 관련 파일을 다시 수정했다면 영향을 받는 검증만 재판단한다.

## 2. 검증 선택

사용자의 명시적 검증 목적과 실행 제한을 먼저 적용한 뒤, 아래 규칙의 합집합을 선택한다. 경로 예시는 현재 저장소 기준이며 새 경로는 실제 역할로 판단한다.

| 변경·목적 | 선택 | 판단 근거 |
| --- | --- | --- |
| Backend 소스·테스트·영역 설정, Gradle·Wrapper·Flyway 변경 | `-Backend` | `backend/`의 실제 실행·저장·테스트 영향 |
| Frontend 소스·테스트·package/lock·TS/Vite 설정·화면 자산 변경 | `-Frontend` | `frontend/`의 타입·화면·빌드 영향 |
| Python PoC·관련 단위 테스트 변경 | `-Python` | `youtube_poc.py`, `kakao_local_poc.py`, `test_youtube_poc.py`, `test_kakao_local_poc.py` 등 |
| API 계약의 의미 변경 | `-Backend -Frontend` | 경로·메서드·요청 검증·응답 필드·null·상태 코드·오류·인증·CSRF·공개 조건·소비 코드 영향 |
| 공통 실행 설정 또는 검증 스크립트 변경 | 전체 | 예: `compose.yaml`, 공통 환경변수 선언·도구 설정, `.gitignore`, `scripts/verify.ps1`, `scripts/start-backend.ps1` |
| 여러 영역의 변경 | 영역 합집합 | 같은 검사를 불필요하게 중복 실행하지 않음 |
| 일반 문서·주석만 변경 | 정적 검토 기본 | 링크·명령·구현 사실·지침 충돌·실행 영향 확인 후 런타임 검증 필요성 판단 |
| 영향이 불명확하거나 Git 범위를 확인하지 못함 | 전체 검증 권장 | 불확실성을 보고하고, 실행이 허용된 범위에서만 수행 |
| 변경 없이 전체 회귀 검증을 명시적으로 요청 | 전체 | 변경 유무보다 명시적 검증 목적을 따름 |
| 변경 없이 특정 영역 검증을 요청 | 요청한 영역 | 미선택 영역의 사유를 보고 |
| 변경 없이 분석·계획·상태 설명만 요청 | 기본적으로 실행하지 않음 | 실행 검증 필요성이 있다면 이유와 제안 범위를 설명 |

- API 계약 변경은 파일 위치와 무관하게 양쪽 검증을 선택한다. Backend Controller·Models·서비스·보안·공개 조회와 Frontend `src/api.ts`·타입·호출 화면·입력 조건·갱신·테스트를 대조한다. 한쪽 수정이 불필요해도 근거를 보고한다.
- 문서 안의 실질적인 API 계약 변경은 일반 문서 예외보다 API 규칙을 우선한다. 단, 예정 계약을 문서화한 것을 기능 구현으로 간주하지 않으며 현재 테스트 통과가 예정 기능을 입증한다고 보고하지 않는다.
- AGENTS.md·Skill·검증 안내 문서만 변경했다면 정적 검토와 대표 사례 평가를 기본으로 한다. 실행 명령·권한·검증 정책에 영향을 주면 전체 실행 검증 필요성을 판단해 근거를 설명한다. 실제 검증 스크립트·공통 실행 설정 변경에는 전체 규칙을 적용한다.
- 외부 호출용 Python 스크립트가 바뀌어도 그 스크립트 자체를 실행하지 않는다. 관련 Python 단위 검증을 선택하고, 기존 두 테스트 모듈이 변경 기능을 검증하는지와 검증 공백을 별도로 보고한다.
- 작업 트리가 깨끗하다는 사실은 검증 통과도, 전체 검증 필요도 의미하지 않는다. 반대로 변경이 없더라도 명시적 전체 회귀 요청에는 전체를 실행한다. 실행 금지나 환경 부재가 있으면 해당 항목은 `NOT RUN`이다.

## 3. 기존 진입점으로 실행

실행 전 선택 영역·이유를 간단히 알린다. Windows PowerShell 5.1 호환 진입점을 사용하며 임의의 대체 테스트·설치 명령으로 우회하지 않는다.

```powershell
# Git 저장소 안에서 루트를 찾는다. 실패 시 실행하지 않고 사유를 보고한다.
$repoRoot = git rev-parse --show-toplevel
$gitExit = $LASTEXITCODE
if ($gitExit -ne 0) { throw 'Repository root could not be determined.' }
$verifyPath = Join-Path $repoRoot 'scripts/verify.ps1'

# 선택한 예시 하나를 사용한다.
powershell.exe -NoProfile -File $verifyPath -Backend
$verificationExit = $LASTEXITCODE

# API 계약 변경: Backend와 Frontend
powershell.exe -NoProfile -File $verifyPath -Backend -Frontend
$verificationExit = $LASTEXITCODE

# Frontend만: -Frontend / Python만: -Python / 조합: -Frontend -Python
# 전체는 영역 스위치를 생략한다.
powershell.exe -NoProfile -File $verifyPath
$verificationExit = $LASTEXITCODE
```

위 명령은 선택 예시이며 전부 순서대로 실행하라는 지시가 아니다. 루트를 찾은 뒤 절대 경로를 사용해 현재 디렉터리에 따른 실패를 피한다. 실행 직후 종료 코드를 보존하고 원래 검사 결과·요약을 확인한다.

- 개발 DB 저장·migration, 실제 YouTube·Kakao 등 외부 서비스 호출, Docker/Compose/서버 자동 시작, 의존성·도구·이미지 자동 설치·다운로드를 하지 않는다. 환경이 없으면 `NOT RUN`과 사유를 보고한다.
- `scripts/smoke-api.py`, `scripts/check-backend-state.py`, `validate_additional_videos.py`, PoC CLI, `start-backend.ps1` 기본 실행·`-Migrate`, Gradle `migrateDevelopmentDatabase`를 검증 대신 실행하지 않는다.
- 기존 Backend 검증의 Testcontainers 임시 PostgreSQL·테스트 DB Flyway와 도구의 로컬 보고서·캐시·Frontend dist 생성은 문서화된 검증 부작용이다. 이를 개발 DB 변경이나 실제 외부 API 검증과 혼동하지 않는다.
- Docker·캐시·권한·의존성 문제나 사전검사 한계가 나오면 사실을 보고한다. 설정을 끄거나 `verify.ps1`을 고쳐 성공시키지 않는다. 현재 Alpine 사전검사 같은 알려진 제한도 별도 유지보수 대상으로 남긴다.
- 실패 뒤 독립적인 검사를 계속하는 것은 기존 wrapper에 맡긴다. 비정상 종료를 무시하거나 필요하지 않은 전체 재실행을 반복하지 않는다.

## 4. 결과 보고

다음 정보를 결과에 포함한다. 이번에 실행하지 않은 검사를 과거 통과 수치로 채우지 않는다.

```text
변경 범위: staged / unstaged / untracked 및 기존 변경과 이번 변경 구분
선택 근거: 관련 파일·계약·공통 영향, 정적 검토 또는 실행 범위
실행 명령: 실제 호출한 명령
전체 종료 코드: 실제 관측한 값 / 실행하지 않았다면 해당 없음

| 검사 | 상태 | 종료 코드 | 이번 실행 근거 또는 미실행 사유 |
| Backend | PASS / FAIL / NOT RUN | 실제 값 또는 해당 없음 | ... |
| Frontend 타입 검사·테스트·빌드 | 각 검사별 상태 | 실제 값 또는 해당 없음 | ... |
| Python | PASS / FAIL / NOT RUN | 실제 값 또는 해당 없음 | ... |

정적 검토: 수행 내용과 결과, git diff --check 결과
검증 한계: 환경 문제, 미검증 동작, 테스트 범위 공백
```

- `PASS`: 이번 명령·wrapper 요약과 종료 코드로 확인된 성공만 사용한다. 테스트 수는 실제 출력·이번 보고서에 있을 때만 적는다.
- `FAIL`: 실제 타입 검사·빌드·테스트·명령 실패다. assertion 실패인지 다른 명령 실패인지 근거에 따라 구분한다.
- `NOT RUN`: 미선택, 사용자 실행 제한, 문서 정적 검토만 수행, 환경 부재 등을 구체적으로 적는다. 환경 부재에 따른 미실행은 전체 성공이 아니다. 선택하지 않은 영역도 통과했다고 표현하지 않는다.
- wrapper 분류와 실제 출력이 불일치하거나 원인이 불명확하면 원본 상태·종료 코드를 보존하고 해석의 한계를 적는다. 실패를 통과로 바꾸거나 환경 문제의 원인을 추측해 확정하지 않는다.
- 정적 검토 성공을 애플리케이션 테스트 통과로 표현하지 않는다. 코드 수정 작업은 `git diff --check`와 신규 미추적 파일의 공백·참조도 확인하고, 그 검사를 수행하지 못했다면 사유를 보고한다.
