package kr.sedanji;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import kr.sedanji.admin.*;
import kr.sedanji.chef.ChefService;
import kr.sedanji.mention.MentionType;
import kr.sedanji.mentiondraft.*;
import kr.sedanji.registration.*;
import kr.sedanji.restaurant.RestaurantService;
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
import static kr.sedanji.Models.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** No caller transaction: assertions inspect committed state after service completion/rollback. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
@WithMockUser(username="admin", roles="ADMIN")
class MentionDraftIntegrationTest {
    private static final String PATH = MentionDraftService.PATH;
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired MentionDraftService service;
    @Autowired PlaceSelectionService selections;
    @Autowired RestaurantService restaurants;
    @Autowired ChefService chefs;
    @Autowired VideoService videos;
    @Autowired AdminUserRepository admins;
    @Autowired DirectRegistrationFacade direct;
    @MockitoBean ExternalApis apis;
    @MockitoSpyBean MentionDraftRepository drafts;
    @MockitoSpyBean AdminAuditEventRepository audits;
    @MockitoSpyBean AdminRequestReceiptRepository receipts;
    private record Prepared(ObjectNode body, MockHttpSession session, Place place) {}
    private String key() { return UUID.randomUUID().toString(); }
    private Prepared prepare() {
        String placeId = Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits());
        var place = new Place(placeId, "초안 식당", null, null, 127, 37, "https://place.map.kakao.com/" + placeId);
        when(apis.places(anyString())).thenReturn(new PlaceSearch(List.of(place), 1, true));
        var session = new MockHttpSession();
        String token = selections.search("식당", "admin", session).candidates().get(0).placeSelectionToken();
        return new Prepared(mapper.createObjectNode().put("placeSelectionToken", token), session, place);
    }
    private MentionDraftResult create(Prepared p) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return service.create(MentionDraftInput.create(p.body()), key(), "admin", p.session());
    }
    private ResultActions postDraft(ObjectNode body, MockHttpSession session, String key) throws Exception {
        return mvc.perform(post(PATH).session(session).with(csrf()).header("Idempotency-Key", key)
            .contentType("application/json").content(body.toString()));
    }
    private ResultActions patchDraft(long id, String body) throws Exception {
        return mvc.perform(patch(PATH + "/" + id).with(csrf()).contentType("application/json").content(body));
    }
    private MentionDraftResult read(MvcResult response) throws Exception {
        return mapper.readValue(response.getResponse().getContentAsString(), MentionDraftResult.class);
    }
    private long count(String table) { return db.queryForObject("select count(*) from " + table, Long.class); }
    private long[] counts() {
        return new long[]{count("restaurants"), count("mention_drafts"), count("mentions"), count("admin_audit_events"), count("admin_request_receipts")};
    }
    private long auditCount(long id) { return db.queryForObject("select count(*) from admin_audit_events where draft_id=?", Long.class, id); }
    private void references(ObjectNode body) {
        long chef = chefs.createChef("초안 셰프").id();
        String video = key().replace("-", "").substring(0, 11);
        videos.saveMetadata(new Video(video, "영상", "채널", OffsetDateTime.now(), "설명", "확인"));
        body.put("chefId", chef).put("videoId", video).put("mentionType", "VISITED");
    }
    @Test void restaurantOnlyCreateHasExplicitNullsAuditAndNoPublicRelationship() throws Exception {
        var p = prepare(); long[] before = counts();
        var response = postDraft(p.body(), p.session(), key()).andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("CREATE")).andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.version").value(0)).andReturn();
        var json = mapper.readTree(response.getResponse().getContentAsString());
        for (String field : List.of("chefId", "videoId", "mentionType", "timestampSeconds", "verificationNote", "candidateId", "appliedMentionId")) {
            assertThat(json.has(field)).isTrue(); assertThat(json.get(field).isNull()).isTrue();
        }
        assertThat(json.get("createdBy").isIntegralNumber()).isTrue();
        var result = read(response);
        assertThat(counts()).containsExactly(before[0]+1, before[1]+1, before[2], before[3]+1, before[4]+1);
        assertThat(result.restaurant().kakaoPlaceId()).isEqualTo(p.place().id());
        assertThat(result.createdBy()).isEqualTo(result.updatedBy());
        String audit = db.queryForObject("select after_value::text from admin_audit_events where draft_id=?", String.class, result.id());
        assertThat(mapper.readTree(audit)).isEqualTo(json);
        assertThat(audit).doesNotContain(p.body().get("placeSelectionToken").asText(), "placeSelectionToken");
        assertThat(db.queryForObject("select action from admin_audit_events where draft_id=?", String.class, result.id())).isEqualTo("DRAFT_CREATED");
        var publicJson = mvc.perform(get("/api/restaurants")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(publicJson).doesNotContain(p.place().id(), "verificationNote", "createdBy", "draft");
        mvc.perform(get(PATH + "/" + result.id())).andExpect(status().isOk()).andExpect(content().json(json.toString()));
        verify(apis, times(1)).places(anyString()); verifyNoMoreInteractions(apis);
    }
    @Test void existingRestaurantIsReusedAndDuplicateDraftsAreAllowed() throws Exception {
        var p = prepare();
        long restaurant = restaurants.getOrCreate(new Place(p.place().id(), "보존 이름", "도로", "지번", 126, 36, p.place().placeUrl())).getId();
        var body = mapper.createObjectNode().put("restaurantId", restaurant); references(body);
        long before = count("restaurants"), mentions = count("mentions");
        var first = read(postDraft(body, new MockHttpSession(), key()).andExpect(status().isCreated()).andReturn());
        var second = read(postDraft(body, new MockHttpSession(), key()).andExpect(status().isCreated()).andReturn());
        assertThat(first.restaurant().name()).isEqualTo("보존 이름"); assertThat(first.restaurant().longitude()).isEqualTo(126);
        assertThat(first.chefId()).isEqualTo(body.get("chefId").longValue()); assertThat(first.videoId()).isEqualTo(body.get("videoId").asText());
        assertThat(second.id()).isNotEqualTo(first.id()); assertThat(first.timestampSeconds()).isNull();
        assertThat(count("restaurants")).isEqualTo(before); assertThat(count("mentions")).isEqualTo(mentions);
    }
    @Test void replayUsesNormalizedRequestAndSurvivesExpiredSelectionButStillChecksActiveAdmin() throws Exception {
        var p = prepare(); p.body().put("verificationNote", "  메모  "); String key = key();
        var first = postDraft(p.body(), p.session(), key).andExpect(status().isCreated()).andReturn();
        long[] before = counts(); p.body().put("verificationNote", "메모");
        p.body().putNull("chefId").putNull("videoId").putNull("mentionType").putNull("timestampSeconds");
        postDraft(p.body(), new MockHttpSession(), key).andExpect(status().isCreated()).andExpect(content().json(first.getResponse().getContentAsString()));
        assertThat(counts()).containsExactly(before);
        patchDraft(read(first).id(), "{\"expectedVersion\":0,\"verificationNote\":\"나중 수정\"}").andExpect(status().isOk());
        before = counts();
        postDraft(p.body(), new MockHttpSession(), key).andExpect(status().isCreated()).andExpect(content().json(first.getResponse().getContentAsString()));
        assertThat(counts()).containsExactly(before);
        p.body().put("verificationNote", "다른 메모");
        postDraft(p.body(), p.session(), key).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(counts()).containsExactly(before);
        String other = "admin-" + key(); var admin = admins.saveAndFlush(new AdminUserEntity(other, "test-only-hash"));
        var request = MentionDraftInput.create(mapper.createObjectNode().put("restaurantId", read(first).restaurant().id()));
        String otherKey = key; service.create(request, otherKey, other, new MockHttpSession());
        db.update("update admin_users set enabled=false where id=?", admin.getId()); before = counts();
        assertThatThrownBy(() -> service.create(request, otherKey, other, new MockHttpSession()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(counts()).containsExactly(before);
    }
    @Test void expiredCreateReceiptAllowsAnotherDraftAndCrossPathKeysConflictInBothDirections() throws Exception {
        var p = prepare(); references(p.body()); String firstKey = key();
        var first = read(postDraft(p.body(), p.session(), firstKey).andExpect(status().isCreated()).andReturn());
        var directRequest = new DirectRegistrationRequest(p.body().get("placeSelectionToken").asText(), p.body().get("chefId").longValue(),
            p.body().get("videoId").asText(), MentionType.VISITED, null, null);
        long[] before = counts();
        assertThatThrownBy(() -> direct.approve(directRequest, firstKey, "admin", p.session()))
            .isInstanceOfSatisfying(DirectRegistrationException.class, ex -> assertThat(ex.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(counts()).containsExactly(before);
        String directKey = key(); var approved = direct.approve(directRequest, directKey, "admin", p.session());
        before = counts();
        postDraft(p.body(), p.session(), directKey).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(direct.approve(directRequest, directKey, "admin", new MockHttpSession())).isEqualTo(approved);
        assertThat(counts()).containsExactly(before);
        db.update("update admin_request_receipts set created_at=now()-interval '25 hours',expires_at=now()-interval '1 hour' where idempotency_key=?", firstKey);
        var next = read(postDraft(p.body(), p.session(), firstKey).andExpect(status().isCreated()).andReturn());
        assertThat(next.id()).isNotEqualTo(first.id()); assertThat(count("mention_drafts")).isEqualTo(before[1]+1);
        assertThat(count("admin_request_receipts")).isEqualTo(before[4]);
    }
    @Test void patchPreservesOmittedFieldsClearsNullsAndIncrementsUnchangedValuesOnce() throws Exception {
        var p = prepare(); references(p.body()); p.body().put("timestampSeconds", 0).put("verificationNote", " 내부 메모 ");
        long mentionCount = count("mentions");
        var first = create(p);
        var changed = read(patchDraft(first.id(), "{\"expectedVersion\":0,\"chefId\":null,\"verificationNote\":\" \"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andReturn());
        assertThat(changed.chefId()).isNull(); assertThat(changed.verificationNote()).isNull();
        assertThat(changed.videoId()).isEqualTo(first.videoId()); assertThat(changed.mentionType()).isEqualTo(first.mentionType());
        assertThat(changed.timestampSeconds()).isZero(); assertThat(changed.updatedAt()).isAfter(first.updatedAt());
        var unchanged = read(patchDraft(first.id(), "{\"expectedVersion\":1,\"timestampSeconds\":0}").andExpect(status().isOk()).andReturn());
        assertThat(unchanged.version()).isEqualTo(2); assertThat(unchanged.updatedAt()).isAfter(changed.updatedAt());
        assertThat(auditCount(first.id())).isEqualTo(3);
        var cleared = read(patchDraft(first.id(), "{\"expectedVersion\":2,\"videoId\":null,\"mentionType\":null,\"timestampSeconds\":null}")
            .andExpect(status().isOk()).andReturn());
        assertThat(cleared.videoId()).isNull(); assertThat(cleared.mentionType()).isNull(); assertThat(cleared.timestampSeconds()).isNull();
        patchDraft(first.id(), "{\"expectedVersion\":2,\"verificationNote\":null}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STALE_VERSION")).andExpect(jsonPath("$.currentVersion").value(3));
        String beforeJson = db.queryForObject("select before_value::text from admin_audit_events where draft_id=? and action='DRAFT_UPDATED' order by id limit 1", String.class, first.id());
        assertThat(mapper.readTree(beforeJson)).isEqualTo(mapper.readTree(mapper.writeValueAsString(first)));
        assertThat(count("mentions")).isEqualTo(mentionCount);
    }
    @Test void storedDraftIsIndependentOfTokenExpiryAndSharedAcrossActiveAdmins() throws Exception {
        var p = prepare(); var first = create(p);
        var expiredSelections = new PlaceSelectionService(apis, Clock.fixed(Instant.now().plusSeconds(1000), ZoneOffset.UTC));
        assertThatThrownBy(() -> expiredSelections.resolve(p.body().get("placeSelectionToken").asText(), "admin", p.session()))
            .isInstanceOf(DirectRegistrationException.class);
        String other = "admin-" + key(); long actor = admins.saveAndFlush(new AdminUserEntity(other, "test-only-hash")).getId();
        mvc.perform(get(PATH + "/" + first.id()).with(user(other).roles("ADMIN"))).andExpect(status().isOk());
        var result = mvc.perform(patch(PATH + "/" + first.id()).with(user(other).roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"expectedVersion\":0,\"verificationNote\":\"확인\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.updatedBy").value(actor)).andReturn();
        assertThat(read(result).createdBy()).isEqualTo(first.createdBy());
        verify(apis, times(1)).places(anyString()); verifyNoMoreInteractions(apis);
    }
    @Test void patchReplacesPlaceByTokenAndExistingIdWithoutOverwritingRestaurant() throws Exception {
        var p = prepare(); var first = create(p); var q = prepare();
        var patch = mapper.createObjectNode().put("expectedVersion", 0).put("placeSelectionToken", q.body().get("placeSelectionToken").asText());
        var changed = service.patch(first.id(), MentionDraftInput.patch(patch), "admin", q.session());
        assertThat(changed.restaurant().kakaoPlaceId()).isEqualTo(q.place().id());
        var back = read(patchDraft(first.id(), "{\"expectedVersion\":1,\"restaurantId\":" + first.restaurant().id() + "}")
            .andExpect(status().isOk()).andReturn());
        assertThat(back.restaurant()).isEqualTo(first.restaurant());
    }
    @ParameterizedTest @ValueSource(strings={"draft", "audit", "receipt"})
    void createStorageFailureRollsBackRestaurantDraftAuditReceiptAndPreservesMetadata(String step) {
        var p = prepare(); references(p.body()); long[] before = counts();
        if (step.equals("draft")) doThrow(new IllegalStateException("injected")).when(drafts).saveAndFlush(any());
        if (step.equals("audit")) doThrow(new IllegalStateException("injected")).when(audits).saveAndFlush(any());
        if (step.equals("receipt")) doThrow(new IllegalStateException("injected")).when(receipts).saveAndFlush(any());
        assertThatThrownBy(() -> create(p)).isInstanceOf(IllegalStateException.class);
        assertThat(counts()).containsExactly(before);
        assertThat(videos.findExisting(p.body().get("videoId").asText())).isPresent();
        assertThat(chefs.getRequired(p.body().get("chefId").longValue())).isNotNull();
    }
    @ParameterizedTest @ValueSource(strings={"draft", "audit"})
    void patchStorageFailureRollsBackVersionAndNewRestaurant(String step) {
        var p = prepare(); var first = create(p); var q = prepare(); long[] before = counts();
        var patch = mapper.createObjectNode().put("expectedVersion", 0).put("placeSelectionToken", q.body().get("placeSelectionToken").asText())
            .put("verificationNote", "변경");
        if (step.equals("draft")) doThrow(new IllegalStateException("injected")).when(drafts).flush();
        if (step.equals("audit")) doThrow(new IllegalStateException("injected")).when(audits).saveAndFlush(any());
        assertThatThrownBy(() -> service.patch(first.id(), MentionDraftInput.patch(patch), "admin", q.session())).isInstanceOf(IllegalStateException.class);
        assertThat(service.get(first.id(), "admin")).isEqualTo(first); assertThat(counts()).containsExactly(before);
    }
    @Test void concurrentSameKeyCreatesOneDraftAuditAndReceipt() throws Exception {
        var p = prepare(); String key = key(); long[] before = counts();
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<Future<MentionDraftResult>>();
            for (int i = 0; i < 2; i++) futures.add(executor.submit(() -> {
                ready.countDown(); if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                return service.create(MentionDraftInput.create(p.body()), key, "admin", p.session());
            }));
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(futures.get(0).get(30, TimeUnit.SECONDS)).isEqualTo(futures.get(1).get(30, TimeUnit.SECONDS));
            assertThat(counts()).containsExactly(before[0]+1, before[1]+1, before[2], before[3]+1, before[4]+1);
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    @Test void storageFailureResponseDoesNotExposeDatabaseOrInternalException() throws Exception {
        var p = prepare(); long[] before = counts();
        doThrow(new IllegalStateException("private database detail")).when(receipts).saveAndFlush(any());
        var response = postDraft(p.body(), p.session(), key()).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("private database detail", "IllegalStateException");
        assertThat(counts()).containsExactly(before);
    }
    @Test void concurrentSameVersionHasExactlyOneWinnerAndOneUpdateAudit() throws Exception {
        var p = prepare(); var first = create(p);
        String other = "admin-" + key(); admins.saveAndFlush(new AdminUserEntity(other, "test-only-hash"));
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = new ArrayList<Future<Object>>();
            for (String username : List.of("admin", other)) futures.add(executor.submit(() -> {
                var body = mapper.createObjectNode().put("expectedVersion", 0).put("verificationNote", username);
                ready.countDown(); if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                try { return service.patch(first.id(), MentionDraftInput.patch(body), username, new MockHttpSession()); }
                catch (MentionDraftException ex) { return ex; }
            }));
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var outcomes = List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
            assertThat(outcomes.stream().filter(MentionDraftResult.class::isInstance)).hasSize(1);
            var winner = outcomes.stream().filter(MentionDraftResult.class::isInstance).map(MentionDraftResult.class::cast).findFirst().orElseThrow();
            assertThat(outcomes.stream().filter(MentionDraftException.class::isInstance).map(MentionDraftException.class::cast)).singleElement()
                .satisfies(ex -> { assertThat(ex.code()).isEqualTo("STALE_VERSION"); assertThat(ex.currentVersion()).isEqualTo(1); });
            assertThat(service.get(first.id(), "admin")).isEqualTo(winner); assertThat(winner.version()).isEqualTo(1);
            assertThat(auditCount(first.id())).isEqualTo(2);
            String snapshot = db.queryForObject("select after_value::text from admin_audit_events where draft_id=? and action='DRAFT_UPDATED'", String.class, first.id());
            assertThat(mapper.readTree(snapshot)).isEqualTo(mapper.readTree(mapper.writeValueAsString(winner)));
            assertThat(db.queryForObject("select actor_id from admin_audit_events where draft_id=? and action='DRAFT_UPDATED'", Long.class, first.id()))
                .isEqualTo(winner.updatedBy());
        } finally { start.countDown(); executor.shutdownNow(); }
    }
    @Test void invalidBodiesReferencesAndSelectionHaveNoBusinessWrites() throws Exception {
        var p = prepare(); var first = create(p); long[] before = counts();
        for (String field : List.of("kind", "status", "createdBy", "updatedBy", "version", "candidateId", "appliedMentionId", "verificationStatus", "publicationStatus", "unknown")) {
            postDraft(p.body().deepCopy().put(field, "bad"), p.session(), key()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String timestamp : List.of("-1", "1.5", "2147483648", "\"0\"")) {
            mvc.perform(post(PATH).with(csrf()).header("Idempotency-Key", key()).contentType("application/json")
                .content("{\"restaurantId\":" + first.restaurant().id() + ",\"timestampSeconds\":" + timestamp + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String body : List.of("{}", "{\"expectedVersion\":0}", "{\"expectedVersion\":0,\"restaurantId\":null}",
            "{\"expectedVersion\":0,\"videoId\":\"\"}", "{\"expectedVersion\":0,\"mentionType\":\"\"}")) {
            patchDraft(first.id(), body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        for (String field : List.of("restaurantId", "chefId", "videoId")) {
            var body = mapper.createObjectNode().put("restaurantId", first.restaurant().id());
            if (field.equals("videoId")) body.put(field, "-----------"); else body.put(field, Long.MAX_VALUE);
            postDraft(body, p.session(), key()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            body.remove("restaurantId"); body.put("expectedVersion", 0);
            if (field.equals("restaurantId")) body.put(field, Long.MAX_VALUE);
            patchDraft(first.id(), body.toString()).andExpect(status().isNotFound());
        }
        postDraft(p.body(), new MockHttpSession(), key()).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PLACE_SELECTION_EXPIRED"));
        String other = "admin-" + key(); admins.saveAndFlush(new AdminUserEntity(other, "test-only-hash"));
        mvc.perform(post(PATH).session(p.session()).with(user(other).roles("ADMIN")).with(csrf())
            .header("Idempotency-Key", key()).contentType("application/json").content(p.body().toString()))
            .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PLACE_SELECTION_EXPIRED"));
        patchDraft(first.id(), "{\"expectedVersion\":0,\"placeSelectionToken\":\"lost\"}")
            .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PLACE_SELECTION_EXPIRED"));
        mvc.perform(post(PATH).with(csrf()).contentType("application/json").content(p.body().toString())).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/0")).andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        assertThat(service.get(first.id(), "admin")).isEqualTo(first); assertThat(counts()).containsExactly(before);
    }
    @Test void appliedIsReadableButImmutableAndListFiltersSortsAndPagesCreateDrafts() throws Exception {
        var p = prepare(); references(p.body()); var first = create(p);
        var body = mapper.createObjectNode().put("restaurantId", first.restaurant().id());
        var second = read(postDraft(body, p.session(), key()).andExpect(status().isCreated()).andReturn());
        var third = read(postDraft(body, p.session(), key()).andExpect(status().isCreated()).andReturn());
        // Approved fixture only; #14 intentionally exposes no approval endpoint.
        var request = new DirectRegistrationRequest(p.body().get("placeSelectionToken").asText(), first.chefId(), first.videoId(), first.mentionType(), null, null);
        var relation = direct.approve(request, key(), "admin", p.session());
        db.update("update mention_drafts set status='APPLIED',applied_mention_id=? where id=?", relation.mentionId(), first.id());
        db.update("update mention_drafts set updated_at=? where id in (?,?)", OffsetDateTime.parse("2026-01-01T00:00:00Z"), second.id(), third.id());
        db.update("insert into mention_drafts(restaurant_id,kind,target_mention_id,base_version,change_reason,created_by,updated_by) values (?,'CORRECTION',?,0,'정정',?,?)",
            first.restaurant().id(), relation.mentionId(), first.createdBy(), first.createdBy());
        long correction = db.queryForObject("select id from mention_drafts where target_mention_id=? and kind='CORRECTION'", Long.class, relation.mentionId());
        mvc.perform(get(PATH + "/" + correction)).andExpect(status().isNotFound());
        patchDraft(correction, "{\"expectedVersion\":0,\"verificationNote\":null}").andExpect(status().isNotFound());
        create(prepare()); long[] before = counts();
        String list = "/api/admin/restaurants/" + first.restaurant().id() + "/mention-drafts";
        mvc.perform(get(list).param("size", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items[0].id").value(third.id())).andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(list).param("size", "1").param("page", "1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(second.id())).andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(list).param("status", "APPLIED")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(first.id()))
            .andExpect(jsonPath("$.totalElements").value(1));
        var appliedBefore = service.get(first.id(), "admin");
        var relationBefore = db.queryForMap("select * from mentions where id=?", relation.mentionId());
        mvc.perform(get(PATH + "/" + first.id())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));
        patchDraft(first.id(), "{\"expectedVersion\":0,\"verificationNote\":null}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DRAFT_NOT_EDITABLE"));
        assertThat(counts()).containsExactly(before);
        assertThat(service.get(first.id(), "admin")).isEqualTo(appliedBefore);
        assertThat(db.queryForMap("select * from mentions where id=?", relation.mentionId())).isEqualTo(relationBefore);
        assertThat(auditCount(first.id())).isEqualTo(1);
        patchDraft(second.id(), "{\"expectedVersion\":0,\"verificationNote\":\"최신\"}").andExpect(status().isOk());
        mvc.perform(get(list)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second.id()));
        for (String query : List.of("size=101", "size=0", "page=-1", "status=OTHER", "page=bad"))
            mvc.perform(get(list + "?" + query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        long emptyRestaurant = restaurants.getOrCreate(prepare().place()).getId();
        mvc.perform(get("/api/admin/restaurants/" + emptyRestaurant + "/mention-drafts")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/api/admin/restaurants/" + Long.MAX_VALUE + "/mention-drafts")).andExpect(status().isNotFound());
    }
    @Test void accessControlAndCsrfProtectReadsWritesAndReplays() throws Exception {
        var p = prepare(); String key = key(); var first = read(postDraft(p.body(), p.session(), key).andExpect(status().isCreated()).andReturn());
        String other = "inactive-" + key(); long actor = admins.saveAndFlush(new AdminUserEntity(other, "test-only-hash")).getId();
        var body = mapper.createObjectNode().put("restaurantId", first.restaurant().id()); String replayKey = key();
        service.create(MentionDraftInput.create(body), replayKey, other, new MockHttpSession());
        db.update("update admin_users set enabled=false where id=?", actor); long[] before = counts();
        String list = "/api/admin/restaurants/" + first.restaurant().id() + "/mention-drafts";
        // Explicit anonymous expectations preserve the existing empty 401 security response.
        mvc.perform(get(PATH + "/" + first.id()).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(list).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(anonymous()).with(csrf()).contentType("application/json").content(body.toString())).andExpect(status().isUnauthorized());
        mvc.perform(patch(PATH + "/" + first.id()).with(anonymous()).with(csrf()).contentType("application/json")
            .content("{\"expectedVersion\":0,\"chefId\":null}")).andExpect(status().isUnauthorized());
        for (String username : List.of("viewer", other)) {
            var identity = user(username).roles(username.equals("viewer") ? "USER" : "ADMIN");
            mvc.perform(get(PATH + "/" + first.id()).with(identity)).andExpect(status().isForbidden());
            mvc.perform(get(list).with(identity)).andExpect(status().isForbidden());
            mvc.perform(post(PATH).with(identity).with(csrf()).header("Idempotency-Key", replayKey).contentType("application/json").content(body.toString()))
                .andExpect(status().isForbidden());
            mvc.perform(patch(PATH + "/" + first.id()).with(identity).with(csrf()).contentType("application/json").content("{\"expectedVersion\":0,\"chefId\":null}"))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post(PATH).contentType("application/json").content(body.toString())).andExpect(status().isForbidden());
        mvc.perform(patch(PATH + "/" + first.id()).contentType("application/json").content("{\"expectedVersion\":0,\"chefId\":null}"))
            .andExpect(status().isForbidden());
        assertThat(counts()).containsExactly(before);
    }
}
