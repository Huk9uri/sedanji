package kr.sedanji;

import java.time.OffsetDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import kr.sedanji.mention.MentionType;
import kr.sedanji.chef.ChefService;
import kr.sedanji.restaurant.RestaurantService;
import kr.sedanji.video.VideoService;
import kr.sedanji.restaurantcandidate.PlaceCandidateRepository;
import kr.sedanji.restaurantcandidate.RestaurantCandidateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.IllegalTransactionStateException;
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
    @Autowired ChefService chefService;
    @Autowired RestaurantService restaurantService;
    @Autowired VideoService videoService;
    @Autowired RestaurantCandidateService candidateService;
    @Autowired PlatformTransactionManager transactionManager;
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
        return candidateService.findCandidates().get(0).id();
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
        assertThat(candidateService.findCandidates()).hasSize(1);
        assertThat(service.mentions()).isEmpty();
        assertThat(restaurantService.findPublishedRestaurants()).isEmpty();
        assertThat(chefService.findChefs()).isEmpty();
        assertThat(db.queryForObject("select count(*) from import_jobs", Integer.class)).isEqualTo(1);
        verify(apis, times(1)).video(videoId);
    }
    @Test void metadataCanBeStoredWithoutCreatingImportJobsOrCandidates() {
        videoService.saveMetadata(new Video(videoId, "메타데이터만 등록", "채널", OffsetDateTime.now(), "식당 설명 원문", "확인"));
        assertThat(videoService.findExisting(videoId)).isPresent();
        assertThat(candidateService.findCandidates()).isEmpty();
        assertThat(service.mentions()).isEmpty();
        assertThat(db.queryForObject("select count(*) from import_jobs", Integer.class)).isZero();
        verifyNoInteractions(apis);
    }
    @Test void savingExistingVideoReusesItWithoutOverwritingMetadata() {
        var first = videoService.saveMetadata(new Video(videoId, "기존 제목", "채널", OffsetDateTime.now(), "원문", "확인"));
        var reused = videoService.saveMetadata(new Video(videoId, "바뀐 제목", "다른 채널", OffsetDateTime.now(), "변경 원문", "확인"));
        assertThat(reused.getId()).isEqualTo(first.getId());
        assertThat(reused.getTitle()).isEqualTo("기존 제목");
        assertThat(db.queryForObject("select count(*) from videos", Integer.class)).isEqualTo(1);
        verifyNoInteractions(apis);
    }
    @Test void externalMetadataLookupDoesNotRunInsideDatabaseTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        when(apis.video(videoId)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new Video(videoId, "영상", "채널", OffsetDateTime.now(), "", "확인");
        });
        assertThat(videoService.fetchMetadata(videoId).id()).isEqualTo(videoId);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    }
    @Test void adminVideoListKeepsExistingResponseFields() throws Exception {
        videoService.saveMetadata(new Video(videoId, "목록 영상", "채널", OffsetDateTime.now(), "설명", "확인"));
        mvc.perform(get("/api/admin/videos")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(videoId))
            .andExpect(jsonPath("$[0].title").value("목록 영상"))
            .andExpect(jsonPath("$[0].channelTitle").value("채널"))
            .andExpect(jsonPath("$[0].description").value("설명"))
            .andExpect(jsonPath("$[0].publishedAt").exists())
            .andExpect(jsonPath("$[0].extractionWarning").value("확인"));
    }
    @Test void repeatedExtractionKeepsOriginalCandidateEvidence() {
        long id = imported();
        var original = candidateService.findCandidates().get(0);
        var job = candidateService.getForUpdate(id).getImportJob();
        candidateService.addExtractedCandidate(job, new Extracted(original.extractedName(), "다른 주소",
            original.sourceLine(), "다른 원문"));
        assertThat(candidateService.findCandidates()).containsExactly(original);
    }
    @Test void candidateApiKeepsListAndDismissalBehavior() throws Exception {
        long id = imported();
        mvc.perform(get("/api/admin/candidates")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].extractedName").value("뽀요베리"))
            .andExpect(jsonPath("$[0].disposition").value("OPEN"));
        mvc.perform(post("/api/admin/candidates/" + id + "/dismiss").with(csrf())
            .contentType("application/json").content("{\"note\":\"제외 사유\"}"))
            .andExpect(status().isNoContent());
        assertThat(candidateService.findCandidates().get(0).disposition()).isEqualTo("DISMISSED");
        mvc.perform(get("/api/admin/candidates/" + Long.MAX_VALUE + "/places")).andExpect(status().isNotFound());
    }
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void candidateLocksRequireCallerTransaction() {
        assertThatThrownBy(() -> candidateService.getForUpdate(1)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> candidateService.resolveCandidate(1)).isInstanceOf(IllegalTransactionStateException.class);
    }
    @Test void approvalAndCandidateResolutionRollbackTogether() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            long id = prepared();
            approve(id, chefService.createChef("롤백 확인 셰프").id());
            assertThat(candidateService.findCandidates().get(0).disposition()).isEqualTo("RESOLVED");
            throw new IllegalStateException("강제 롤백 확인");
        })).isInstanceOf(IllegalStateException.class).hasMessage("강제 롤백 확인");
        assertThat(candidateService.findCandidates()).isEmpty();
        assertThat(service.mentions()).isEmpty();
        assertThat(restaurantService.findPublishedRestaurants()).isEmpty();
        assertThat(db.queryForObject("select count(*) from restaurants", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from videos", Integer.class)).isZero();
    }
    @Test void matchingNeverApprovesRelationship() {
        prepared();
        assertThat(candidateService.findCandidates().get(0).matchStatus()).isEqualTo("MATCHED");
        assertThat(candidateService.findCandidates().get(0).disposition()).isEqualTo("OPEN");
        assertThat(service.mentions()).isEmpty();
        assertThat(restaurantService.findPublishedRestaurants()).isEmpty();
    }
    @Test void resolvedCandidateDoesNotPublishUnapprovedRelation() {
        long candidate = prepared(); approve(candidate, chefService.createChef("윤남노").id());
        entityManager.flush(); entityManager.clear();
        db.update("update mentions set verification_status='PENDING',publication_status='SUSPENDED'");
        assertThat(candidateService.findCandidates().get(0).disposition()).isEqualTo("RESOLVED");
        assertThat(restaurantService.findPublishedRestaurants()).isEmpty();
    }
    @Test void approvalCreatesSeparateRelationAndPublishesOnlyVerifiedFields() throws Exception {
        long candidate = prepared(), chef = chefService.createChef("윤남노").id();
        mvc.perform(post("/api/admin/candidates/" + candidate + "/mentions").with(csrf()).contentType("application/json").content(body(chef))).andExpect(status().isCreated());
        entityManager.flush(); entityManager.clear();
        var relation = service.mentions().get(0);
        assertThat(relation.candidateId()).isEqualTo(candidate);
        assertThat(relation.mentionType()).isEqualTo(MentionType.VISITED);
        assertThat(relation.timestampSeconds()).isNull();
        assertThat(relation.reviewedBy()).isEqualTo("admin");
        assertThat(candidateService.findCandidates()).hasSize(1);
        assertThat(candidateService.findCandidates().get(0).disposition()).isEqualTo("RESOLVED");
        mvc.perform(get("/api/restaurants")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].mentions[0].mentionType").value("VISITED"))
            .andExpect(jsonPath("$[0].mentions[0].reviewNote").doesNotExist())
            .andExpect(jsonPath("$[0].mentions[0].evidence").doesNotExist());
        mvc.perform(post("/api/admin/candidates/" + candidate + "/mentions").with(csrf()).contentType("application/json").content(body(chef))).andExpect(status().isConflict());
    }
    @Test void oneCandidateCanSupportMultipleChefs() {
        long candidate = prepared();
        approve(candidate, chefService.createChef("셰프 하나").id());
        approve(candidate, chefService.createChef("셰프 둘").id());
        assertThat(candidateService.findCandidates()).hasSize(1);
        assertThat(service.mentions()).hasSize(2);
        assertThat(restaurantService.findPublishedRestaurants()).hasSize(1);
        assertThat(restaurantService.findPublishedRestaurants().get(0).mentions()).hasSize(2);
    }
    @Test void sameRelationshipCannotBeDuplicatedThroughDifferentCandidates() {
        long first = prepared(), chef = chefService.createChef("윤남노").id();
        candidateService.addManualCandidate(videoId, "수동 후보", "", "뽀요베리");
        long second = candidateService.findCandidates().get(0).id();
        matching.search(second, "뽀요베리");
        approve(first, chef);
        assertThatThrownBy(() -> approve(second, chef)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(service.mentions()).hasSize(1);
    }
    @Test void sameNameChefsAreIndependentPeople() {
        var first = chefService.createChef("동명셰프");
        var second = chefService.createChef("동명셰프");
        assertThat(second.id()).isNotEqualTo(first.id());
    }
    @Test void optionalReviewNoteCanBeOmittedFromApprovalRequest() throws Exception {
        long candidate = prepared();
        long chef = chefService.createChef("선택 메모 셰프").id();
        mvc.perform(post("/api/admin/candidates/" + candidate + "/mentions").with(csrf())
            .contentType("application/json").content(body(chef).replace(",\"note\":\"출처와 지점 확인\"", "")))
            .andExpect(status().isCreated());
        assertThat(db.queryForObject("select review_note from mentions", String.class)).isNull();
        assertThat(restaurantService.findPublishedRestaurants()).hasSize(1);
    }
    @Test void suspendedRelationshipIsNotPublishedAndCannotBeDuplicatedWithAnotherType() {
        long candidate = prepared();
        long chef = chefService.createChef("공개 중단 셰프").id();
        approve(candidate, chef);
        db.update("update mentions set publication_status='SUSPENDED',version=version+1");
        entityManager.clear();
        assertThat(restaurantService.findPublishedRestaurants()).isEmpty();
        assertThatThrownBy(() -> service.approve(candidate, chef, place.id(), MentionType.RECOMMENDED, null, "새 유형", null, "admin"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("409");
    }
    @Test void dismissalDoesNotCreateMentionOrRestaurant() {
        long candidate = prepared(); candidateService.dismissCandidate(candidate, "영상 출처 근거 부족");
        assertThat(candidateService.findCandidates().get(0).disposition()).isEqualTo("DISMISSED");
        assertThat(service.mentions()).isEmpty();
        assertThat(restaurantService.findPublishedRestaurants()).isEmpty();
    }
    @Test void resolvedCandidateCannotBeDismissed() {
        long candidate = prepared(); approve(candidate, chefService.createChef("윤남노").id());
        assertThatThrownBy(() -> candidateService.dismissCandidate(candidate, "제외")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void onlyPlacesBelongingToCandidateCanBeApproved() {
        prepared();
        candidateService.addManualCandidate(videoId, "다른 후보", "", "뽀요베리");
        long unsearched = candidateService.findCandidates().get(0).id(), chef = chefService.createChef("윤남노").id();
        assertThatThrownBy(() -> approve(unsearched, chef)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.mentions()).isEmpty();
    }
    @Test void unknownTimestampAndZeroAreDistinct() {
        long candidate = prepared(), chef = chefService.createChef("윤남노").id();
        service.approve(candidate, chef, place.id(), MentionType.MENTIONED, 0, "시작 부분 언급 확인", "메모", "admin");
        assertThat(service.mentions().get(0).timestampSeconds()).isZero();
    }
    @Test void approvalRequiresExplicitTypeEvidenceAndValidTimestamp() throws Exception {
        long candidate = prepared(), chef = chefService.createChef("윤남노").id();
        for (String invalid : List.of(body(chef).replace("\"VISITED\"", "null"), body(chef).replace("\"방문 확인\"", "\" \""), body(chef).replace("\"timestampSeconds\":null", "\"timestampSeconds\":-1")))
            mvc.perform(post("/api/admin/candidates/" + candidate + "/mentions").with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        assertThat(service.mentions()).isEmpty();
    }
    @Test void manualCandidatesNeedSourceEvidenceAndKeepUnknownFieldsNull() {
        imported();
        candidateService.addManualCandidate(videoId, "수동", "", "뽀요베리");
        var manual = candidateService.findCandidates().get(0);
        assertThat(manual.sourceLine()).isNull();
        assertThat(manual.extractedAddress()).isNull();
        assertThatThrownBy(() -> candidateService.addManualCandidate(videoId, "가짜", "", "없는 설명")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void searchFailurePreservesSnapshotStatusAndRecordsSafeError() {
        long candidate = prepared();
        when(apis.places(anyString())).thenThrow(new ExternalApis.ApiFailure("외부 API 연결 실패"));
        assertThatThrownBy(() -> matching.search(candidate, "뽀요베리")).isInstanceOf(ExternalApis.ApiFailure.class);
        entityManager.flush(); entityManager.clear();
        assertThat(candidateService.findCandidates().get(0).matchStatus()).isEqualTo("MATCHED");
        assertThat(candidateService.findCandidates().get(0).matchingErrorCode()).isEqualTo("EXTERNAL_API_FAILURE");
        assertThat(matching.saved(candidate).candidates()).hasSize(1);
        assertThat(service.mentions()).isEmpty();
    }
    @Test void initialSearchFailureIsNotNotFound() {
        long candidate = imported();
        when(apis.places(anyString())).thenThrow(new ExternalApis.ApiFailure("오류"));
        assertThatThrownBy(() -> matching.search(candidate, "뽀요베리")).isInstanceOf(ExternalApis.ApiFailure.class);
        assertThat(candidateService.findCandidates().get(0).matchStatus()).isNull();
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
        assertThat(candidateService.findCandidates()).isEmpty(); assertThat(service.mentions()).isEmpty();
    }
}
