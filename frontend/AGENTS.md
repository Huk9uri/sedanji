# Frontend 개발 지침

루트 `../AGENTS.md`와 함께 적용한다. 아래 근거 경로는 별도 표시가 없으면 `frontend/` 기준이다.

## 현재 TSX 구조와 계획

- 현재 React 19 + TypeScript/TSX + Vite + 일반 CSS다. 의존성·명령은 `package.json`, 잠금 버전은 `package-lock.json`으로 확인한다.
- `src/main.tsx`가 진입점, `src/App.tsx`가 공개 지도·관리자와 page 상태 전환을 담당한다. 상태는 React hooks로 관리한다. `src/features/admin/CandidateReviewCard.tsx`는 분리된 후보 검토 기능이다.
- React Router, TanStack Query, Tailwind CSS, shadcn/ui는 기존 기본 선택·향후 도입 대상이며 현재 사용 중이 아니다. 승인 없이 추가하거나 하네스 정비와 함께 전환하지 않는다.
- 기능 중심 디렉터리를 필요한 범위에서 확장하되 app/pages/entities/shared 전체가 이미 있다고 가정하거나 일괄 재배치하지 않는다.
- 사용자 모바일·관리자 웹은 목표 제품 방향이다. 현재 공개 웹 지도를 React Native·Expo 앱으로 표현하지 않으며 SDK·내비게이션·Expo Go 호환성은 실제 모바일 파일 확인 전 미확인으로 둔다.
- 직접 등록·초안·정정·공개 제어 신규 화면은 완료되지 않았다. 직접 장소 검색·메타데이터·직접 승인 Backend API는 있으나 화면은 기존 후보 경로를 사용한다. `../docs/ADMIN_API.md`의 현재 계약과 목표 설계를 구분하며 미구현 API를 호출하거나 가짜 성공을 표시하지 않는다.

근거: `package.json`, `tsconfig.json`, `vite.config.ts`, `src/main.tsx`, `src/App.tsx`, `../docs/REQUIREMENTS.md`, `../docs/DIRECT_REGISTRATION_MIGRATION.md`.

## TypeScript 경계

- `tsconfig.json`의 strict·noEmit·isolatedModules·Bundler 해석을 유지한다. 타입 오류를 숨기기 위해 any, ts-ignore, strict 해제에 의존하지 않는다. 타입 import는 import type을 사용한다.
- `src/types.ts`는 현재 Backend Models 계약을 표현한다. ID, 문자열 날짜, 선택·null 필드, 상태·언급 유형을 서버와 대조한다. 예정 계약을 현재 타입에 섞지 않는다.
- `src/api.ts`의 제네릭은 응답 계약의 정적 선언이며 런타임 JSON 검증이 아니다. JSON 응답 조회에는 실제 반환 타입을 명시하고 빈 응답 경로는 null 처리를 유지한다.
- `src/kakao.ts`와 `src/vite-env.d.ts`는 사용 중인 SDK 표면·브라우저 전역·환경변수를 선언한다. SDK 전체나 네이티브 지도 구현이 있다고 가정하지 않는다.
- TypeScript로 옮겨도 화면·검증·API 동작을 임의 변경하지 않는다. 파일 이름만 TSX로 바꾼 것을 타입 검증 완료로 표현하지 않는다.

## API 래퍼와 양쪽 영향 확인

- 애플리케이션 API 호출은 `src/api.ts`의 `api(path, options)`를 사용한다. 현재 `/api` 접두어를 래퍼가 붙이므로 호출 경로에 중복 추가하지 않는다.
- 현재 래퍼는 GET 이외 요청 전 `/api/csrf`를 조회해 응답의 headerName에 token을 넣는다. 일반 본문은 JSON, `form: true`는 URLSearchParams로 전송한다.
- 204와 빈 201 등 빈 성공 응답은 null로 처리한다. 모든 성공 응답이 JSON이라고 가정하지 않는다. 서버 message와 본문 없는 401·403의 기본 오류 안내를 유지한다.
- 현재 래퍼는 사용자 지정 헤더를 받지 않아 직접 승인에 필요한 Idempotency-Key를 전달할 수 없고, 오류 code·existingMentionId도 Error에 보존하지 않는다. 신규 화면 연결 작업에서 계약·재시도·오류 UI와 함께 구현할 범위이며 이번 문서 정리로 지원됐다고 가정하지 않는다.
- API 변경 시 `../backend/src/main/java/kr/sedanji/ApiController.java`, `../backend/src/main/java/kr/sedanji/Models.java`, `../backend/src/main/java/kr/sedanji/ApiErrors.java`, `../backend/src/main/java/kr/sedanji/SecurityConfig.java`, 관련 서비스·테스트를 함께 확인한다. 경로·메서드·필드·타입·null·상태 코드·오류·인증·CSRF·입력 조건·목록 갱신의 양쪽 영향을 보고한다.
- `vite.config.ts`는 루트 환경변수를 읽고 개발 `/api`를 로컬 백엔드로 프록시한다. 이 설정을 배포·모바일 API 주소나 교차 출처 인증 정책으로 간주하지 않는다.
- 브라우저용 `VITE_` 변수는 공개될 수 있다. 서버 REST 키·YouTube 키·DB·관리자 비밀번호에 이 접두어를 붙이거나 번들에 포함하지 않는다.

