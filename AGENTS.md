# AGENTS.md — 세단지(Sedanji) 개발 지침

## 1. 프로젝트 개요

### 프로젝트명
- 한글: 세단지
- 영문: SEDANJI
- 의미: 셰프들의 단골 지도

### 서비스 목적

셰프들이 YouTube 영상에서 방문하거나 추천하거나 단골로 언급한 식당을 지도 기반으로 탐색할 수 있는 서비스를 개발한다.

사용자는 지도에서 식당을 발견하고, 어떤 셰프가 어떤 영상에서 해당 식당을 언급했는지 출처를 확인할 수 있어야 한다.

### 핵심 가치

1. **출처 기반 신뢰성**: 식당과 셰프의 관계에는 근거가 되는 영상이 반드시 존재해야 한다.
2. **정확한 장소 매칭**: 동일 상호의 다른 지점을 잘못 연결하지 않는다.
3. **검증 후 공개**: 자동 수집 결과를 그대로 공개하지 않고 관리자가 확인한다.
4. **사용자 중심 탐색**: 지도, 셰프, 식당, 영상 간의 관계를 직관적으로 탐색한다.
5. **유지보수 가능한 설계**: 과도한 추상화 없이 명확한 책임 분리와 테스트 가능한 코드를 작성한다.

## 2. 개발 기술 스택

### Frontend
- React
- TypeScript
- Vite
- Tailwind CSS
- shadcn/ui
- TanStack Query
- React Router
- Kakao Maps JavaScript SDK

### Backend
- Java 17 이상
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- Spring Validation
- Spring Security (관리자 인증에 필요한 범위)
- PostgreSQL
- Flyway
- Gradle

### 외부 API
- YouTube Data API v3
- Kakao Local REST API
- Kakao Maps JavaScript SDK

### 테스트
- Backend: JUnit 5, Mockito, Testcontainers
- Frontend: Vitest, React Testing Library
- E2E: Playwright (핵심 흐름 구현 후 도입)

### 개발 환경
- Git / GitHub
- Docker / Docker Compose
- 환경변수 기반 비밀정보 관리

위 기술은 기본 선택이며, 변경이 필요하면 이유와 영향을 설명하고 승인받는다.

## 3. 현재 개발 상태

### PoC 01 — YouTube 메타데이터 조회

검증 완료:
- YouTube 영상 URL에서 video ID 추출
- YouTube Data API를 이용한 영상 메타데이터 조회
- 영상 설명란에서 식당명 및 주소 후보 추출
- 오류 및 예외 처리
- 단위 테스트

테스트 영상:
https://www.youtube.com/watch?v=RMTJPFKRbEk

추출 식당:
- 뽀요베리
- 모리본

### PoC 02 — Kakao Local API 장소 매칭

검증 완료:
- Kakao Local REST API 인증 및 실제 호출
- 식당명 검색
- 도로명 주소 비교
- 동일 상호의 다른 지역 식당 구분
- 장소 ID 및 좌표 확보
- 30개 단위 테스트 통과 (YouTube PoC 테스트 포함)

검증된 장소:
- 뽀요베리: Kakao Place ID `1158460117`
- 모리본: Kakao Place ID `652535611`

### 중요한 구분

PoC에서 검증된 것은 특정 영상 설명란의 식당 정보를 지도상의 실제 장소와 연결할 수 있다는 사실이다.

아직 검증되지 않은 사항:
- 모든 영상에서 식당 정보가 추출되는지
- 셰프와 식당의 관계가 실제로 어떤 유형인지
- 영상에서의 정확한 언급 시점
- 관리자의 최종 승인
- 운영 환경에서의 안정성

PoC 성공을 전체 서비스 기능 완성으로 표현하지 않는다.

## 4. 아키텍처 원칙

### 기본 구조

초기에는 모듈형 모놀리식 구조를 사용한다.

- Frontend: React SPA
- Backend: Spring Boot REST API
- Database: PostgreSQL
- External APIs: YouTube, Kakao

초기 단계에서 마이크로서비스, Kafka, Redis, Kubernetes 등을 도입하지 않는다.

실제 요구사항이나 성능 측정 결과가 있을 때만 추가 기술을 검토한다.

### Backend 패키지 구조

도메인 중심 구조를 우선한다.

예시:

```text
backend/
  src/main/java/.../
    chef/
    restaurant/
    video/
    mention/
    importjob/
    admin/
    common/
    infrastructure/
      youtube/
      kakao/
```

각 도메인은 필요한 범위에서 controller, service, repository, dto, entity 등의 책임을 분리한다.

외부 API 연동 코드는 도메인 로직과 분리하여 테스트 시 교체 가능하게 작성한다.

### Frontend 구조

기능 중심 디렉터리 구조를 사용한다.

예시:

