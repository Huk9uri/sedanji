# 관리자 식당 관리 API 계약

갱신일: 2026-11-05. 상태: 현재 코드 계약과 목표 설계를 구분한 문서. 기준: [모델](DOMAIN_MODEL.md), [명세서](REQUIREMENTS.md).

## 현재 코드 계약 — 2026-11-05 정적 확인

기준: `feat/RegistrationService` / `d2c44a16f203c3468cceb3bd1b4bd1c02ed57bd4` 및 Issue #14 작업 트리. 다음은 로컬 소스 기준이며 배포 서버 응답·DB 적용 상태·테스트 통과를 이번에 확인한 것은 아니다. 이 절 뒤의 **목표 설계**는 현재 API 전체에 적용된 규칙이 아니다.

경로는 아래 표에서 `/api/admin` 기준이다. DB 관리자 세션·ADMIN·CSRF 보호는 유지한다.

| 경로 | 현재 요청·응답·부수효과 |
| --- | --- |
| GET `/places?query=...` | 200. query는 공백만 입력 불가·최대 100자. `{candidates:[{placeSelectionToken,place:{id,name,roadAddress,address,longitude,latitude,placeUrl}}],totalCount,complete}`. 검색만으로 업무 DB 자료를 만들지 않음 |
| POST `/videos/metadata` | `{url}`(필수·최대 500자). 후보 추출 없이 기존 영상 재사용 또는 공식 메타데이터 등록. 신규·재사용 모두 201, `Models.Video` 직접 반환 |
| POST `/mentions` | 아래 직접 승인 요청과 `Idempotency-Key`(필수·비공백·최대 128자). 201 `{mentionId,restaurantId,version,verificationStatus,publicationStatus}`. 초안은 생성하지 않으며 `draftId` 없음 |
| POST `/mention-drafts` | CREATE 초안 생성. 필수 장소·멱등키, 201 상세 JSON. 아래 #14 계약 참조 |
| GET `/mention-drafts/{id}` | CREATE 초안 상세 200. DRAFT·APPLIED 조회 가능 |
| GET `/restaurants/{restaurantId}/mention-drafts` | CREATE 초안 식당별 페이지, 200 페이지 JSON |
| PATCH `/mention-drafts/{id}` | DRAFT 부분 수정. expectedVersion 필수, 200 상세 JSON |
| GET / POST `/chefs` | GET은 검색·페이지 없는 `{id,name}` 배열. POST `{name}`은 필수·최대 100자, 200 Chef. 동명 신규 생성 가능. 현재 이 경로에는 멱등 영수증 처리 없음 |
| GET / POST `/videos` | GET은 Video 배열. POST `{url}`은 기존 영상 수집·설명란 후보 추출 흐름이며 200 Video. 메타데이터 전용 API와 구분 |
| GET `/mentions`, GET `/candidates` 및 `/candidates/...` | 기존 관계·후보 조회·검색·승인·제외 유지. 후보 승인·수동 추가는 빈 201, 제외는 204. 후보 승인 입력은 `evidence` 필수·`note` 선택 |

직접 승인 요청은 `placeSelectionToken`, 양의 정수 `chefId`, 11자리 `videoId`, `mentionType`이 필수다. `timestampSeconds`는 null 또는 0 이상 int 정수, `verificationNote`는 선택·최대 2,000자이며 공백은 null로 저장한다. 직접 승인은 토큰만 받으며 #14 초안 API는 기존 restaurantId도 지원한다. 직접 승인은 자유 입력 evidence를 요구하지 않지만 기존 후보 승인은 근거 입력을 요구한다.

장소 토큰은 관리자·세션에 묶여 15분 유효하며 최대 45개 후보를 담은 검색 묶음을 최근 5개 보관한다. 응답에 `selectionExpiresAt`은 없다. 주소 미제공은 null, 장소 URL은 검증된 Kakao ID로 생성한다. 완전하지 않은 검색은 `complete=false`로 반환한다.

