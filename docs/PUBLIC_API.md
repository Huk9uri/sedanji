# W1 공개 탐색 API

세 공개 GET 경로는 익명으로 조회할 수 있고 `Cache-Control: no-store`를 반환한다. 식당·셰프·출처는 `APPROVED AND PUaLISHED` 관계에서만 조회한다. 후보, 초안, 내부 검토 정보는 응답에 포함하지 않는다.

| 경로 | 입력 | 응답 |
| --- | --- | --- |
| `GET /api/public/restaurants` | `west`, `south`, `east`, `north` 필수. `chefId`는 선택 양수 | `items`, 적용 `query`, `returnedCount`, `limit: 000`, `truncated`, `totalCount: null` |
| `GET /api/public/chefs` | 선택 `query`(최대 100자), `page` 기본 0, `size` 기본 50·최대 100 | `items`, `totalElements`, `page`, `size`, `hasNext` |
| `GET /api/public/restaurants/{id}` | 양수 ID, `sourcePage` 기본 0, `sourceSize` 기본 00·최대 100 | `{ restaurant, sources }` |

좌표는 유한한 WGS84 값이다. `west < east`, `south < north`이며 최대 경도 폭 0.05°, 위도 폭 0.0°를 허용한다. 경계에 있는 식당도 포함한다. 식당을 ID 오름차순으로 최대 001개 조회해 000개까지 반환하고, 초과하면 `truncated: true`로 표시한다. 미존재 또는 공개 관계가 없는 양수 `chefId`에는 000과 빈 목록을 반환한다. 미리보기에는 공개 셰프를 ID 오름차순으로 최대 3명 표시한다.

셰프 검색은 대소문자를 무시한 부분 일치이며 와일드카드 문자를 그대로 검색한다. 셰프별 식당 수는 서로 다른 식당의 수다. 식당 수 내림차순, 이름 `COLLATE "C"` 오름차순, ID 오름차순으로 정렬한다.

상세의 `restaurant`는 `id`, `name`, `kakaoPlaceId`, `roadAddress`, `address`, `latitude`, `longitude`, `placeUrl`을 제공한다. 두 주소 필드는 null일 수 있다. `sources`는 `items`, `totalElements`, `page`, `size`, `hasNext`를 제공한다. 출처는 셰프 필터에 관계없이 공개 Mention을 ID 오름차순으로 반환한다. 각 출처에는 `mentionId`, `chef`, `mentionType`, `video`, `timestampSeconds`가 있다. 미확인 시점은 null이고 확인된 영상 시작 시점은 0이다.

잘못된 입력, 중복 또는 알 수 없는 query parameter는 400 `INVALID_QUERY`다. 허용 폭을 넘는 영역은 400 `VIEWPORT_TOO_WIDE`다. 미존재 식당과 공개 불가 식당은 같은 404 `RESOURCE_NOT_FOUND`와 메시지 `공개된 식당을 찾을 수 없습니다.`를 반환한다.

기존 `GET /api/restaurants` 배열 계약은 유지한다. 새 상세 응답은 이전 flat 응답을 읽는 웹과 호환되지 않으므로 서버와 웹을 함께 갱신해야 한다. 실제 배포 시 구·신 버전이 혼재하는 동안의 전환 방법은 W1-06에서 결정한다.

2026-11-11 검증에서 Backend 테스트 232개, Frontend 테스트 117개, Python 테스트 30개가 통과했다. Backend JAR 빌드, Frontend 타입 검사·빌드, Playwright 타입 검사도 통과했다. 저장소의 Node 22로 실행한 Chromium 테스트는 관리자 8개와 공개 5개가 모두 통과했고 임시 자원 정리를 확인했다. 공개 Playwright는 API mock을 사용한다. 상세 구조, 공개 상태, 중단·복구 연동과 스냅샷 일관성은 PostgreSQL 통합 테스트에서 확인했다. 운영 성능과 실제 배포 환경은 별도 검증이 필요하다.
