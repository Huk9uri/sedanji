# W1-05 #53 망원동 공개 콘텐츠 검수

기존 ngrok HTTPS 테스트 환경에서 망원동 주변을 검색했다. 결과에 포함된 인접 동도 검수 범위로 삼았다. 관찰일은 2026-11-15(KST)이며 정확한 관찰 시각과 실행 서버의 배포 SHA는 확인하지 못했다. 검수 당시 로컬 HEAD는 d0c2bdd4a6339b3f90f128c545119bf231b55b01이었다.

지도에 표시된 범위는 경도 126.8958–126.9625, 위도 37.5467–37.5862이며 필터는 전체 셰프였다. 표시 좌표를 사용한 별도 공개 API 조회에서 2곳을 반환했다. 응답은 truncated=false, totalCount=null이었다. 두 곳 모두 망원동 주소였다. [공개 영역 응답](https://culture-obtrusive-unsaddle.ngrok-free.dev/api/public/restaurants?west=126.8958&south=37.5467&east=126.9625&north=37.5862)

관리자가 관계 유형과 원본 근거를 확인한다. 이 검수의 기대값은 승인·공개된 관계의 지점, 셰프, 유형, 영상, 시점이 공개 화면과 일치하는 것이다. 관리자 근거 위치는 기존 후보 검토 → 셰프–식당 관계 → 관계 검증 근거다.

| 식당과 지점 | 관리자 관계 | 공개 관찰과 판정 |
| --- | --- | --- |
| 뽀요베리 · 식당 1 · Place 1158460117 · 서울 마포구 월드컵로13길 55-15 / 망원동 57-302 · 위도 37.554615, 경도 126.906871 | 관계 1 · 윤남노 1 · RECOMMENDED · 영상 RMTJPFKRbEk · 시점 null · APPROVED / PUBLISHED. 변경 이력에서 과거 공개 중단과 복구 확인 | **PASS** · 핀·목록·상세에 같은 식당이 표시됐다. 주소와 장소 링크가 일치했고 영상에 시점 미확인이 표시되며 URL에 t가 없었다. [공개 상세](https://culture-obtrusive-unsaddle.ngrok-free.dev/api/public/restaurants/1?sourcePage=0&sourceSize=20) |
| 라바즈 · 식당 6 · Place 783754308 · 서울 마포구 월드컵로13길 19-23 / 망원동 379-32 · 위도 37.554994, 경도 126.909425 | 관계 6 · 윤남노 1 · REGULAR · 영상 8PEUBRxlzFQ · 440초 · APPROVED / PUBLISHED. 관리자 화면에 변경 이력 없음 | **PASS** · 핀·목록·상세에 같은 식당이 표시됐다. 주소와 장소 링크가 일치했고 영상 URL에 t=440s가 있었다. [공개 상세](https://culture-obtrusive-unsaddle.ngrok-free.dev/api/public/restaurants/6?sourcePage=0&sourceSize=20) |

- **PASS** · 이 검색 범위에서 두 핀을 발견하고 각 식당의 정확한 지점과 관리자 승인 출처까지 따라갈 수 있었다. 확인한 공개 상세 응답에는 내부 검토 메모가 없었다.
- **과거 #52 검증** · 라바즈의 공식 Kakao 지점과 도로명 주소가 일치했다. YouTube 영상 ID와 t=440s를 확인했고, 사용자가 재생 시작 위치 7:20을 확인했다.
- **NOT RUN** · 당시 검색에 0초 사례와 초안·후보·중단 관계의 비교 자료가 없어 실제 데이터로 제외 조건과 0초 링크를 별도 대조하지 못했다. 기존 공개 API 통합 테스트의 공개 상태 및 출처 시점 검증을 참고할 수 있다. [PublicIntegrationTest](../backend/src/test/java/kr/sedanji/publicweb/PublicIntegrationTest.java)