직접 승인에서는 식당 생성·재사용, 관계 승인, 감사 이벤트, 24시간 멱등 영수증을 하나의 트랜잭션으로 저장한다. 같은 관리자·키·요청의 유효 영수증 재생은 세션 검색 자료가 없어져도 같은 결과를 201로 반환한다. 다른 요청은 409, 만료 후에는 관계 중복 제약으로 재생성을 막는다. 기존 식당 이름·주소는 재사용 때 덮어쓰지 않는다. 후보·초안 생성 없이 관계가 APPROVED/PUBLISHED가 되며 공개 조회는 두 상태를 모두 검사한다.

현재 Video는 `id/title/channelTitle/publishedAt/description/extractionWarning`이다. `sourceCheckStatus/sourceCheckedAt`, 재사용 여부 래퍼, 출처 재확인 API는 없다.

### 현재 오류와 소비자

- `DirectRegistrationErrors`는 신규 Controller 범위에서 400 `INVALID_INPUT`, 404 `RESOURCE_NOT_FOUND`, 409 `DUPLICATE_RELATION`(existingMentionId 포함)·`IDEMPOTENCY_CONFLICT`, 410 `PLACE_SELECTION_EXPIRED`를 `code/message`로 변환한다. 모든 오류가 이 형식은 아니다.
- 외부 실패는 기존 `ApiErrors`의 502 `message` 응답을 사용한다. 목표의 422 출처 오류·503/504 분류·`fieldErrors/retryable`은 구현 완료가 아니다. 보안 401·403은 빈 본문일 수 있다.
- `frontend/src/App.tsx`·`CandidateReviewCard.tsx`는 기존 후보 경로를 사용하며 신규 직접 등록 API 소비 화면은 없다. `api.ts`는 CSRF·JSON/폼·빈 응답을 처리하지만 사용자 지정 헤더 전달과 구조화 오류 보존은 지원하지 않는다. 신규 화면 연결 시 멱등키·중복 ID·만료 오류 처리가 필요하다.
- 기존 후보 화면의 검토 메모는 필수지만 Backend는 선택이다. 이를 서버 필수 규칙으로 바꾸지 않으며 UI 수정은 후속 구현 범위다.
- `scripts/smoke-api.py`, `scripts/check-backend-state.py`도 기존 조회 경로를 소비한다. 영상 경로·응답 변경 때 함께 검토하며 이번에는 실행하지 않았다.

근거: `backend/src/main/java/kr/sedanji/`의 `ApiController`, `Models`, `PlaceSelectionService`, `registration/DirectRegistrationController`, `DirectRegistrationRequest`, `DirectRegistrationResult`, `DirectRegistrationFacade`, `RegistrationService`, `DirectRegistrationErrors`, `video/VideoMetadataService` 및 Frontend 소비 코드.

### #14 CREATE 초안 — 승인된 현재 계약

모든 경로는 관리자 세션·ADMIN을 요구하며 POST·PATCH는 CSRF를 유지한다. 서비스에서도 활성 관리자를 확인한다. 활성 관리자끼리 조회·수정을 공유하고 작성자·수정자는 ID로 기록한다.

생성 입력:

```typescript
{
  restaurantId?: number;
  placeSelectionToken?: string;
  chefId?: number | null;
  videoId?: string | null;
  mentionType?: 'REGULAR' | 'RECOMMENDED' | 'VISITED' | 'MENTIONED' | null;
  timestampSeconds?: number | null;
  verificationNote?: string | null;
}
```

- 유효한 양의 정수 restaurantId 또는 비공백·최대 100자 장소 토큰 중 정확히 하나를 제출한다. 장소 null·빈 토큰·두 필드 동시 제출은 400이다. 토큰은 기존 관리자·세션 검색 자료로 검증하고 외부 API는 호출하지 않는다.
- 선택 관계 필드의 생략·null은 허용한다. 입력한 Chef·Video·Restaurant 참조는 실제 존재해야 한다. videoId는 비어 있지 않은 최대 11자 문자열이다.
- 시점은 null 또는 0..2147483647 정수다. 메모는 원문 최대 2000자를 검사한 다음 trim하고 빈 값을 null로 저장한다.
- 숫자 문자열·소수·범위 초과·잘못된 enum·알 수 없는 필드·서버 관리 필드는 400이다. kind/status/작성자/적용 관계/검증·공개 상태는 지정할 수 없다. 이 엄격 파싱은 신규 초안 API에 한정한다.
- 명시적 초안 저장 시 Restaurant을 생성·재사용하며 기존 Kakao ID 장소 정보를 덮어쓰지 않는다. Mention은 만들지 않고 동일 관계 조합의 CREATE 초안 중복은 허용한다. 관계 중복 차단은 최종 승인 책임이다.

