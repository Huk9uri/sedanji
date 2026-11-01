# 관리자 식당 관리 API 계약

갱신일: 2026-11-02. 상태: 구현 예정 계약. 현재 실행 중인 API와 구분한다. 기준: [모델](DOMAIN_MODEL.md), [명세서](REQUIREMENTS.md).

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
