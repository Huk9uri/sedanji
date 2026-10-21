package kr.sedanji;

import java.time.OffsetDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import kr.sedanji.mention.MentionType;
import kr.sedanji.restaurantcandidate.PlaceCandidateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static kr.sedanji.Models.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional @Testcontainers
@WithMockUser(username = "admin", roles = "ADMIN")
class ApprovalIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired CatalogService service;
    @Autowired CandidateMatchingService matching;
    @Autowired JdbcTemplate db;
    @Autowired EntityManager entityManager;
    @Autowired PlaceCandidateRepository places;
    @MockitoBean ExternalApis apis;
    private final String videoId = "RMTJPFKRbEk";
    private final Place place = new Place("1158460117", "뽀요베리", "서울 마포구 월드컵로13길 55-15", "서울 마포구 망원동 57-302", 126.90687068921198, 37.55461513602502, "http://place.map.kakao.com/1158460117");
    private long imported() {
        when(apis.video(videoId)).thenReturn(new Video(videoId, "검증 영상", "채널은 셰프 아님", OffsetDateTime.now(), "🥘 뽀요베리\n📍서울 마포구 월드컵로13길 55-15", "수동 확인"));
        service.importVideo("https://youtu.be/" + videoId);
        return service.candidates().get(0).id();
    }
    private long prepared() {
        long candidate = imported();
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(place), 1, true));
        matching.search(candidate, "뽀요베리");
        return candidate;
    }
    private void approve(long candidate, long chef) {
        service.approve(candidate, chef, place.id(), MentionType.VISITED, null, "해당 셰프의 방문을 직접 확인", "지점과 출처 확인", "admin");
    }
    private String body(long chef) {
        return "{\"chefId\":" + chef + ",\"placeId\":\"1158460117\",\"mentionType\":\"VISITED\",\"timestampSeconds\":null,\"evidence\":\"방문 확인\",\"note\":\"출처와 지점 확인\"}";
    }
    @Test void extractionCreatesCandidateNotMentionAndImportIsIdempotent() {
        imported(); imported();
        assertThat(service.candidates()).hasSize(1);
        assertThat(service.mentions()).isEmpty();
        assertThat(service.restaurants()).isEmpty();
        assertThat(service.chefs()).isEmpty();
        assertThat(db.queryForObject("select count(*) from import_jobs", Integer.class)).isEqualTo(1);
        verify(apis, times(1)).video(videoId);
    }
    @Test void matchingNeverApprovesRelationship() {
        prepared();
        assertThat(service.candidates().get(0).matchStatus()).isEqualTo("MATCHED");
        assertThat(service.candidates().get(0).disposition()).isEqualTo("OPEN");
        assertThat(service.mentions()).isEmpty();
        assertThat(service.restaurants()).isEmpty();
    }
    @Test void resolvedCandidateDoesNotPublishUnapprovedRelation() {
        long candidate = prepared(); approve(candidate, service.createChef("윤남노").id());
        entityManager.flush(); entityManager.clear();
        db.update("update mentions set verification_status='PENDING'");
        assertThat(service.candidates().get(0).disposition()).isEqualTo("RESOLVED");
        assertThat(service.restaurants()).isEmpty();
    }
    @Test void approvalCreatesSeparateRelationAndPublishesOnlyVerifiedFields() throws Exception {
        long candidate = prepared(), chef = service.createChef("윤남노").id();
        mvc.perform(post("/api/admin/candidates/" + candidate + "/mentions").with(csrf()).contentType("application/json").content(body(chef))).andExpect(status().isCreated());
        entityManager.flush(); entityManager.clear();
        var relation = service.mentions().get(0);
        assertThat(relation.candidateId()).isEqualTo(candidate);
        assertThat(relation.mentionType()).isEqualTo(MentionType.VISITED);
        assertThat(relation.timestampSeconds()).isNull();
        assertThat(relation.reviewedBy()).isEqualTo("admin");
        assertThat(service.candidates()).hasSize(1);
        assertThat(service.candidates().get(0).disposition()).isEqualTo("RESOLVED");
        mvc.perform(get("/api/restaurants")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].mentions[0].mentionType").value("VISITED"))
            .andExpect(jsonPath("$[0].mentions[0].reviewNote").doesNotExist())
            .andExpect(jsonPath("$[0].mentions[0].evidence").doesNotExist());
        mvc.perform(post("/api/admin/candidates/" + candidate + "/mentions").with(csrf()).contentType("application/json").content(body(chef))).andExpect(status().isConflict());
    }
    @Test void oneCandidateCanSupportMultipleChefs() {
        long candidate = prepared();
        approve(candidate, service.createChef("셰프 하나").id());
        approve(candidate, service.createChef("셰프 둘").id());
        assertThat(service.candidates()).hasSize(1);
        assertThat(service.mentions()).hasSize(2);
        assertThat(service.restaurants()).hasSize(1);
        assertThat(service.restaurants().get(0).mentions()).hasSize(2);
    }
    @Test void sameRelationshipCannotBeDuplicatedThroughDifferentCandidates() {
        long first = prepared(), chef = service.createChef("윤남노").id();
        service.addCandidate(videoId, "수동 후보", "", "뽀요베리");
        long second = service.candidates().get(0).id();
        matching.search(second, "뽀요베리");
        approve(first, chef);
        assertThatThrownBy(() -> approve(second, chef)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.mentions()).hasSize(1);
    }
    @Test void dismissalDoesNotCreateMentionOrRestaurant() {
        long candidate = prepared(); service.dismiss(candidate, "영상 출처 근거 부족");
        assertThat(service.candidates().get(0).disposition()).isEqualTo("DISMISSED");
        assertThat(service.mentions()).isEmpty();
        assertThat(service.restaurants()).isEmpty();
    }
    @Test void resolvedCandidateCannotBeDismissed() {
        long candidate = prepared(); approve(candidate, service.createChef("윤남노").id());
        assertThatThrownBy(() -> service.dismiss(candidate, "제외")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void onlyPlacesBelongingToCandidateCanBeApproved() {
        prepared();
        service.addCandidate(videoId, "다른 후보", "", "뽀요베리");
        long unsearched = service.candidates().get(0).id(), chef = service.createChef("윤남노").id();
        assertThatThrownBy(() -> approve(unsearched, chef)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.mentions()).isEmpty();
    }
    @Test void unknownTimestampAndZeroAreDistinct() {
        long candidate = prepared(), chef = service.createChef("윤남노").id();
        service.approve(candidate, chef, place.id(), MentionType.MENTIONED, 0, "시작 부분 언급 확인", "메모", "admin");
        assertThat(service.mentions().get(0).timestampSeconds()).isZero();
    }
    @Test void approvalRequiresExplicitTypeEvidenceAndValidTimestamp() throws Exception {
        long candidate = prepared(), chef = service.createChef("윤남노").id();
        for (String invalid : List.of(body(chef).replace("\"VISITED\"", "null"), body(chef).replace("\"방문 확인\"", "\" \""), body(chef).replace("\"timestampSeconds\":null", "\"timestampSeconds\":-1")))
            mvc.perform(post("/api/admin/candidates/" + candidate + "/mentions").with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        assertThat(service.mentions()).isEmpty();
    }
    @Test void manualCandidatesNeedSourceEvidenceAndKeepUnknownFieldsNull() {
        imported();
        service.addCandidate(videoId, "수동", "", "뽀요베리");
        var manual = service.candidates().get(0);
        assertThat(manual.sourceLine()).isNull();
        assertThat(manual.extractedAddress()).isNull();
        assertThatThrownBy(() -> service.addCandidate(videoId, "가짜", "", "없는 설명")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void searchFailurePreservesSnapshotStatusAndRecordsSafeError() {
        long candidate = prepared();
        when(apis.places(anyString())).thenThrow(new ExternalApis.ApiFailure("외부 API 연결 실패"));
        assertThatThrownBy(() -> matching.search(candidate, "뽀요베리")).isInstanceOf(ExternalApis.ApiFailure.class);
        entityManager.flush(); entityManager.clear();
        assertThat(service.candidates().get(0).matchStatus()).isEqualTo("MATCHED");
        assertThat(service.candidates().get(0).matchingErrorCode()).isEqualTo("EXTERNAL_API_FAILURE");
        assertThat(matching.saved(candidate).candidates()).hasSize(1);
        assertThat(service.mentions()).isEmpty();
    }
    @Test void initialSearchFailureIsNotNotFound() {
        long candidate = imported();
        when(apis.places(anyString())).thenThrow(new ExternalApis.ApiFailure("오류"));
        assertThatThrownBy(() -> matching.search(candidate, "뽀요베리")).isInstanceOf(ExternalApis.ApiFailure.class);
        assertThat(service.candidates().get(0).matchStatus()).isNull();
    }
    @Test void emptySuccessfulSearchIsNotFound() {
        long candidate = imported();
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(), 0, true));
        assertThat(matching.search(candidate, "뽀요베리").matchStatus()).isEqualTo("NOT_FOUND");
        assertThat(service.mentions()).isEmpty();
    }
    @Test void repeatedSearchReplacesSnapshots() {
        long candidate = prepared();
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(), 0, true));
        matching.search(candidate, "없음"); entityManager.flush(); entityManager.clear();
        assertThat(places.findByCandidateIdOrderByIdAsc(candidate)).isEmpty();
    }
    @Test @WithAnonymousUser void adminRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/candidates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/mentions")).andExpect(status().isUnauthorized());
    }
    @Test void csrfRequiredForCandidateAndRelationWrites() throws Exception {
        mvc.perform(post("/api/admin/chefs").contentType("application/json").content("{\"name\":\"셰프\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/candidates/1/mentions").contentType("application/json").content(body(1))).andExpect(status().isForbidden());
    }
    @Test @WithAnonymousUser void realDatabaseLoginAndPasswordHash() throws Exception {
        String hash = db.queryForObject("select password_hash from admin_users where username='admin'", String.class);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("integration-test-password-only");
        mvc.perform(post("/api/login").with(csrf()).param("username","admin").param("password","incorrect")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/login").with(csrf()).param("username","admin").param("password","integration-test-password-only")).andExpect(status().isNoContent());
    }
    @Test void failedVideoImportDoesNotCreateCandidatesOrRelations() throws Exception {
        when(apis.video(videoId)).thenThrow(new ExternalApis.ApiFailure("외부 API 요청 실패 (HTTP 403)"));
        mvc.perform(post("/api/admin/videos").with(csrf()).contentType("application/json").content("{\"url\":\"https://youtu.be/"+videoId+"\"}")).andExpect(status().isBadGateway());
        assertThat(service.candidates()).isEmpty(); assertThat(service.mentions()).isEmpty();
    }
}