근거: `src/api.ts`, `src/api.test.ts`, `vite.config.ts`, `../README.md`.

## 인증과 승인 화면

- 로그인은 세션 기반이며 `api('/login', { method: 'POST', form: true, ... })`와 `/session`으로 처리한다. 로그아웃도 래퍼의 POST·CSRF 흐름을 유지한다. JWT·localStorage 토큰 저장을 임의 도입하지 않는다.
- 관리자 UI 표시 여부는 사용성 처리이며 실제 권한은 서버가 검증한다. 로그인·권한·CSRF 오류를 성공 상태로 덮어쓰지 않는다.
- 장소 MATCHED와 관계 승인을 분리해 안내한다. 서버 검색 후보에서 지점·주소를 확인하고 셰프·유형·관계 근거를 명시적으로 선택·입력하게 한다. 검색 실패·빈 결과·불완전 검색을 구분한다.
- 현재 `CandidateReviewCard`는 메모를 필수로 요구하지만 서버와 V3 정책은 선택이다. 기존 화면의 제약을 새로운 비즈니스 규칙으로 일반화하지 않는다. 수정 요청 시 서버 계약·이관 문서·화면·테스트를 함께 맞춘다.
- 시점 공란은 null, 확인된 0초는 0으로 전송하고 영상 링크에도 보존한다. truthy 검사로 0을 누락하거나 미확인 시점을 만들어 넣지 않는다.
- 승인·제외 후 목록과 공개 조회 갱신 흐름, 실패 시 원래 오류 안내를 유지한다. 내부 후보·검토 메모를 공개 지도 데이터로 사용하지 않는다.
- 공개 목록이 비어 있거나 로딩·오류 상태일 때 실제 상태를 표시한다. 가짜 식당·자동 승인·미구현 제보 기능을 넣지 않는다.

근거: `src/App.tsx`, `src/features/admin/CandidateReviewCard.tsx`, `src/App.test.tsx`, `../backend/src/main/java/kr/sedanji/SecurityConfig.java`, `../docs/DIRECT_REGISTRATION_MIGRATION.md`.

## 지도와 출처 표시

- `src/KakaoMap.tsx`는 DOM과 Kakao Maps JavaScript SDK에 의존하는 웹 컴포넌트다. 모바일 공통 컴포넌트로 간주하지 않는다.
- 지도에는 브라우저용 `VITE_KAKAO_JAVASCRIPT_KEY`만 사용하고 등록 도메인 제한을 적용한다. 장소 검색 REST 키는 서버에서만 사용한다.
- Kakao `LatLng` 인수는 latitude, longitude 순서다. Backend의 x=longitude, y=latitude와 혼동하지 않는다.
- SDK 중복 로딩 방지, 타임아웃·연결 실패·키 누락 안내, effect 종료 후 비동기 처리 방지, 마커 정리와 선택 위치 이동을 유지한다.
- 승인된 식당 목록과 지도 선택·필터를 일관되게 연결한다. 지도 실패 시에도 목록·공식 Kakao 장소 링크·YouTube 출처를 확인할 수 있게 한다.
- 관계 유형은 검증된 값만 표시하고 영상은 공식 링크 또는 허용된 임베드로 제공한다. 미확인 관계를 단골·추천으로 강화하지 않는다.

근거: `src/KakaoMap.tsx`, `src/KakaoMap.test.tsx`, `src/App.tsx`.

## 테스트와 검증

- 실행이 허용된 작업에서 `frontend` 기준 `npm test`(vitest run), `npm run build`(Vite)를 사용한다. `npm run typecheck`는 tsc --noEmit이며 `npm run build`는 타입 검사 후 Vite 빌드를 수행한다. lint 스크립트는 없다. 실행하지 않은 검사를 통과했다고 보고하지 않는다.
- 테스트 환경은 `vite.config.ts`의 jsdom과 `src/test-setup.ts`다. RTL·Vitest로 API·지도 SDK를 모의 처리하고 실제 API·키·외부 SDK 다운로드에 의존하지 않는다.
- API 래퍼 변경은 CSRF·폼 전송·빈 응답·오류 메시지 처리, 화면 변경은 빈 목록·검색·출처 링크·승인 조건·실패 상태, 지도 변경은 좌표 순서·수명주기를 관련 범위에서 검증한다.
- 현재 테스트는 API의 빈 201·204·JSON·서버 오류 메시지·폼 인코딩·CSRF 조회 실패 시 요청 차단, 공개 목록·검색·오류·0초 출처 링크·명시적 승인 입력, 지도 좌표 순서를 검증한다. SDK 타임아웃 등 추가 검증 권고를 기존 테스트 완료 사실로 표현하지 않는다.
- 기존 `src/api.test.ts`, `src/App.test.tsx`, `src/KakaoMap.test.tsx`를 먼저 확인한다. UI 입력 필수 조건은 Backend 계약과 교차 검토한다.
- jsdom 테스트·Vite 빌드 통과를 실제 지도·브라우저 세션·배포·Expo Go 검증으로 표현하지 않는다. 설치·빌드·테스트 금지 요청을 따르고 문서만 변경하면 참조·사실·diff 검토를 우선한다.
