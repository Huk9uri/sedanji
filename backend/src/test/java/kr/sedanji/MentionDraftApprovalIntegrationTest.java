package kr.sedanji;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import kr.sedanji.admin.*;
import kr.sedanji.chef.ChefService;
import kr.sedanji.mention.MentionRepository;
import kr.sedanji.mentiondraft.*;
import kr.sedanji.registration.*;
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
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Independent service transactions, followed by JDBC reads of committed/rolled-back rows. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
@WithMockUser(username="admin", roles="ADMIN")
class MentionDraftApprovalIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MentionDraftService drafts;
    @Autowired MentionDraftApprovalFacade approvals;
    @Autowired DirectRegistrationFacade direct;
    @Autowired RegistrationService registrations;
    @Autowired kr.sedanji.importjob.ImportJobRepository jobs;
    @Autowired kr.sedanji.restaurantcandidate.RestaurantCandidateService candidates;
    @Autowired CandidateMatchingService matching;
    @Autowired PlaceSelectionService selections;
    @Autowired ChefService chefs;
    @Autowired VideoService videos;
    @Autowired AdminUserRepository admins;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper mapper;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired MockMvc mvc;
    @MockitoBean ExternalApis apis;
    @MockitoSpyBean MentionRepository mentions;
    @MockitoSpyBean MentionDraftRepository draftRepository;
    @MockitoSpyBean AdminAuditEventRepository audits;
    @MockitoSpyBean AdminRequestReceiptRepository receipts;
    @MockitoSpyBean kr.sedanji.restaurant.RestaurantService restaurants;

    private String key() { return UUID.randomUUID().toString(); }
    private MentionDraftResult prepare() {
        String place = Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
        when(apis.places(anyString())).thenReturn(new Models.PlaceSearch(List.of(
            new Models.Place(place, "승인 식당", null, null, 127, 37, "https://place.map.kakao.com/" + place)), 1, true));
        var session = new MockHttpSession();
        String token = selections.search("식당", "admin", session).candidates().get(0).placeSelectionToken();
        String video = key().replace("-", "").substring(0, 11);
        videos.saveMetadata(new Models.Video(video, "영상", "채널", OffsetDateTime.now(), "설명", "확인"));
        var body = mapper.createObjectNode().put("placeSelectionToken", token).put("chefId", chefs.createChef("셰프").id())
            .put("videoId", video).put("mentionType", "VISITED");
        var result = drafts.create(MentionDraftInput.create(body), key(), "admin", session);
        session.invalidate(); // Approval must not depend on the selection session.
        return result;
    }
    private MentionDraftResult copy(MentionDraftResult d) {
        var body = mapper.createObjectNode().put("restaurantId", d.restaurant().id()).put("chefId", d.chefId())
            .put("videoId", d.videoId()).put("mentionType", "REGULAR");
        return drafts.create(MentionDraftInput.create(body), key(), "admin", new MockHttpSession());
    }
    private MentionDraftApprovalResult approve(MentionDraftResult d, String key, String actor) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return approvals.approve(d.id(), new MentionDraftApprovalRequest(d.version()), key, actor);
    }
    private ResultActions postApproval(long id, String body, String key) throws Exception {
        return mvc.perform(post(MentionDraftApprovalFacade.path(id)).with(csrf()).header("Idempotency-Key", key)
            .contentType("application/json").content(body));
    }
    private Map<String,Object> row(String table, long id) { return db.queryForMap("select * from " + table + " where id=?", id); }
    private long count(String table) { return db.queryForObject("select count(*) from " + table, Long.class); }
    private long[] counts() { return new long[]{count("restaurants"), count("mentions"), count("admin_audit_events"), count("admin_request_receipts")}; }
    private void code(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(MentionDraftApprovalException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }
    private String actor() {
        String name = "approve-" + key(); admins.saveAndFlush(new AdminUserEntity(name, "test-only-hash")); return name;
    }

    @Test void commitsApprovalAuditReceiptAndPublicSourceAndReplaysSnapshot() throws Exception {
        var d = prepare(); var before = row("mention_drafts", d.id()); long restaurants = count("restaurants");
        String key = key();
        var response = postApproval(d.id(), "{\"expectedVersion\":0}", key).andExpect(status().isOk())
            .andExpect(jsonPath("$.draftVersion").value(1)).andExpect(jsonPath("$.mentionVersion").value(0))
            .andExpect(jsonPath("$.draftStatus").value("APPLIED")).andExpect(jsonPath("$.verificationStatus").value("APPROVED"))
            .andExpect(jsonPath("$.publicationStatus").value("PUBLISHED")).andReturn();
        var result = mapper.readValue(response.getResponse().getContentAsString(), MentionDraftApprovalResult.class);
        var mention = row("mentions", result.mentionId());
        assertThat(mention).containsEntry("chef_id", d.chefId()).containsEntry("restaurant_id", d.restaurant().id())
            .containsEntry("video_id", d.videoId()).containsEntry("mention_type", "VISITED")
            .containsEntry("timestamp_seconds", null).containsEntry("evidence", null).containsEntry("review_note", null)
            .containsEntry("restaurant_candidate_id", null).containsEntry("verification_status", "APPROVED").containsEntry("publication_status", "PUBLISHED");
        var after = row("mention_drafts", d.id());
        assertThat(after).containsEntry("status", "APPLIED").containsEntry("applied_mention_id", result.mentionId()).containsEntry("version", 1L)
            .containsEntry("created_by", before.get("created_by")).containsEntry("created_at", before.get("created_at"));
        assertThat(count("restaurants")).isEqualTo(restaurants);
        var audit = db.queryForMap("select * from admin_audit_events where draft_id=? and action='APPROVE_DRAFT'", d.id());
        assertThat(audit).containsEntry("actor_id", d.createdBy()).containsEntry("mention_id", result.mentionId()).containsEntry("restaurant_id", d.restaurant().id());
        assertThat(mapper.readTree(audit.get("before_value").toString()).get("status").asText()).isEqualTo("DRAFT");
        assertThat(mapper.readTree(audit.get("after_value").toString()).get("draft").get("version").asLong()).isEqualTo(1);
        var receipt = db.queryForMap("select * from admin_request_receipts where idempotency_key=?", key);
        assertThat(receipt).containsEntry("request_path", MentionDraftApprovalFacade.path(d.id())).containsEntry("response_status", 200);
        assertThat(mapper.readValue(receipt.get("response_body").toString(), MentionDraftApprovalResult.class)).isEqualTo(result);
        var counts = counts(); assertThat(approve(d, key, "admin")).isEqualTo(result); assertThat(counts()).isEqualTo(counts);
        // Replaying the receipt must not restore a subsequently suspended relation.
        db.update("update mentions set publication_status='SUSPENDED',version=version+1 where id=?", result.mentionId());
        assertThat(approve(d, key, "admin")).isEqualTo(result);
        assertThat(row("mentions", result.mentionId())).containsEntry("publication_status", "SUSPENDED").containsEntry("version", 1L);
        verify(apis, times(1)).places(anyString()); verifyNoMoreInteractions(apis);
    }

    @Test void incompleteVersionAlreadyAppliedAndIdempotencyPriorityHaveNoSideEffects() throws Exception {
        var d = prepare();
        drafts.patch(d.id(), MentionDraftInput.patch(mapper.readTree("{\"expectedVersion\":0,\"chefId\":null}")), "admin", new MockHttpSession());
        var before = row("mention_drafts", d.id()); var counts = counts();
        postApproval(d.id(), "{\"expectedVersion\":1}", key()).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DRAFT_INCOMPLETE")).andExpect(jsonPath("$.missingFields[0]").value("chefId"));
        assertThat(row("mention_drafts", d.id())).isEqualTo(before); assertThat(counts()).isEqualTo(counts);
        postApproval(d.id(), "{\"expectedVersion\":0}", key()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STALE_VERSION")).andExpect(jsonPath("$.currentVersion").value(1));
        var complete = prepare(); String key = key(); var result = approve(complete, key, "admin");
        counts = counts();
        code(() -> approvals.approve(complete.id(), new MentionDraftApprovalRequest(1), key, "admin"), "IDEMPOTENCY_CONFLICT");
        code(() -> approve(complete, key(), "admin"), "DRAFT_ALREADY_APPLIED");
        String other = actor(); code(() -> approve(complete, key, other), "DRAFT_ALREADY_APPLIED");
        postApproval(complete.id(), "{\"expectedVersion\":0}", key()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.currentVersion").value(1)).andExpect(jsonPath("$.appliedMentionId").value(result.mentionId()));
        assertThat(counts()).isEqualTo(counts);
        var receiptBefore = db.queryForMap("select * from admin_request_receipts where idempotency_key=?", key);
        db.update("update admin_request_receipts set created_at=now()-interval '2 days',expires_at=now()-interval '1 day' where idempotency_key=?", key);
        var expired = db.queryForMap("select * from admin_request_receipts where idempotency_key=?", key);
        code(() -> approve(complete, key, "admin"), "DRAFT_ALREADY_APPLIED");
        assertThat(db.queryForMap("select * from admin_request_receipts where idempotency_key=?", key)).isEqualTo(expired);
        assertThat(receiptBefore.get("response_body").toString()).isEqualTo(expired.get("response_body").toString());
    }

    @ParameterizedTest @ValueSource(strings={"PENDING/SUSPENDED", "REJECTED/SUSPENDED", "APPROVED/SUSPENDED", "APPROVED/PUBLISHED"})
    void allExistingStatesAndDifferentTypesRemainCompletelyUnchanged(String state) {
        var d = prepare(); var result = approve(d, key(), "admin");
        String[] parts = state.split("/");
        db.update("update mentions set verification_status=?,publication_status=?,review_note='기존 메모',evidence='기존 근거',timestamp_seconds=7 where id=?",
            parts[0], parts[1], result.mentionId());
        var duplicate = copy(d); var before = row("mentions", result.mentionId()); var draft = row("mention_drafts", duplicate.id()); var counts = counts();
        assertThatThrownBy(() -> approve(duplicate, key(), "admin")).isInstanceOfSatisfying(MentionDraftApprovalException.class,
            ex -> { assertThat(ex.code()).isEqualTo("DUPLICATE_RELATION"); assertThat(ex.details()).containsEntry("existingMentionId", result.mentionId()); });
        assertThat(row("mentions", result.mentionId())).isEqualTo(before); assertThat(row("mention_drafts", duplicate.id())).isEqualTo(draft);
        assertThat(counts()).isEqualTo(counts);
    }

    @ParameterizedTest @ValueSource(strings={"mention", "draft", "audit", "receipt"})
    void everyLateFailureRollsBackAllApprovalWritesAndExpiredReceiptDeletion(String stage) {
        var d = prepare(); String key = key();
        db.update("insert into admin_request_receipts(admin_id,idempotency_key,request_path,request_hash,response_status,response_body,created_at,expires_at) values (?,?,?, ?,201,'{}',now()-interval '2 days',now()-interval '1 day')",
            d.createdBy(), key, MentionDraftService.PATH, "a".repeat(64));
        var before = row("mention_drafts", d.id()); var counts = counts();
        var expired = db.queryForMap("select * from admin_request_receipts where idempotency_key=?", key);
        if (stage.equals("mention")) doAnswer(inv -> { em.persist(inv.getArgument(0)); em.flush(); throw new IllegalStateException("injected"); }).when(mentions).saveAndFlush(any());
        if (stage.equals("draft")) doThrow(new IllegalStateException("injected")).when(draftRepository).flush();
        if (stage.equals("audit")) doThrow(new IllegalStateException("injected")).when(audits).saveAndFlush(any());
        if (stage.equals("receipt")) doThrow(new IllegalStateException("injected")).when(receipts).saveAndFlush(any());
        assertThatThrownBy(() -> approve(d, key, "admin")).isInstanceOf(RuntimeException.class);
        assertThat(row("mention_drafts", d.id())).isEqualTo(before); assertThat(counts()).isEqualTo(counts);
        assertThat(db.queryForMap("select * from admin_request_receipts where idempotency_key=?", key)).isEqualTo(expired);
    }

    @Test void sameKeyConcurrentReplayAndDifferentAdminsSameDraftSerialize() throws Exception {
        var d = prepare(); String key = key();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> approve(d, key, "admin")); var second = pool.submit(() -> approve(d, key, "admin"));
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
            var next = prepare(); String a = actor(), b = actor(); var gate = new CountDownLatch(1);
            Callable<String> one = () -> { gate.await(); try { approve(next, key(), a); return "OK"; } catch (MentionDraftApprovalException ex) { return ex.code(); } };
            Callable<String> two = () -> { gate.await(); try { approve(next, key(), b); return "OK"; } catch (MentionDraftApprovalException ex) { return ex.code(); } };
            var x = pool.submit(one); var y = pool.submit(two); gate.countDown();
            assertThat(List.of(x.get(15, TimeUnit.SECONDS), y.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK", "DRAFT_ALREADY_APPLIED");
            assertThat(db.queryForObject("select count(*) from admin_audit_events where draft_id=? and action='APPROVE_DRAFT'", Long.class, next.id())).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void controlledUniqueRaceQueriesOnlyAfterRollbackAndLeavesLoserDraftUntouched() throws Exception {
        var first = prepare(); var second = copy(first); String a = actor(), b = actor();
        var barrier = new CyclicBarrier(2); var inTransaction = new CopyOnWriteArrayList<Boolean>();
        doAnswer(inv -> {
            Optional<Long> result = em.createQuery("select m.id from MentionEntity m where m.chef.id=:chef and m.restaurant.id=:restaurant and m.video.id=:video", Long.class)
                .setParameter("chef", first.chefId()).setParameter("restaurant", first.restaurant().id()).setParameter("video", first.videoId())
                .getResultList().stream().findFirst();
            boolean active = TransactionSynchronizationManager.isActualTransactionActive(); inTransaction.add(active);
            if (active && result.isEmpty()) barrier.await(10, TimeUnit.SECONDS);
            return result;
        }).when(mentions).findStoredRelationshipId(first.chefId(), first.restaurant().id(), first.videoId());
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> x = () -> { try { approve(first, key(), a); return "OK"; } catch (MentionDraftApprovalException ex) { return ex.code(); } };
            Callable<String> y = () -> { try { approve(second, key(), b); return "OK"; } catch (MentionDraftApprovalException ex) { return ex.code(); } };
            var fx = pool.submit(x); var fy = pool.submit(y);
            assertThat(List.of(fx.get(20, TimeUnit.SECONDS), fy.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK", "DUPLICATE_RELATION");
            assertThat(inTransaction).containsExactly(true, true, false);
            assertThat(db.queryForObject("select count(*) from mentions where chef_id=? and restaurant_id=? and video_id=?", Long.class,
                first.chefId(), first.restaurant().id(), first.videoId())).isEqualTo(1);
            assertThat(List.of(row("mention_drafts", first.id()).get("status"), row("mention_drafts", second.id()).get("status")))
                .containsExactlyInAnyOrder("APPLIED", "DRAFT");
        } finally { pool.shutdownNow(); }
    }

    @Test void strictHttpAndSecurityAndInactiveReplayNeverWrite() throws Exception {
        var d = prepare(); var counts = counts();
        for (String body : List.of("{}", "{\"expectedVersion\":null}", "{\"expectedVersion\":\"0\"}", "{\"expectedVersion\":0.1}",
            "{\"expectedVersion\":-1}", "{\"expectedVersion\":9223372036854775808}", "{\"expectedVersion\":0,\"publicationStatus\":\"PUBLISHED\"}"))
            postApproval(d.id(), body, key()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        postApproval(0, "{\"expectedVersion\":0}", key()).andExpect(status().isBadRequest());
        postApproval(Long.MAX_VALUE, "{\"expectedVersion\":0}", key()).andExpect(status().isNotFound());
        postApproval(d.id(), "{\"expectedVersion\":0}", " ").andExpect(status().isBadRequest());
        mvc.perform(post(MentionDraftApprovalFacade.path(d.id())).with(anonymous()).with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(post(MentionDraftApprovalFacade.path(d.id())).with(user("user").roles("USER")).with(csrf()).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(content().string(""));
        mvc.perform(post(MentionDraftApprovalFacade.path(d.id())).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(content().string(""));
        assertThat(counts()).isEqualTo(counts);
        String actor = actor(), key = key(); approve(d, key, actor); var after = counts();
        db.update("update admin_users set enabled=false where username=?", actor);
        code(() -> approve(d, key, actor), "ADMIN_INACTIVE"); assertThat(counts()).isEqualTo(after);
    }

    @Test void zeroTimestampOptionalNoteCrossPathKeysAndCorrectionAreHandled() throws Exception {
        var d = prepare();
        var patched = drafts.patch(d.id(), MentionDraftInput.patch(mapper.readTree("{\"expectedVersion\":0,\"timestampSeconds\":0}")), "admin", new MockHttpSession());
        String key = key(); var result = approve(patched, key, "admin");
        assertThat(row("mentions", result.mentionId())).containsEntry("timestamp_seconds", 0).containsEntry("review_note", null);
        var second = prepare(); var counts = counts();
        code(() -> approve(second, key, "admin"), "IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(() -> drafts.create(MentionDraftInput.create(mapper.createObjectNode().put("restaurantId", second.restaurant().id())),
            key, "admin", new MockHttpSession())).isInstanceOfSatisfying(MentionDraftException.class,
                ex -> assertThat(ex.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        String createKey = key();
        drafts.create(MentionDraftInput.create(mapper.createObjectNode().put("restaurantId", second.restaurant().id())), createKey, "admin", new MockHttpSession());
        code(() -> approve(second, createKey, "admin"), "IDEMPOTENCY_CONFLICT");
        assertThat(counts()[1]).isEqualTo(counts[1]);
        long correction = db.queryForObject("insert into mention_drafts(restaurant_id,kind,target_mention_id,base_version,change_reason,created_by,updated_by) values (?,'CORRECTION',?,0,'정정',?,?) returning id",
            Long.class, d.restaurant().id(), result.mentionId(), d.createdBy(), d.createdBy());
        postApproval(correction, "{\"expectedVersion\":0}", key()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest @ValueSource(strings={"chefId", "videoId", "mentionType"})
    void everyMissingReferenceFieldIsReportedWithoutWrites(String field) throws Exception {
        var d = prepare();
        var patch = mapper.createObjectNode().put("expectedVersion", 0).putNull(field);
        drafts.patch(d.id(), MentionDraftInput.patch(patch), "admin", new MockHttpSession());
        var before = row("mention_drafts", d.id()); var counts = counts();
        postApproval(d.id(), "{\"expectedVersion\":1}", key()).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.missingFields[0]").value(field));
        assertThat(row("mention_drafts", d.id())).isEqualTo(before); assertThat(counts()).isEqualTo(counts);
    }

    @Test void oversizedStoredNoteIsRejectedAndStorageErrorsAreSafe() throws Exception {
        var d = prepare(); db.update("update mention_drafts set verification_note=? where id=?", "x".repeat(2001), d.id());
        var counts = counts();
        postApproval(d.id(), "{\"expectedVersion\":0}", key()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(counts()).isEqualTo(counts);
        db.update("update mention_drafts set verification_note=null where id=?", d.id());
        doThrow(new org.springframework.dao.DataIntegrityViolationException("private database detail")).when(audits).saveAndFlush(any());
        postApproval(d.id(), "{\"expectedVersion\":0}", key()).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database"))));
        assertThat(counts()).isEqualTo(counts);
    }

    @ParameterizedTest @ValueSource(strings={"direct/draft", "draft/direct", "candidate/draft", "draft/candidate"})
    void controlledCrossEntryUniqueRacesRollBackTheLosingBusinessPath(String order) throws Exception {
        var d = prepare(); String draftActor = actor(), otherActor = actor();
        String otherPath = order.contains("direct") ? "direct" : "candidate";
        String placeId = d.restaurant().kakaoPlaceId();
        when(apis.places(anyString())).thenReturn(new Models.PlaceSearch(List.of(new Models.Place(placeId, "변경 금지", "", "", 127, 37,
            "https://place.map.kakao.com/" + placeId)), 1, true));
        var session = new MockHttpSession();
        String token = selections.search("식당", otherActor, session).candidates().get(0).placeSelectionToken();
        jobs.saveAndFlush(new kr.sedanji.importjob.ImportJobEntity(videos.getRequired(d.videoId()), OffsetDateTime.now()));
        candidates.addManualCandidate(d.videoId(), "식당", "", "설명");
        long candidate = candidates.findCandidates().stream().filter(c -> c.videoId().equals(d.videoId())).findFirst().orElseThrow().id();
        matching.search(candidate, "식당");
        String candidateBefore = db.queryForObject("select disposition from restaurant_candidates where id=?", String.class, candidate);
        var restaurantBefore = row("restaurants", d.restaurant().id()); var draftBefore = row("mention_drafts", d.id());
        long[] counts = counts();
        var loserReachedInsert = new CountDownLatch(1); var winnerCommitted = new CountDownLatch(1);
        var role = new ThreadLocal<String>();
        doAnswer(inv -> {
            if ("loser".equals(role.get())) {
                loserReachedInsert.countDown();
                if (!winnerCommitted.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("winner timeout");
            }
            Object entity = inv.getArgument(0);
            try { em.persist(entity); em.flush(); return entity; }
            catch (RuntimeException ex) {
                // The interception replaces Spring Data's write, including its exception translation.
                var translated = new org.springframework.orm.jpa.vendor.HibernateJpaDialect().translateExceptionIfPossible(ex);
                throw translated == null ? ex : translated;
            }
        }).when(mentions).saveAndFlush(any());
        String draftKey = key(), otherKey = key();
        Callable<Object> draftCall = () -> approve(d, draftKey, draftActor);
        Callable<Object> otherCall = () -> {
            if (otherPath.equals("direct")) return direct.approve(new DirectRegistrationRequest(token, d.chefId(), d.videoId(),
                kr.sedanji.mention.MentionType.REGULAR, null, null), otherKey, otherActor, session);
            registrations.approveFromCandidate(candidate, d.chefId(), placeId, kr.sedanji.mention.MentionType.VISITED, null, "설명", null, otherActor);
            return "candidate";
        };
        boolean draftWins = order.startsWith("draft/");
        Callable<Object> winnerCall = draftWins ? draftCall : otherCall, loserCall = draftWins ? otherCall : draftCall;
        var pool = Executors.newFixedThreadPool(2);
        try {
            var loser = pool.submit(() -> { role.set("loser"); try { return loserCall.call(); } catch (RuntimeException ex) { return ex; } finally { role.remove(); } });
            assertThat(loserReachedInsert.await(10, TimeUnit.SECONDS)).isTrue();
            var winner = pool.submit(() -> { try { return winnerCall.call(); } finally { winnerCommitted.countDown(); } });
            assertThat(winner.get(20, TimeUnit.SECONDS)).isNotInstanceOf(RuntimeException.class);
            Object failure = loser.get(20, TimeUnit.SECONDS); assertThat(failure).isInstanceOf(RuntimeException.class);
            if (!draftWins) assertThat(failure).isInstanceOfSatisfying(MentionDraftApprovalException.class,
                ex -> assertThat(ex.code()).isEqualTo("DUPLICATE_RELATION"));
            if (draftWins && otherPath.equals("direct")) assertThat(failure).isInstanceOfSatisfying(DirectRegistrationException.class,
                ex -> assertThat(ex.code()).isEqualTo("DUPLICATE_RELATION"));
            assertThat(count("mentions")).isEqualTo(counts[1] + 1);
            assertThat(row("restaurants", d.restaurant().id())).isEqualTo(restaurantBefore);
            assertThat(count("restaurants")).isEqualTo(counts[0]);
            if (draftWins) assertThat(row("mention_drafts", d.id())).containsEntry("status", "APPLIED");
            else assertThat(row("mention_drafts", d.id())).isEqualTo(draftBefore);
            assertThat(db.queryForObject("select disposition from restaurant_candidates where id=?", String.class, candidate))
                .isEqualTo(!draftWins && otherPath.equals("candidate") ? "RESOLVED" : candidateBefore);
            assertThat(db.queryForObject("select count(*) from admin_request_receipts where idempotency_key in (?,?)", Long.class, draftKey, otherKey))
                .isEqualTo(!draftWins && otherPath.equals("candidate") ? 0 : 1);
            assertThat(count("admin_audit_events")).isEqualTo(counts[2] + (!draftWins && otherPath.equals("candidate") ? 0 : 1));
        } finally { winnerCommitted.countDown(); pool.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(booleans={true, false})
    void controlledPatchApprovalLockOrderRejectsStaleOrNoneditableFollower(boolean patchFirst) throws Exception {
        var d = prepare(); String a = actor(), b = actor(); var firstLocked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var role = new ThreadLocal<Boolean>();
        doAnswer(inv -> {
            Object result = Optional.ofNullable(em.find(MentionDraftEntity.class, d.id(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
            if (Boolean.TRUE.equals(role.get())) { firstLocked.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
            return result;
        }).when(draftRepository).findForUpdate(d.id());
        var patch = MentionDraftInput.patch(mapper.readTree("{\"expectedVersion\":0,\"verificationNote\":\"검토\"}"));
        Callable<Object> patchCall = () -> drafts.patch(d.id(), patch, patchFirst ? a : b, new MockHttpSession());
        Callable<Object> approveCall = () -> approve(d, key(), patchFirst ? b : a);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { role.set(true); try { return (patchFirst ? patchCall : approveCall).call(); } finally { role.remove(); } });
            assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> { try { return (patchFirst ? approveCall : patchCall).call(); } catch (RuntimeException ex) { return ex; } });
            release.countDown(); first.get(15, TimeUnit.SECONDS);
            Object failure = second.get(15, TimeUnit.SECONDS);
            if (patchFirst) assertThat(failure).isInstanceOfSatisfying(MentionDraftApprovalException.class, ex -> assertThat(ex.code()).isEqualTo("STALE_VERSION"));
            else assertThat(failure).isInstanceOfSatisfying(MentionDraftException.class, ex -> assertThat(ex.code()).isEqualTo("DRAFT_NOT_EDITABLE"));
            assertThat(row("mention_drafts", d.id())).containsEntry("version", 1L).containsEntry("status", patchFirst ? "DRAFT" : "APPLIED");
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(strings={"PENDING/SUSPENDED", "REJECTED/SUSPENDED", "APPROVED/SUSPENDED", "APPROVED/PUBLISHED"})
    void publicQueriesExposeOnlyPublishedApprovedSourcesAndNoInternalFields(String state) throws Exception {
        var d = prepare(); var result = approve(d, key(), "admin"); String[] parts = state.split("/");
        db.update("update mentions set verification_status=?,publication_status=?,review_note='비공개 메모' where id=?", parts[0], parts[1], result.mentionId());
        String body = mvc.perform(get("/api/restaurants").with(anonymous())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var rows = mapper.readTree(body); JsonNode restaurant = null;
        for (var entry : rows) if (entry.get("id").asLong() == d.restaurant().id()) restaurant = entry;
        if (state.equals("APPROVED/PUBLISHED")) {
            assertThat(restaurant).isNotNull(); assertThat(restaurant.get("mentions").size()).isEqualTo(1);
            assertThat(restaurant.get("mentions").get(0).get("mentionId").asLong()).isEqualTo(result.mentionId());
        } else assertThat(restaurant).isNull();
        assertThat(body).doesNotContain("비공개 메모", "reviewNote", "evidence", "createdBy", "draftId", "idempotencyKey", "reviewer");
    }

    @Test void databaseRejectsInvalidPublicCombinationAndInvalidReferencesWithoutChangingRows() {
        var d = prepare(); var result = approve(d, key(), "admin"); var before = row("mentions", result.mentionId());
        assertThatThrownBy(() -> db.update("update mentions set verification_status='PENDING',publication_status='PUBLISHED' where id=?", result.mentionId()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(row("mentions", result.mentionId())).isEqualTo(before);
        var next = prepare(); var draftBefore = row("mention_drafts", next.id());
        assertThatThrownBy(() -> db.update("update mention_drafts set chef_id=? where id=?", Long.MAX_VALUE, next.id()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(row("mention_drafts", next.id())).isEqualTo(draftBefore);
    }

    @Test void draftCreationDirectAndCandidateConcurrentlyCreateOneKakaoRestaurant() throws Exception {
        var d = prepare(); String b = actor(), c = actor();
        String placeId = Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
        var place = new Models.Place(placeId, "공유 장소", "도로명", "지번", 127, 37, "https://place.map.kakao.com/" + placeId);
        when(apis.places(anyString())).thenReturn(new Models.PlaceSearch(List.of(place), 1, true));
        var sa = new MockHttpSession(); var sb = new MockHttpSession();
        String ta = selections.search("식당", "admin", sa).candidates().get(0).placeSelectionToken();
        String tb = selections.search("식당", b, sb).candidates().get(0).placeSelectionToken();
        jobs.saveAndFlush(new kr.sedanji.importjob.ImportJobEntity(videos.getRequired(d.videoId()), OffsetDateTime.now()));
        candidates.addManualCandidate(d.videoId(), "식당", "", "설명");
        long candidate = candidates.findCandidates().stream().filter(v -> v.videoId().equals(d.videoId())).findFirst().orElseThrow().id();
        matching.search(candidate, "식당");
        long otherChef = chefs.createChef("다른 셰프").id(); var gate = new CyclicBarrier(3);
        doAnswer(inv -> { gate.await(10, TimeUnit.SECONDS); return inv.callRealMethod(); }).when(restaurants).getOrCreate(argThat(p -> p.id().equals(placeId)));
        var pool = Executors.newFixedThreadPool(3);
        try {
            var stored = pool.submit(() -> drafts.create(MentionDraftInput.create(mapper.createObjectNode().put("placeSelectionToken", ta)), key(), "admin", sa));
            var approved = pool.submit(() -> direct.approve(new DirectRegistrationRequest(tb, otherChef, d.videoId(), kr.sedanji.mention.MentionType.VISITED, null, null), key(), b, sb));
            var candidateApproved = pool.submit(() -> { registrations.approveFromCandidate(candidate, d.chefId(), placeId,
                kr.sedanji.mention.MentionType.VISITED, null, "설명", null, c); return true; });
            var created = stored.get(20, TimeUnit.SECONDS); var directResult = approved.get(20, TimeUnit.SECONDS);
            assertThat(candidateApproved.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(created.restaurant().id()).isEqualTo(directResult.restaurantId());
            assertThat(db.queryForObject("select count(*) from restaurants where kakao_place_id=?", Long.class, placeId)).isEqualTo(1);
            assertThat(row("restaurants", created.restaurant().id())).containsEntry("name", "공유 장소").containsEntry("road_address", "도로명");
        } finally { pool.shutdownNow(); }
    }
}