PATCH는 expectedVersion(필수·null 불가·0 이상 정수)과 하나 이상의 변경 필드가 필요하다. 누락은 유지, 선택 관계 필드 null은 해제다. 빈 영상 ID·빈 유형은 400, 빈/공백 메모는 null이다. 장소 필드 모두 생략 시 유지, 제출 시 생성과 같은 규칙으로 교체한다. 같은 값을 제출해도 성공 시 version과 updatedAt을 정확히 한 번 갱신한다. 이전 버전 재시도는 409이며 수정 영수증은 만들지 않는다.

상세 응답:

```text
id, kind, status, version,
restaurant: {id,kakaoPlaceId,name,roadAddress,address,longitude,latitude,placeUrl},
chefId, videoId, mentionType, timestampSeconds, verificationNote,
candidateId, appliedMentionId, createdBy, updatedBy, createdAt, updatedAt
```

선택값은 명시적 null, 관리자는 ID만 반환한다. #14 생성은 CREATE/DRAFT/version=0, candidateId/appliedMentionId=null이다. 목록은 CREATE만 포함하고 page=0, size=20(1..100), status=DRAFT(또는 APPLIED), updatedAt DESC·id DESC다. `{items,totalElements,page,size,hasNext}`를 반환한다. 기존 식당에 초안이 없으면 빈 목록, 없는 식당은 404다. APPLIED는 조회만 허용하며 CORRECTION은 이번 API의 조회·수정 대상이 아니다.

생성은 관리자별 필수 Idempotency-Key(비공백·최대 128자)로 24시간 성공 결과를 재생한다. 메서드·경로·정규화 요청을 해시하며 같은 관리자·키의 다른 본문·경로는 409다. 활성 관리자 확인 후 유효 영수증은 세션 검색 자료가 없어도 재생한다. 토큰 원문은 감사·영수증에 저장하지 않는다. 24시간 만료 후 동일 초안의 영구 중복 방지는 보장하지 않는다. 기존 직접 승인 영수증 형식·해시·201 재생은 유지한다.

초안·새 식당·감사·생성 영수증은 함께 커밋/롤백한다. DRAFT_CREATED는 생성 후, DRAFT_UPDATED는 전후 스냅샷을 기록하며 감사는 추가 전용이다. PATCH는 관리자 → 초안 순서로 잠근 후 expectedVersion을 비교하고 flush 후 실제 version을 응답·감사에 기록한다.

| 상태 | code | 추가 필드 |
| --- | --- | --- |
| 400 | INVALID_INPUT | 없음 |
| 404 | RESOURCE_NOT_FOUND | 없음 |
| 409 | STALE_VERSION | currentVersion |
| 409 | DRAFT_NOT_EDITABLE / IDEMPOTENCY_CONFLICT | 없음 |
| 410 | PLACE_SELECTION_EXPIRED | 없음 |
| 500 | INTERNAL_ERROR | 내부 예외·DB 원문 없음 |