```text
frontend/src/
  app/
  pages/
  features/
    map/
    restaurants/
    chefs/
    admin/
  entities/
  shared/
    api/
    components/
    hooks/
    utils/
```

불필요한 추상화 계층은 만들지 않는다.

## 5. 핵심 도메인 설계

### Chef
셰프 정보.

### Restaurant
실제 식당의 장소 정보.

- 이름
- 도로명 주소
- 지번 주소
- 위도 / 경도
- Kakao Place ID

### Video
출처 YouTube 영상.

- YouTube Video ID
- 제목
- 채널
- 게시일
- 영상 URL

### Mention
셰프, 식당, 영상을 연결하는 근거 관계.

- Chef
- Restaurant
- Video
- 언급 유형
- 영상 타임스탬프 (확인된 경우)
- 검증 상태
- 검증 메모

### ImportJob
YouTube 영상 수집 작업과 처리 상태.

### RestaurantCandidate
설명란에서 추출한 식당 후보와 장소 매칭 결과.

### AdminUser
관리자 인증 및 승인 권한.

정확한 필드, 관계, 제약조건은 구현 전에 ERD로 정의한다.

## 6. 데이터 신뢰성 규칙

### 장소 매칭과 관계 검증은 별개다

장소 매칭 상태:
- MATCHED
- REVIEW_REQUIRED
- NOT_FOUND

API 호출 실패는 NOT_FOUND로 처리하지 않는다.

셰프-식당 관계 검증 상태:
- PENDING
- APPROVED
- REJECTED

MATCHED는 실제 장소를 식별했다는 뜻이지 셰프의 추천 사실을 확인했다는 뜻이 아니다.

### 언급 유형

- REGULAR: 단골임이 명시적으로 확인됨
- RECOMMENDED: 추천 의사가 명확하게 확인됨
- VISITED: 실제 방문이 확인됨
- MENTIONED: 단순 언급만 확인됨

유형이 불확실하면 더 강한 의미로 추론하지 않는다.

예를 들어:
- 영상에 등장했다는 이유만으로 단골로 분류하지 않는다.
- 식당 주소가 설명란에 있다는 이유만으로 추천으로 분류하지 않는다.
- 영상 제목에 셰프 이름이 있다는 이유만으로 모든 식당을 해당 셰프가 추천했다고 판단하지 않는다.

### 공개 조건

관리자가 승인한 관계만 공개 API에 포함한다.

검증되지 않은 후보와 내부 메모는 공개하지 않는다.

## 7. 외부 API 연동 규칙

### YouTube

- 공식 YouTube Data API를 사용한다.
- 영상 메타데이터와 설명란을 우선 활용한다.
- 일반 공개 영상의 자막을 공식 API로 자유롭게 다운로드할 수 있다고 가정하지 않는다.
- 권한이 없는 자막 다운로드나 접근 제한 우회를 구현하지 않는다.
- 영상은 공식 링크 또는 허용된 임베드 방식으로 제공한다.
- API 실패, 할당량 초과, 삭제된 영상 등의 상황을 처리한다.

### Kakao

- 장소 검색에는 Kakao Local REST API를 사용한다.
- 지도 표시는 Kakao Maps JavaScript SDK를 사용한다.
- REST API 키는 서버 측에서만 사용한다.
- 브라우저용 키는 제공업체의 허용된 방식과 도메인 제한을 적용한다.
- 검색 결과의 첫 번째 장소를 무조건 선택하지 않는다.
- 이름과 상세 주소, 복수 후보 여부를 확인한다.
- 장소 식별에는 Kakao Place ID를 활용한다.
- 좌표계와 위도/경도 순서를 명확히 구분한다.
- API 호출량을 고려하고 중복 호출을 최소화한다.

### API 응답 관리

- 외부 API 응답을 그대로 도메인 엔티티에 결합하지 않는다.
- DTO 또는 어댑터를 통해 변환한다.
- 타임아웃과 오류를 명확하게 처리한다.
- 비밀키나 인증 헤더를 로그에 출력하지 않는다.

## 8. 데이터베이스 규칙

- PostgreSQL을 사용한다.
- 스키마 변경은 Flyway 마이그레이션으로 관리한다.
- `ddl-auto=update`에 의존하지 않는다.
- 식당 중복 식별에 Kakao Place ID를 우선 활용한다.
- YouTube Video ID에 적절한 유일성 제약을 적용한다.
- 셰프, 식당, 영상 간 관계를 단일 문자열 필드로 표현하지 않는다.
- 필요한 외래키, 인덱스, NOT NULL, UNIQUE 제약조건을 설계한다.
- 삭제 정책과 참조 무결성을 고려한다.
- 마이그레이션 파일은 적용 이후 임의 수정하지 않는다.

## 9. API 설