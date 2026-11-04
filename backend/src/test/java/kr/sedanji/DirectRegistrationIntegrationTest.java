package kr.sedanji;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import kr.sedanji.admin.*;
import kr.sedanji.chef.ChefService;
import kr.sedanji.mention.*;
import kr.sedanji.registration.*;
import kr.sedanji.restaurant.RestaurantService;
import kr.sedanji.restaurantcandidate.RestaurantCandidateService;
import kr.sedanji.video.VideoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.AopTestUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static kr.sedanji.Models.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No caller transaction: every success and failure checks the service's committed state. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
@WithMockUser(username="admin", roles="ADMIN")
class DirectRegistrationIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired ChefService chefs;
    @Autowired VideoService videos;
    @Autowired RestaurantService restaurants;
    @Autowired PlaceSelectionService selections;
    @Autowired DirectRegistrationFacade facade;
    @Autowired RegistrationService registrations;
    @Autowired AdminUserRepository admins;
    @Autowired kr.sedanji.importjob.ImportJobRepository jobs;
    @Autowired RestaurantCandidateService candidates;
    @Autowired CandidateMatchingService matching;
    @MockitoBean ExternalApis apis;
    @MockitoSpyBean AdminAuditEventRepository audits;
    @MockitoSpyBean AdminRequestReceiptRepository receipts;
    @MockitoSpyBean MentionService mentions;
    private record Prepared(DirectRegistrationRequest request, MockHttpSession session, String placeId) {}
    private Prepared prepare(Integer timestamp, String note) {
        String placeId = Long.toString(Math.abs(UUID.randomUUID().getMostSignificantBits()));
        String video = UUID.randomUUID().toString().replace("-", "").substring(0, 11);
        videos.saveMetadata(new Video(video, "영상", "채널", OffsetDateTime.now(), "설명란", "확인"));
        long chef = chefs.createChef("검증 셰프").id();
        var place = new Place(placeId, "식당", null, null, 127, 37, "https://place.map.kakao.com/" + placeId);
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(place), 1, true));
        var session = new MockHttpSession();
        String token = selections.search("식당", "admin", session).candidates().get(0).placeSelectionToken();
        return new Prepared(new DirectRegistrationRequest(token, chef, video, MentionType.VISITED, timestamp, note), session, placeId);
    }
    private DirectRegistrationResult approve(Prepared p) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return facade.approve(p.request(), UUID.randomUUID().toString(), "admin", p.session());
    }
    private long count(String table) { return db.queryForObject("select count(*) from " + table, Long.class); }
    private long[] counts() { return new long[]{count("restaurants"), count("mentions"), count("admin_audit_events"), count("admin_request_receipts")}; }

    @Test void createsApprovedPublishedRelationAndAuditWithNullableEvidenceAndZero() throws Exception {
        var p = prepare(0, "  ");
        long[] before = counts();
        var response = mvc.perform(post("/api/admin/mentions").session(p.session()).with(csrf())
            .header("Idempotency-Key", "create-" + p.placeId()).contentType("application/json").content(mapper.writeValueAsString(p.request())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0))
            .andExpect(jsonPath("$.verificationStatus").value("APPROVED"))
            .andExpect(jsonPath("$.publicationStatus").value("PUBLISHED")).andReturn();
        long id = mapper.readTree(response.getResponse().getContentAsString()).path("mentionId").asLong();
        assertThat(counts()).containsExactly(before[0]+1, before[1]+1, before[2]+1, before[3]+1);
        var row = db.queryForMap("select * from mentions where id=?", id);
        assertThat(row.get("restaurant_candidate_id")).isNull(); assertThat(row.get("evidence")).isNull();
        assertThat(row.get("review_note")).isNull(); assertThat(row.get("timestamp_seconds")).isEqualTo(0);
        assertThat(row.get("chef_id")).isEqualTo(p.request().chefId());
        assertThat(row.get("video_id")).isEqualTo(p.request().videoId());
        assertThat(row.get("mention_type")).isEqualTo("VISITED");
        assertThat(row.get("reviewed_by")).isNotNull(); assertThat(row.get("reviewed_at")).isNotNull();
        String audit = db.queryForObject("select after_value::text from admin_audit_events where mention_id=?", String.class, id);
        assertThat(audit).doesNotContain(p.request().placeSelectionToken());
        var publicJson = mvc.perform(get("/api/restaurants")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publicJson).doesNotContain("evidence", "reviewNote", "reviewedBy", "placeSelectionToken", "receipt");
        assertThat(restaurants.findPublishedRestaurants()).anyMatch(r -> r.id() == ((Number)row.get("restaurant_id")).longValue());
    }
    @Test void reusesExistingRestaurantWithoutOverwritingSnapshotAndPreservesNullTimestamp() {
        var p = prepare(null, " 확인 메모 ");
        long restaurant = restaurants.getOrCreate(new Place(p.placeId(), "원래 이름", "원래 주소", "원래 지번", 126, 36, "https://place.map.kakao.com/" + p.placeId())).getId();
        long before = count("restaurants");
        var result = approve(p);
        assertThat(result.restaurantId()).isEqualTo(restaurant); assertThat(count("restaurants")).isEqualTo(before);
        var row = db.queryForMap("select * from restaurants where id=?", restaurant);
        assertThat(row.get("name")).isEqualTo("원래 이름"); assertThat(row.get("road_address")).isEqualTo("원래 주소");
        assertThat(row.get("longitude")).isEqualTo(126.0);
        var mention = db.queryForMap("select timestamp_seconds,review_note from mentions where id=?", result.mentionId());
        assertThat(mention.get("timestamp_seconds")).isNull(); assertThat(mention.get("review_note")).isEqualTo("확인 메모");
    }
    @ParameterizedTest @ValueSource(strings={"PENDING", "REJECTED", "SUSPENDED", "PUBLISHED"})
    void duplicatesNeverApproveRestoreOrOverwriteExistingRelationship(String state) throws Exception {
        var p = prepare(null, "원래 메모"); var result = approve(p);
        String verification = state.equals("PENDING") || state.equals("REJECTED") ? state : "APPROVED";
        String publication = state.equals("PUBLISHED") ? "PUBLISHED" : "SUSPENDED";
        db.update("update mentions set verification_status=?,publication_status=? where id=?", verification, publication, result.mentionId());
        long[] before = counts();
        mvc.perform(post("/api/admin/mentions").session(p.session()).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType("application/json").content(mapper.writeValueAsString(p.request())))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_RELATION"))
            .andExpect(jsonPath("$.existingMentionId").value(result.mentionId()));
        assertThat(counts()).containsExactly(before);
        var row = db.queryForMap("select verification_status,publication_status,review_note from mentions where id=?", result.mentionId());
        assertThat(row.get("verification_status")).isEqualTo(verification); assertThat(row.get("publication_status")).isEqualTo(publication);
        assertThat(row.get("review_note")).isEqualTo("원래 메모");
        var published = restaurants.findPublishedRestaurants().stream().filter(r -> r.id() == result.restaurantId()).toList();
        assertThat(published).hasSize(state.equals("PUBLISHED") ? 1 : 0);
    }
    @Test void replayWorksAfterSessionSnapshotLossButDifferentRequestConflicts() {
        var p = prepare(null, null); String key = UUID.randomUUID().toString();
        var first = facade.approve(p.request(), key, "admin", p.session()); long[] before = counts();
        assertThat(facade.approve(p.request(), key, "admin", new MockHttpSession())).isEqualTo(first);
        var changed = new DirectRegistrationRequest(p.request().placeSelectionToken(), p.request().chefId(), p.request().videoId(), MentionType.REGULAR, null, null);
        assertThatThrownBy(() -> facade.approve(changed, key, "admin", p.session())).isInstanceOfSatisfying(DirectRegistrationException.class,
            ex -> assertThat(ex.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(counts()).containsExactly(before);
        db.update("update admin_request_receipts set created_at=now()-interval '25 hours',expires_at=now()-interval '1 hour' where idempotency_key=?", key);
        assertThatThrownBy(() -> facade.approve(p.request(), key, "admin", p.session())).isInstanceOfSatisfying(DirectRegistrationException.class,
            ex -> assertThat(ex.code()).isEqualTo("DUPLICATE_RELATION"));
    }
    @ParameterizedTest @ValueSource(strings={"mention", "audit", "receipt"})
    void storageFailureRollsBackNewRestaurantAndAllBusinessWrites(String step) {
        var p = prepare(null, null); long[] before = counts();
        MentionService mentionTarget = AopTestUtils.getUltimateTargetObject(mentions);
        if (step.equals("mention")) doThrow(new IllegalStateException("injected failure")).when(mentionTarget)
            .approveDirect(any(), any(), any(), any(), any(), any(), any());
        if (step.equals("audit")) doThrow(new IllegalStateException("injected failure")).when(audits).saveAndFlush(any());
        if (step.equals("receipt")) doThrow(new IllegalStateException("injected failure")).when(receipts).saveAndFlush(any());
        assertThatThrownBy(() -> approve(p)).isInstanceOf(IllegalStateException.class);
        assertThat(counts()).containsExactly(before);
        assertThat(videos.findExisting(p.request().videoId())).isPresent();
        assertThat(chefs.getRequired(p.request().chefId())).isNotNull();
    }
    @Test void invalidInputMissingReferencesAndExpiredTokenHaveNoWrites() throws Exception {
        var p = prepare(null, null); long[] before = counts();
        var base = mapper.valueToTree(p.request());
        for (String field : List.of("chefId", "videoId", "mentionType", "placeSelectionToken")) {
            var body = ((com.fasterxml.jackson.databind.node.ObjectNode) base).deepCopy(); body.remove(field);
            mvc.perform(post("/api/admin/mentions").session(p.session()).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json").content(body.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String value : List.of("-1", "1.5", "2147483648", "\"bad\"")) {
            String json = mapper.writeValueAsString(p.request()).replace("\"timestampSeconds\":null", "\"timestampSeconds\":" + value);
            mvc.perform(post("/api/admin/mentions").session(p.session()).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json").content(json)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        var invalidType = ((com.fasterxml.jackson.databind.node.ObjectNode) base).deepCopy().put("mentionType", "UNKNOWN");
        mvc.perform(post("/api/admin/mentions").session(p.session()).with(csrf()).header("Idempotency-Key", "invalid-type")
            .contentType("application/json").content(invalidType.toString())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/mentions").session(p.session()).with(csrf()).contentType("application/json")
            .content(base.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        for (String field : List.of("chefId", "videoId")) {
            var body = ((com.fasterxml.jackson.databind.node.ObjectNode) base).deepCopy();
            if (field.equals("chefId")) body.put(field, Long.MAX_VALUE); else body.put(field, "-----------");
            mvc.perform(post("/api/admin/mentions").session(p.session()).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType("application/json").content(body.toString())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        mvc.perform(post("/api/admin/mentions").session(new MockHttpSession()).with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
            .contentType("application/json").content(base.toString())).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PLACE_SELECTION_EXPIRED"));
        assertThat(counts()).containsExactly(before);
    }
    @Test void newEndpointsPreserveAuthenticationAuthorizationAndCsrf() throws Exception {
        var p = prepare(null, null); long[] before = counts(); String body = mapper.writeValueAsString(p.request());
        mvc.perform(get("/api/admin/places").param("query", "식당").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/mentions").with(anonymous()).with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/mentions").with(user("viewer").roles("USER")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/mentions").contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/videos/metadata").contentType("application/json").content("{\"url\":\"https://youtu.be/RMTJPFKRbEk\"}")).andExpect(status().isForbidden());
        assertThat(counts()).containsExactly(before);
    }
    @Test void metadataEndpointHasNoExtractionAndReusesExistingWithoutExternalLookup() throws Exception {
        String id = "MetaOnly001";
        long jobs = count("import_jobs"), candidateCount = count("restaurant_candidates");
        when(apis.video(id)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new Video(id, "메타데이터", "채널", OffsetDateTime.now(), "🥘 식당\n📍서울 마포구", "확인");
        });
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/admin/videos/metadata").with(csrf()).contentType("application/json")
            .content("{\"url\":\"https://youtu.be/" + id + "\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("메타데이터"));
        verify(apis, times(1)).video(id);
        assertThat(count("import_jobs")).isEqualTo(jobs); assertThat(count("restaurant_candidates")).isEqualTo(candidateCount);
    }
    @Test void placeSearchIsReadOnlyAndExternalFailureKeepsPreviousSelection() throws Exception {
        var p = prepare(null, null); long[] before = counts();
        mvc.perform(get("/api/admin/places").session(p.session()).param("query", "식당"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.candidates[0].placeSelectionToken").isString())
            .andExpect(jsonPath("$.candidates[0].place.id").value(p.placeId()))
            .andExpect(jsonPath("$.candidates[0].place.address").isEmpty())
            .andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.complete").value(true));
        when(apis.places(anyString())).thenThrow(new ExternalApis.ApiFailure("외부 API 연결 또는 응답 형식 오류"));
        mvc.perform(get("/api/admin/places").session(p.session()).param("query", "실패"))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.message").value("외부 API 연결 또는 응답 형식 오류"));
        assertThat(selections.resolve(p.request().placeSelectionToken(), "admin", p.session()).id()).isEqualTo(p.placeId());
        assertThat(counts()).containsExactly(before);
    }
    @Test void inactiveAdministratorCannotApproveOrReplay() {
        var p = prepare(null, null); String other = anotherAdmin(); var q = forAdmin(p, other, p.request().chefId());
        db.update("update admin_users set enabled=false where username=?", other); long[] before = counts();
        assertThatThrownBy(() -> facade.approve(q.request(), UUID.randomUUID().toString(), other, q.session()))
            .isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
        assertThat(counts()).containsExactly(before);
    }
    private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var tasks = new ArrayList<Future<Object>>();
            for (var action : List.of(first, second)) tasks.add(executor.submit(() -> {
                ready.countDown(); if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
                try { return action.call(); } catch (RuntimeException ex) { return ex; }
            }));
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS));
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    private String anotherAdmin() {
        String name = "admin-" + UUID.randomUUID(); admins.saveAndFlush(new AdminUserEntity(name, "test-only-hash")); return name;
    }
    private Prepared forAdmin(Prepared p, String admin, Long chef) {
        var session = new MockHttpSession(); String token = selections.search("식당", admin, session).candidates().get(0).placeSelectionToken();
        return new Prepared(new DirectRegistrationRequest(token, chef, p.request().videoId(), MentionType.VISITED, null, null), session, p.placeId());
    }
    @Test void concurrentSameRelationAcrossAdminsHasOneSuccessAndOneConflict() throws Exception {
        var p = prepare(null, null); String other = anotherAdmin(); var q = forAdmin(p, other, p.request().chefId());
        var results = race(() -> approve(p), () -> facade.approve(q.request(), UUID.randomUUID().toString(), other, q.session()));
        assertThat(results.stream().filter(DirectRegistrationResult.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(DirectRegistrationException.class::isInstance).map(DirectRegistrationException.class::cast))
            .singleElement().satisfies(ex -> { assertThat(ex.code()).isEqualTo("DUPLICATE_RELATION"); assertThat(ex.existingMentionId()).isNotNull(); });
        assertThat(db.queryForObject("select count(*) from restaurants where kakao_place_id=?", Long.class, p.placeId())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from mentions m join restaurants r on m.restaurant_id=r.id where r.kakao_place_id=?", Long.class, p.placeId())).isEqualTo(1);
    }
    @Test void concurrentDifferentRelationshipsReuseOneRestaurant() throws Exception {
        var p = prepare(null, null); String other = anotherAdmin(); var q = forAdmin(p, other, chefs.createChef("다른 셰프").id());
        var results = race(() -> approve(p), () -> facade.approve(q.request(), UUID.randomUUID().toString(), other, q.session()));
        assertThat(results).allMatch(DirectRegistrationResult.class::isInstance);
        assertThat(db.queryForObject("select count(*) from restaurants where kakao_place_id=?", Long.class, p.placeId())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from mentions m join restaurants r on m.restaurant_id=r.id where r.kakao_place_id=?", Long.class, p.placeId())).isEqualTo(2);
    }
    @Test void concurrentIdempotentReplayCreatesOneAuditAndReceipt() throws Exception {
        var p = prepare(null, null); String key = UUID.randomUUID().toString(); long[] before = counts();
        var results = race(() -> facade.approve(p.request(), key, "admin", p.session()), () -> facade.approve(p.request(), key, "admin", p.session()));
        assertThat(results.get(0)).isInstanceOf(DirectRegistrationResult.class).isEqualTo(results.get(1));
        assertThat(counts()).containsExactly(before[0]+1, before[1]+1, before[2]+1, before[3]+1);
    }
    @Test void candidateApprovalRacePreservesUniqueRelationAndLosingCandidateState() throws Exception {
        var p = prepare(null, null);
        String id = p.request().videoId();
        // Explicit manual evidence belongs to the stored video; no invented relation evidence.
        jobs.saveAndFlush(new kr.sedanji.importjob.ImportJobEntity(videos.getRequired(id), OffsetDateTime.now()));
        candidates.addManualCandidate(id, "식당", "", "설명란");
        long candidateId = candidates.findCandidates().stream().filter(c -> c.videoId().equals(id)).findFirst().orElseThrow().id();
        // Candidate snapshots retain their existing non-null address contract.
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(new Place(p.placeId(), "식당", "", "", 127, 37,
            "https://place.map.kakao.com/" + p.placeId())), 1, true));
        matching.search(candidateId, "식당");
        var results = race(() -> approve(p), () -> {
            registrations.approveFromCandidate(candidateId, p.request().chefId(), p.placeId(), MentionType.VISITED, null, "설명란", null, "admin");
            return "candidate-approved";
        });
        boolean candidateWon = results.contains("candidate-approved");
        assertThat(results.stream().filter(r -> r instanceof RuntimeException).count()).isEqualTo(1);
        assertThat(db.queryForObject("select disposition from restaurant_candidates where id=?", String.class, candidateId))
            .isEqualTo(candidateWon ? "RESOLVED" : "OPEN");
        assertThat(db.queryForObject("select count(*) from mentions m join restaurants r on m.restaurant_id=r.id where r.kakao_place_id=?", Long.class, p.placeId())).isEqualTo(1);
    }
}