401·403은 기존 보안 응답을 유지하며 빈 본문일 수 있다. 초안만 있는 식당은 공개하지 않으며 APPROVED AND PUBLISHED를 유지한다. 초안 승인(#15), 정정·공개 제어, Frontend 타입·래퍼·UI(#17)는 후속 범위다. 기존 직접 승인은 초안 생성 없이 유지한다.

검증 상태: 입력 단위·API·트랜잭션·동시성 테스트 소스를 추가했다. verify.ps1 -Backend -Frontend의 최초 샌드박스 실행은 AuthorizationManager에서 차단됐다. 정책 변경 없이 동일 명령을 샌드박스 밖에서 재실행한 결과 Frontend 타입 검사·테스트 11개·빌드는 PASS(각 종료 코드 0), Backend는 Docker Engine 접근 실패로 NOT RUN이다. 이후 Docker 실행 후 verify.ps1 -Backend를 재실행하여 컴파일·테스트 137개가 모두 PASS(실패·skip 0, 전체 종료 코드 0)했다. 실제 개발 DB·외부 API·배포 상태는 미검증이다.

### 목표와의 차이 및 결정 상태

| ID | 현재 → 기존 목표 설계 | 후속 검토 |
| --- | --- | --- |
| D1 | 초안 없이 직접 승인 → 기존 목표의 초안 경유 직접 승인 | #14 승인: 기존 직접 승인 유지. 초안 승인은 #15 별도 연결 |
| D2 | 중첩 candidates/place·만료 시각 없음 → 평탄한 items·selectionExpiresAt | #17 전에 응답 형식·만료 안내 계약 결정. 이름만 바꾸는 소급 변경 금지 |
| D3 | `/videos` 후보 수집 + `/videos/metadata` 전용 → `/videos` 메타데이터 전용 | #17 전에 분리 경로 유지 또는 호환 전환 계획 결정. 기존 소비자의 후보 추출을 깨뜨리지 않음 |
| D4 | 초안은 restaurantId 또는 토큰 지원, 직접 승인은 토큰 유지 | #14 승인·구현. #15·#18 소비 흐름은 후속 |
| D5 | 직접 승인·초안 생성 멱등 처리 → 셰프·정정 생성까지 확장 | #14 승인·구현. 다른 생성 경로는 후속 |
| D6 | 출처 확인 상태·재확인 API 없음 → 관리자 재확인·필터 | #16·#18 전에 저장 모델·실패 보존·판정 기준 설계. 영상 접근 불가만으로 관계를 숨기지 않음 |

D1·D4·D5의 #14 범위는 사용자 승인에 따라 위 현재 계약으로 구현했다. 나머지 항목과 후속 범위는 미정이며 문서 전체를 구현 승인으로 간주하지 않는다.

## 목표 설계 — 미구현 부분과 전환안 포함

아래는 2026-11-02 설계 본문을 보존한 것이다. 위 현재 계약과 다른 경로·응답·상태·저장 흐름은 결정 및 후속 구현 전까지 호출 가능한 계약으로 취급하지 않는다. 특히 초안 경유 직접 승인과 초안 저장 시 관계 중복 거부 제안은 #14 확정 계약과 다르므로 위 현재 계약을 우선한다.

## 공통

- 경로는 `/api/admin` 아래다. 기존 DB 관리자 세션·ADMIN 권한·CSRF 보호를 사용한다. 작성·승인 권한을 분리하지 않으며 본인 자료를 승인할 수 있다.
- 요청·응답 JSON은 camelCase, 시간은 UTC ISO 8601, ID는 기존 도메인 타입을 유지한다. 시점은 `timestampSeconds: null | 0 이상의 정수`다.
- version이 있는 수정·상태 전이 요청은 `expectedVersion`을 필수로 받는다. 정정안에는 추가로 `baseVersion`을 두고 서버가 대상 현재 version을 확인한다. 충돌 시 덮어쓰지 않는다.
- 성공 응답은 명시적 JSON을 반환한다. 생성은 201, 조회·수정·승인·공개 제어는 200. 완료된 동일 초안 승인 재요청은 같은 적용 결과를 200으로 반환한다.
- 목록은 `page`(0부터)·`size`(기본 20, 최대 100), `items/totalElements/page/size/hasNext`를 사용한다. 이 규칙은 관리자 목록이며 사용자 앱의 커서 조회와 별개다.
- 외부 키·헤더·원문 외부 오류·DB 예외·비밀번호는 응답과 로그에 포함하지 않는다. 검색 결과가 0개면 200의 빈 배열이며 외부 오류는 비정상 응답이다.

## 장소 검색과 선택

| 메서드·경로 | 계약 |
| --- | --- |
| GET `/places?query=...` | 후보 ID·상호·도로명/지번 주소·longitude·latitude·placeUrl·선택 토큰 반환. 기존 검색 어댑터의 최대 3페이지 범위와 totalCount/complete를 명시하며 잘린 결과를 전체라고 표현하지 않음 |

예시 응답 구조:

```json
{
  "items": [{
    "kakaoPlaceId": "1158460117",
    "name": "뽀요베리",
    "roadAddress": "서울 마포구 월드컵로13길 55-15",
    "address": "서울 마포구 망원동 57-302",
    "longitude": 126.90687068921198,
    "latitude": 37.55461513602502,
    "placeUrl": "https://place.map.kakao.com/1158460117",
    "placeSelectionToken": "opaque-example",
    "selectionExpiresAt": "2026-11-02T01:15:00Z"
  }],
  "totalCount": 1,
  "complete": true
}
```

식당 값은 PoC 참고 자료이며 토큰·시각은 예시다. 검색 응답만으로 Restaurant이나 관계가 생성되지 않는다. `complete=false`도 후보 선택은 가능하나 후보 누락 여부를 관리자에게 표시하고 주소·실제 지점을 확인해야 한다.

각 선택 토큰은 검색한 관리자의 서버 세션에 후보 스냅샷과 함께 15분간 보관한다. 토큰 문자열은 추측 불가능하게 만들고 로그에 남기지 않는다. 다른 관리자/세션 토큰 사용과 임의 주소·좌표 제출은 거부한다. 세션당 최대 45개 장소를 담은 최근 5개 검색 묶음만 보관하고 이전 묶음은 만료 처리한다. 단순 검색의 일시 캐시는 별도 보류 업무 자료가 아니다.

신규 작성에서 장소만 고른 상태는 브라우저 양식+선택 토큰으로 유지한다. 임시 저장 또는 승인할 때 토큰을 보내면 서버가 스냅샷으로 실제 장소를 조회/생성한다. 기존 식당 상세의 관계 추가는 `restaurantId`만 받는다. 두 식별자를 동시에 보내지 않는다.

## 셰프·영상

| 메서드·경로 | 요청·응답 |
| --- | --- |
| GET `/chefs?query=...&page=0&size=20` | ID·이름·연결 식당 요약을 포함한 관리자 검색 목록. 동명이인 항목을 각각 반환. 관리자 검색은 이름순·ID순으로 안정 정렬하며 사용자 필터의 식당 개수순과 구분 |
| POST `/chefs` | `{name}`. 관리자가 신규 입력을 명시한 경우 생성. 같은 이름의 기존 셰프를 자동 선택하지 않음. 버튼을 누르기 전 검색 결과로 동명 항목 확인 |
| POST `/videos` | `{url}`. videoId 추출 후 기존 자료면 재사용, 없으면 공식 snippet 조회 후 생성. `{video, reused}` 반환, 새 영상은 201·기존은 200. 후보 추출 작업을 자동 시작하지 않음 |
| POST `/videos/{videoId}/source-check` | 공식 API로 접근 상태 재확인. 새 상태·확인 시각 반환. 조회 불가면 REVIEW_REQUIRED, 호출 실패는 오류 응답 및 안전한 확인 실패 기록. 관계 공개 상태는 변경하지 않음 |

Video 응답은 `id/title/channelTitle/publishedAt/description/sourceCheckStatus/sourceCheckedAt`를 제공한다. 잘못된 URL은 400, 조회 가능한 원본 영상이 없으면 422로 구분한다. 기존 영상 재사용은 현재 재생 가능 보장을 의미하지 않는다.

외부 호출 실패 시 마지막 확인 상태·성공 확인 시각은 유지하고 마지막 시도 시각·안전한 실패 코드만 별도로 기록한다. 영상 조회 불가가 확인되어 REVIEW_REQUIRED가 된 경우와 네트워크·할당량으로 확인 자체를 못 한 경우를 구분한다.

## 등록·초안·승인

| 메서드·경로 | 동작 |
| --- | --- |
| POST `/mention-drafts` | CREATE 초안 명시적 임시 저장. 장소 식별만 필수. 초안·연결 식당·version 반환 |
| GET `/mention-drafts/{id}` | 원본 후보 연결, 입력 내용, status/version 포함 관리자 조회 |
| PATCH `/mention-drafts/{id}` | DRAFT 내용 저장. expectedVersion 필수. 적용 완료된 초안 수정 불가 |
| POST `/mention-drafts/{id}/approve` | expectedVersion 필수. 필수 항목 확인 후 Mention 생성 또는 정정 적용. 초안 APPLIED와 이력을 함께 저장 |
| POST `/mentions` | 저장 전 양식에서 바로 ‘승인·공개’. CREATE 초안 생성·검증·승인을 하나의 트랜잭션으로 처리. 결과는 mentionId/draftId/version |

CREATE 양식의 공통 필드:

```json
{
  "restaurantId": 12,
  "chefId": null,
  "videoId": null,
  "mentionType": null,
  "timestampSeconds": null,
  "verificationNote": null
}
```

신규 장소라면 `restaurantId` 대신 `placeSelectionToken`을 보낸다. ID 12는 예시다. 시점·메모는 선택이고 빈 메모는 NULL로 정규화한다. 식당이 선택되지 않은 초안 저장은 400이다. 승인에는 실제 셰프·영상 FK·유형이 모두 있어야 한다. 클라이언트가 verificationStatus/publicationStatus/reviewedBy를 지정할 수 없다.

CREATE 초안 PATCH의 식당 변경은 기존 restaurantId 또는 새 장소 토큰으로 처리하며 저장되지 않은 변경 이탈 원칙을 적용한다. 임시 저장을 누른 경우에만 새 선택을 저장한다. 같은 관계가 이미 있으면 409와 existingMentionId를 반환한다. 기존 관계를 수정하거나 승인 상태를 바꾸지 않는다.

생성 POST(셰프·직접 승인·초안·정정안)는 `Idempotency-Key`를 필수로 받는다. 별도 작은 `admin_request_receipts` 테이블에 관리자 ID·키·경로/요청 해시·성공 결과·생성/만료 시각을 저장하고 `(admin_id, key)`를 UNIQUE로 둔다. 성공 결과 보관은 24시간이다. 동일 키·동일 요청은 동일 결과, 다른 요청 재사용은 409다. 업무 저장과 영수증 저장을 한 트랜잭션에 묶고 실패는 성공 영수증을 남기지 않는다. 만료 후에도 관계 UNIQUE·초안 상태·version으로 중복 승인/수정을 방지한다. 외부 조회 결과·인증 헤더를 영수증에 저장하지 않는다.

## 관리자 식당 목록·상세

| 메서드·경로 | 계약 |
| --- | --- |
| GET `/restaurants?query=...&publication=ALL&needsSourceReview=false&page=0&size=20` | 식당명·주소·셰프명 검색. publication은 ALL/PUBLISHED/NO_PUBLIC_RELATION. 출처 재확인 필터는 독립적. 기본 전체·최근 등록순(id 내림차순 보조) |
| GET `/restaurants/{id}` | 장소 정보·version, 관계별 셰프·유형·영상·채널·시점·검증/공개 상태·version, 저장된 미완료 초안 요약 반환 |
| GET `/restaurants/{id}/mention-drafts?page=0&size=20` | 임시 저장 재개용 목록. 식당 상세 안에서 접근하며 새 업무 메뉴를 만들지 않음 |
| GET `/mentions/{id}/history?page=0&size=20` | 상세 관계 아래 펼치기에 필요한 이력. 최근순, 사유·전후 값·처리자·시각 포함 |
| GET `/restaurants/{id}/history` 및 `/chefs/{id}/history` | 공유 기본 정보 정정 이력. 관리자만 접근 |

목록 항목은 `id/name/roadAddress/address/chefs/publicRelationCount/hasPublicRelation/needsSourceReview/createdAt`를 제공한다. 관리자 셰프 표시에는 공개 중단·승인 상태를 구분할 수 있는 관계 요약을 포함한다. 공개 응답 DTO와 공유하지 않는다. 상세의 관계·초안·이력은 각각 페이지 조회를 지원하여 한 식당의 모든 이력을 강제로 내려주지 않는다.

## 정정·공개 중단·복구

| 메서드·경로 | 요청·규칙 |
| --- | --- |
| POST `/mentions/{id}/corrections` | expectedVersion·changeReason 필수, 새 식당/셰프/영상·유형·시점·선택 메모의 제안값. CORRECTION 초안 생성, 원본 유지 |
| PATCH `/mention-drafts/{id}` 및 POST `/{id}/approve` | 위 초안 계약 재사용. 정정 사유 필수, baseVersion 검사. 재승인이어도 SUSPENDED를 자동 복구하지 않음 |
| POST `/mentions/{id}/suspend` | `{expectedVersion, reason}`. 공개 중단과 이력 기록. 같은 version의 완료 요청 반복은 충돌·새로고침 안내, 불필요한 이력 중복 생성 없음 |
| POST `/mentions/{id}/restore` | `{expectedVersion, reason, evidenceRechecked:true}`. 관리자가 실제 근거 재확인을 명시한 뒤 복구 승인. boolean은 서버의 영상 내용 자동 검증을 의미하지 않음 |
| POST `/restaurants/{id}/revisions` | expectedVersion·changeReason·공식 장소 조회의 placeSelectionToken. 기존 Kakao ID와 같아야 함. 다른 지점이면 관계 정정 사용 |
| PATCH `/restaurant-revisions/{id}` | expectedVersion과 수정 제안·사유 저장 |
| POST `/restaurant-revisions/{id}/approve` | expectedVersion 필수, baseVersion 검사 후 실제 기본 정보 반영·이력 기록 |
| POST `/chefs/{id}/revisions` | `{expectedVersion, proposedName, changeReason}`. 신규 동일인 여부는 관리자가 확인 |
| PATCH `/chef-revisions/{id}` 및 POST `/{id}/approve` | 초안 수정·재승인. 다른 셰프와 자동 병합하지 않음 |

Revision 조회 GET도 각 `.../{id}`에 제공한다. 모든 Revision에는 작성자·수정자·생성/수정 시각과 version을 반환한다. 영구 삭제·자동 승인·관계 병합 API는 이번 범위에 두지 않는다. 과거 REJECTED 관계의 별도 승인 취소 정책은 미정이며 새 UI로 임의 확장하지 않는다.

## 오류

```json
{
  "code": "INCOMPLETE_APPROVAL",
  "message": "승인에 필요한 정보를 확인하세요.",
  "fieldErrors": {"chefId": "셰프를 선택하세요."}
}
```

| HTTP | code 예시 | 처리 |
| --- | --- | --- |
| 400 | INVALID_INPUT / INVALID_VIDEO_URL | 입력 유지·필드 안내 |
| 401 / 403 | UNAUTHENTICATED / FORBIDDEN / CSRF_REJECTED | 관리자 인증·접근 안내 |
| 404 | RESOURCE_NOT_FOUND | 대상이 없음을 안내 |
| 409 | DUPLICATE_RELATION / STALE_VERSION / INVALID_STATE / OPEN_REVISION_EXISTS / IDEMPOTENCY_CONFLICT | 기존 ID 또는 최신 version을 관리자에게 제공하고 자동 덮어쓰기 금지 |
| 410 | PLACE_SELECTION_EXPIRED | 검색 결과 선택 기한이 지났음을 안내. 결과 없음으로 처리하지 않음 |
| 422 | INCOMPLETE_APPROVAL / SOURCE_UNAVAILABLE | 초안은 유지하고 승인 또는 영상 등록 불가 사유 안내 |
| 502 / 503 / 504 | EXTERNAL_AUTH / EXTERNAL_RESPONSE / EXTERNAL_QUOTA / EXTERNAL_NETWORK / EXTERNAL_TIMEOUT | 외부 서비스 오류 구분. 외부 잘못된 키를 사용자 로그인 401로 혼동시키지 않음 |

503은 외부 할당량/일시 연결 불가, 504는 타임아웃, 502는 외부 인증/응답 형식 오류에 사용한다. 재시도 가능 여부를 안전한 `retryable` 값으로 제공하며 오류 때문에 식당·제보를 NOT_FOUND/보류로 바꾸지 않는다. 외부 제공자의 상세 메시지·키 포함 URL을 그대로 전달하지 않는다.

## 기존 API 전환

현재 후보 기반 `/admin/candidates/...`와 import 동작은 전환 전까지 유지한다. 새 관리자 화면은 직접 등록 계약을 사용한다. 기존 `/admin/videos`의 추출 부수효과는 명시적인 후보 수집 작업으로 분리하고 직접 등록에서는 실행하지 않는다. 해당 기존 클라이언트·테스트도 같은 작업 단위에서 갱신한다.

새 모델 적용 후 기존 후보 승인 경로도 같은 선택 메모·중복·공개 규칙을 호출하도록 맞춘다. 과거 API가 새 공개 조건을 우회하는 별도 승인 통로로 남아서는 안 된다. 일반 사용자 공개 API에는 초안·상태·사유·이력·관리자 식별자를 포함하지 않는다.
