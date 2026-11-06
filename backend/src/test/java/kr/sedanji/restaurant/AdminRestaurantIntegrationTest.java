package kr.sedanji.restaurant;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.sedanji.ExternalApis;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
@WithMockUser(username="admin", roles="ADMIN")
class AdminRestaurantIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired kr.sedanji.video.VideoService videoService;
    @Autowired kr.sedanji.mentiondraft.MentionDraftService draftService;
    @Autowired kr.sedanji.mentiondraft.MentionDraftApprovalFacade approval;
    @Autowired kr.sedanji.registration.DirectRegistrationFacade direct;
    @Autowired kr.sedanji.PlaceSelectionService selections;
    @Autowired kr.sedanji.registration.RegistrationService registrations;
    @Autowired kr.sedanji.importjob.VideoImportService imports;
    @Autowired kr.sedanji.restaurantcandidate.RestaurantCandidateService candidates;
    @MockitoBean ExternalApis apis;
    @MockitoSpyBean AdminRestaurantQueryRepository repository;
    long[] restaurants, chefs;
    String[] videos;
    long admin;
    @BeforeEach void seed() {
        // Fixture reset is confined to this class's Testcontainers database.
        db.execute("truncate restaurants cascade");
        admin = db.queryForObject("select id from admin_users where username='admin'", Long.class);
        db.update("update admin_users set enabled=true where id=?", admin);
        chefs = new long[5];
        for (int i=0;i<5;i++) chefs[i] = db.queryForObject("insert into chefs(name) values (?) returning id", Long.class,
            new String[]{"윤셰프", "LEE", "김셰프", "동명", "초안전용"}[i]);
        videos = new String[5];
        for (int i=0;i<5;i++) {
            videos[i] = UUID.randomUUID().toString().substring(0,11);
            db.update("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values (?,'영상','채널',now(),'원문','검토')", videos[i]);
            if (i!=2) db.update("update videos set source_check_status=?,source_checked_at=now(),source_check_reason=? where id=?",
                i==1 ? "REVIEW_REQUIRED" : "AVAILABLE", i==1 ? "UNAVAILABLE" : null, videos[i]);
        }
        restaurants = new long[5];
        for (int i=0;i<5;i++) restaurants[i] = db.queryForObject("""
            insert into restaurants(kakao_place_id,name,road_address,address,longitude,latitude,place_url,created_at)
            values (?,?,?, ?,127,37,'https://place.map.kakao.com/1',cast(? as timestamptz)) returning id
            """, Long.class, UUID.randomUUID().toString(), new String[]{"한빛국수","골목밥","초안집","빈장소","윤셰프 식탁"}[i],
            i==0 ? "서울 마포구 10" : null, i==1 ? "부산 해운대 20" : null,
            "2026-01-0" + new int[]{1,2,3,3,4}[i] + "T00:00:00Z");
        mention(0,0,0,"APPROVED","PUBLISHED",0);
        mention(0,1,1,"APPROVED","SUSPENDED",null);
        mention(1,2,2,"PENDING","SUSPENDED",null);
        mention(4,0,3,"APPROVED","PUBLISHED",null);
        mention(4,3,4,"REJECTED","SUSPENDED",null);
        db.update("insert into mention_drafts(restaurant_id,chef_id,video_id,kind,created_by,updated_by) values (?,?,?,'CREATE',?,?)",
            restaurants[2],chefs[4],videos[2],admin,admin);
    }
    long mention(int r,int c,int v,String verification,String publication,Integer timestamp) {
        return db.queryForObject("""
            insert into mentions(restaurant_id,chef_id,video_id,mention_type,verification_status,publication_status,
              timestamp_seconds,review_note,reviewed_by,reviewed_at)
            values (?,?,?,'VISITED',?,?,?, ?,?,now()) returning id
            """, Long.class, restaurants[r],chefs[c],videos[v],verification,publication,timestamp,"메모-"+c,admin);
    }
    JsonNode getJson(String path) throws Exception {
        return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>(); page.get("items").forEach(n -> ids.add(n.get("id").asLong())); return ids;
    }
    JsonNode list(String suffix) throws Exception { return getJson("/api/admin/restaurants"+suffix); }
    @Test void searchesAllActualRelationshipsAndCombinesIndependentPredicates() throws Exception {
        for (String term : List.of("국수","마포구","lee","  LEE  "))
            assertThat(ids(getJsonWithQuery(term))).containsExactly(restaurants[0]);
        assertThat(ids(getJsonWithQuery("해운대"))).containsExactly(restaurants[1]);
        assertThat(ids(getJsonWithQuery("윤셰프"))).containsExactly(restaurants[4],restaurants[0]);
        assertThat(getJsonWithQuery("윤셰프").get("totalElements").asLong()).isEqualTo(2);
        assertThat(ids(getJsonWithQuery("초안전용"))).isEmpty();
        assertThat(ids(list("?query=LEE&publication=PUBLISHED"))).containsExactly(restaurants[0]);
        assertThat(ids(list("?query=김셰프&publication=NO_PUBLIC_RELATION"))).containsExactly(restaurants[1]);
        assertThat(ids(getJsonWithQuery("   "))).hasSize(5);
        for (String term : List.of("%","_","!","' OR 1=1 --","null","한빛 국수")) assertThat(ids(getJsonWithQuery(term))).isEmpty();
        db.update("update restaurants set name='특수%_!문자' where id=?",restaurants[3]);
        assertThat(ids(getJsonWithQuery("%_!"))).containsExactly(restaurants[3]);
    }
    JsonNode getJsonWithQuery(String query) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/admin/restaurants").param("query",query))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void filtersSummariesAndStableRestaurantPages() throws Exception {
        assertThat(ids(list("?publication=PUBLISHED"))).containsExactly(restaurants[4],restaurants[0]);
        assertThat(ids(list("?publication=NO_PUBLIC_RELATION"))).containsExactly(restaurants[3],restaurants[2],restaurants[1]);
        assertThat(ids(list("?needsSourceReview=true"))).containsExactly(restaurants[0]);
        assertThat(ids(list("?publication=PUBLISHED&needsSourceReview=true"))).containsExactly(restaurants[0]);
        assertThat(ids(list("?needsSourceReview=false"))).hasSize(5);
        var all = list("");
        assertThat(all.at("/items/0/needsSourceReview").asBoolean()).isFalse();
        for(int i=1;i<4;i++) assertThat(all.at("/items/"+i+"/needsSourceReview").isNull()).isTrue();
        assertThat(all.at("/items/4/needsSourceReview").asBoolean()).isTrue();
        assertThat(all.at("/items/4/relationCount").asInt()).isEqualTo(2);
        assertThat(all.at("/items/4/publicRelationCount").asInt()).isEqualTo(1);
        assertThat(all.at("/items/4/sourceCount").asInt()).isEqualTo(2);
        assertThat(all.at("/items/4/reviewRequiredSourceCount").asInt()).isEqualTo(1);
        List<Long> union = new ArrayList<>();
        for(int p=0;p<3;p++) {
            var page = list("?size=2&page="+p); union.addAll(ids(page));
            assertThat(page.get("totalElements").asInt()).isEqualTo(5);
            assertThat(page.get("hasNext").asBoolean()).isEqualTo(p<2);
        }
        assertThat(union).containsExactly(restaurants[4],restaurants[3],restaurants[2],restaurants[1],restaurants[0]);
        var before = list(""); mention(0,3,0,"PENDING","SUSPENDED",null);
        assertThat(ids(list(""))).isEqualTo(ids(before));
        assertThat(list("").at("/items/4/createdAt")).isEqualTo(before.at("/items/4/createdAt"));
        assertThat(list("").at("/items/4/sourceCount").asInt()).isEqualTo(2);
        assertThat(list("").at("/items/4/relationCount").asInt()).isEqualTo(3);
        assertThat(ids(list("?page=99"))).isEmpty();
        assertThat(list("?page=99").get("totalElements").asInt()).isEqualTo(5);
    }
    @Test void detailPreservesRelationshipIdentityNullZeroAndPrivateFields() throws Exception {
        var detail = getJson("/api/admin/restaurants/"+restaurants[0]);
        assertThat(detail.at("/mentions/items/0/chef/name").asText()).isEqualTo("LEE");
        assertThat(detail.at("/mentions/items/0/video/id").asText()).isEqualTo(videos[1]);
        assertThat(detail.at("/mentions/items/0/verificationNote").asText()).isEqualTo("메모-1");
        assertThat(detail.at("/mentions/items/0/timestampSeconds").isNull()).isTrue();
        assertThat(detail.at("/mentions/items/1/timestampSeconds").asInt()).isZero();
        assertThat(detail.at("/mentions/items/1/chef/name").asText()).isEqualTo("윤셰프");
        assertThat(detail.at("/mentions/items/0/video/sourceCheckReason").asText()).isEqualTo("UNAVAILABLE");
        assertThat(detail.toString()).doesNotContain("evidence","password","createdBy");
        for(int p=0;p<2;p++) {
            var page=getJson("/api/admin/restaurants/"+restaurants[0]+"?mentionSize=1&mentionPage="+p).get("mentions");
            assertThat(page.get("items").size()).isEqualTo(1);
            assertThat(page.get("totalElements").asInt()).isEqualTo(2);
            assertThat(page.at("/items/0/id")).isEqualTo(detail.at("/mentions/items/"+p+"/id"));
        }
        for(int r : new int[]{2,3}) assertThat(getJson("/api/admin/restaurants/"+restaurants[r]).at("/mentions/items").size()).isZero();
        var before = db.queryForList("select * from mentions where restaurant_id=? order by id", restaurants[0]);
        db.update("update videos set source_check_status='AVAILABLE',source_checked_at=now(),source_check_reason=null where id=?",videos[1]);
        getJson("/api/admin/restaurants/"+restaurants[0]);
        assertThat(db.queryForList("select * from mentions where restaurant_id=? order by id", restaurants[0])).isEqualTo(before);
        String publicJson=getJson("/api/restaurants").toString();
        assertThat(publicJson).doesNotContain("review_note","verificationNote","sourceCheck","version","메모-","초안집",videos[1]);
    }
    @Test void chefPreviewIsLimitedByIdAndHomonymsRemainDistinct() throws Exception {
        chefs[4]=db.queryForObject("insert into chefs(name) values ('동명') returning id",Long.class);
        mention(0,3,0,"PENDING","SUSPENDED",null); mention(0,4,0,"PENDING","SUSPENDED",null);
        var item=list("?query=한빛국수").at("/items/0");
        assertThat(item.get("chefCount").asInt()).isEqualTo(4);
        assertThat(item.get("chefs").size()).isEqualTo(3);
        assertThat(item.get("hasMoreChefs").asBoolean()).isTrue();
        assertThat(item.at("/chefs/0/id").asLong()).isEqualTo(chefs[0]);
        assertThat(item.at("/chefs/2/id").asLong()).isEqualTo(chefs[3]);
    }
    @Test void mixedSourceStatesUseDistinctVideosAndKeepReviewRequiredPriority() throws Exception {
        mention(0,2,2,"PENDING","SUSPENDED",null);
        mention(0,3,2,"REJECTED","SUSPENDED",null);
        var item=list("?query=한빛국수").at("/items/0");
        assertThat(item.get("relationCount").asInt()).isEqualTo(4);
        assertThat(item.get("sourceCount").asInt()).isEqualTo(3);
        assertThat(item.get("uncheckedSourceCount").asInt()).isEqualTo(1);
        assertThat(item.get("reviewRequiredSourceCount").asInt()).isEqualTo(1);
        assertThat(item.get("needsSourceReview").asBoolean()).isTrue();
        assertThat(ids(list("?query=한빛국수&needsSourceReview=true"))).containsExactly(restaurants[0]);
    }
    @Test void rejectedOnlyRestaurantIsSearchableButNotPublicAndRepeatedChefMatchesDoNotDuplicateIt() throws Exception {
        // Use the previously unrelated restaurant so no approved relation can mask the filter result.
        mention(3,3,3,"REJECTED","SUSPENDED",null);
        mention(3,3,4,"REJECTED","SUSPENDED",null);
        var page=getJsonWithQuery("동명");
        assertThat(ids(page)).containsExactly(restaurants[4],restaurants[3]);
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        var rejectedOnly=list("?query=빈장소&publication=NO_PUBLIC_RELATION");
        assertThat(ids(rejectedOnly)).containsExactly(restaurants[3]);
        assertThat(rejectedOnly.at("/items/0/relationCount").asInt()).isEqualTo(2);
        assertThat(rejectedOnly.at("/items/0/publicRelationCount").asInt()).isZero();
        assertThat(ids(list("?query=빈장소&publication=PUBLISHED"))).isEmpty();
    }
    @Test void detailFieldsRemainAttachedToTheirMentionIdsWithoutExternalCalls() throws Exception {
        var checkedAt=java.time.OffsetDateTime.parse("2026-02-01T12:00:00Z");
        var reviewedAt=java.time.OffsetDateTime.parse("2026-02-02T12:00:00Z");
        db.update("update videos set title='영상 A',channel_title='채널 A',source_checked_at=? where id=?",checkedAt,videos[0]);
        db.update("update videos set title='영상 B',channel_title='채널 B',source_checked_at=? where id=?",checkedAt.plusDays(1),videos[1]);
        db.update("update mentions set mention_type='REGULAR',version=7,review_note='메모 A',reviewed_at=? where restaurant_id=? and chef_id=?",
            reviewedAt,restaurants[0],chefs[0]);
        db.update("update mentions set mention_type='MENTIONED',version=9,review_note='메모 B',reviewed_at=? where restaurant_id=? and chef_id=?",
            reviewedAt.plusDays(1),restaurants[0],chefs[1]);
        long first=db.queryForObject("select id from mentions where restaurant_id=? and chef_id=?",Long.class,restaurants[0],chefs[0]);
        long second=db.queryForObject("select id from mentions where restaurant_id=? and chef_id=?",Long.class,restaurants[0],chefs[1]);
        clearInvocations(apis);
        assertThat(ids(getJsonWithQuery("LEE"))).containsExactly(restaurants[0]);
        var items=getJson("/api/admin/restaurants/"+restaurants[0]).at("/mentions/items");
        assertThat(items.size()).isEqualTo(2);
        for(int i=0;i<2;i++) {
            boolean newer=i==0;
            var row=items.get(i);
            assertThat(row.get("id").asLong()).isEqualTo(newer ? second : first);
            assertThat(row.get("version").asLong()).isEqualTo(newer ? 9 : 7);
            assertThat(row.at("/chef/id").asLong()).isEqualTo(chefs[newer ? 1 : 0]);
            assertThat(row.at("/video/id").asText()).isEqualTo(videos[newer ? 1 : 0]);
            assertThat(row.at("/video/title").asText()).isEqualTo(newer ? "영상 B" : "영상 A");
            assertThat(row.at("/video/channelTitle").asText()).isEqualTo(newer ? "채널 B" : "채널 A");
            assertThat(row.get("mentionType").asText()).isEqualTo(newer ? "MENTIONED" : "REGULAR");
            assertThat(row.get("verificationStatus").asText()).isEqualTo("APPROVED");
            assertThat(row.get("publicationStatus").asText()).isEqualTo(newer ? "SUSPENDED" : "PUBLISHED");
            assertThat(row.get("verificationNote").asText()).isEqualTo(newer ? "메모 B" : "메모 A");
            assertThat(row.at("/video/sourceCheckStatus").asText()).isEqualTo(newer ? "REVIEW_REQUIRED" : "AVAILABLE");
            assertThat(java.time.OffsetDateTime.parse(row.at("/video/sourceCheckedAt").asText()).toInstant())
                .isEqualTo((newer ? checkedAt.plusDays(1) : checkedAt).toInstant());
            assertThat(java.time.OffsetDateTime.parse(row.get("reviewedAt").asText()).toInstant())
                .isEqualTo((newer ? reviewedAt.plusDays(1) : reviewedAt).toInstant());
            if(newer) {
                assertThat(row.get("timestampSeconds").isNull()).isTrue();
                assertThat(row.at("/video/sourceCheckReason").asText()).isEqualTo("UNAVAILABLE");
            } else {
                assertThat(row.get("timestampSeconds").isIntegralNumber()).isTrue();
                assertThat(row.get("timestampSeconds").asInt()).isZero();
                assertThat(row.at("/video/sourceCheckReason").isNull()).isTrue();
            }
        }
        verifyNoInteractions(apis);
    }
    @Test void outOfRangePagesPreserveTotalsAndHaveNoNextPage() throws Exception {
        var page=list("?page=99");
        assertThat(ids(page)).isEmpty();
        assertThat(page.get("totalElements").asInt()).isEqualTo(5);
        assertThat(page.get("hasNext").asBoolean()).isFalse();
        var mentions=getJson("/api/admin/restaurants/"+restaurants[0]+"?mentionPage=99").get("mentions");
        assertThat(mentions.get("items").size()).isZero();
        assertThat(mentions.get("totalElements").asInt()).isEqualTo(2);
        assertThat(mentions.get("hasNext").asBoolean()).isFalse();
    }
    @Test void queryLimitCountsUnicodeCodePointsRatherThanUtf16Units() throws Exception {
        String hundred="\uD83C\uDF5C".repeat(100);
        db.update("update restaurants set name=? where id=?",hundred,restaurants[3]);
        assertThat(hundred.length()).isEqualTo(200);
        assertThat(ids(getJsonWithQuery(hundred))).containsExactly(restaurants[3]);
        mvc.perform(get("/api/admin/restaurants").param("query",hundred+"\uD83C\uDF5C"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
    }
    @Test void detailRequiresAuthenticationRoleAndActiveAdministrator() throws Exception {
        String path="/api/admin/restaurants/"+restaurants[0];
        mvc.perform(get(path).with(anonymous())).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(get(path).with(user("normal").roles("USER"))).andExpect(status().isForbidden()).andExpect(content().string(""));
        db.update("update admin_users set enabled=false where id=?",admin);
        try {
            mvc.perform(get(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_INACTIVE"));
        } finally { db.update("update admin_users set enabled=true where id=?",admin); }
    }
    @Test void strictQuerySecurityAndErrors() throws Exception {
        for(String query : List.of("page=-1","size=0","size=101","page=x","page=2147483648","page=2147483647&size=100",
            "publication=BAD","needsSourceReview=1","sort=id","page=0&page=1","query="+"a".repeat(101)))
            mvc.perform(get("/api/admin/restaurants?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
        for(String query : List.of("mentionPage=-1","mentionSize=101","mentionPage=x","page=0","mentionSize=1&mentionSize=2"))
            mvc.perform(get("/api/admin/restaurants/"+restaurants[0]+"?"+query)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/restaurants/no-number")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/restaurants/9223372036854775807")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get("/api/admin/restaurants").with(anonymous())).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(get("/api/admin/restaurants").with(user("normal").roles("USER"))).andExpect(status().isForbidden()).andExpect(content().string(""));
        db.update("update admin_users set enabled=false where id=?",admin);
        try { mvc.perform(get("/api/admin/restaurants")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_INACTIVE")); }
        finally { db.update("update admin_users set enabled=true where id=?",admin); }
        doThrow(new IllegalStateException("private SQL details")).when(repository).countMatching(any());
        mvc.perform(get("/api/admin/restaurants")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
    @Test void repeatableReadKeepsCountPageAndSummaryOnOneSnapshot() throws Exception {
        AtomicBoolean changed = new AtomicBoolean();
        doAnswer(invocation -> {
            long count = (long) invocation.callRealMethod();
            if (changed.compareAndSet(false,true)) {
                // A separate connection commits while the service response is being assembled.
                try(var connection=postgres.createConnection("");var statement=connection.prepareStatement(
                    "update mentions set publication_status='SUSPENDED' where restaurant_id=?")) {
                    statement.setLong(1,restaurants[0]); statement.executeUpdate();
                }
            }
            return count;
        }).when(repository).countMatching(any());
        var page=list("?publication=PUBLISHED");
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(ids(page)).containsExactly(restaurants[4],restaurants[0]);
        assertThat(page.at("/items/1/publicRelationCount").asInt()).isEqualTo(1);
        assertThat(ids(list("?publication=PUBLISHED"))).containsExactly(restaurants[4]);
    }
    @Test void existingJpaAndNativeVideoWritesStartUnchecked() {
        for(boolean nativeInsert : List.of(false,true)) {
            String id=UUID.randomUUID().toString().substring(0,11);
            var video=new kr.sedanji.Models.Video(id,"title","channel",java.time.OffsetDateTime.now(),"description","warning");
            if(nativeInsert) videoService.saveDirectMetadata(video); else videoService.saveMetadata(video);
            assertThat(db.queryForMap("select source_check_status,source_checked_at,source_check_reason from videos where id=?",id))
                .containsEntry("source_check_status","UNCHECKED").containsEntry("source_checked_at",null).containsEntry("source_check_reason",null);
        }
    }
    @Test void draftApprovalDirectAndCandidatePathsAreVisibleWithoutReorderingOldRestaurant() throws Exception {
        var before=list("");
        var body=mapper.createObjectNode().put("restaurantId",restaurants[3]).put("chefId",chefs[0])
            .put("videoId",videos[0]).put("mentionType","VISITED");
        var draft=draftService.create(kr.sedanji.mentiondraft.MentionDraftInput.create(body),UUID.randomUUID().toString(),"admin",new org.springframework.mock.web.MockHttpSession());
        assertThat(getJson("/api/admin/restaurants/"+restaurants[3]).at("/summary/relationCount").asInt()).isZero();
        approval.approve(draft.id(),new kr.sedanji.mentiondraft.MentionDraftApprovalRequest(draft.version()),UUID.randomUUID().toString(),"admin");
        assertThat(ids(list(""))).isEqualTo(ids(before));
        assertThat(getJson("/api/admin/restaurants/"+restaurants[3]).at("/summary/publicRelationCount").asInt()).isEqualTo(1);
        assertThat(list("").at("/items/1/createdAt")).isEqualTo(before.at("/items/1/createdAt"));
        var place=new kr.sedanji.Models.Place(Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()),"직접 등록",null,null,127,37,"https://place.map.kakao.com/1");
        kr.sedanji.ExternalApiFixtures.places(apis,new kr.sedanji.Models.PlaceSearch(List.of(place),1,true));
        var session=new org.springframework.mock.web.MockHttpSession();
        String token=selections.search("식당","admin",session).candidates().get(0).placeSelectionToken();
        var result=direct.approve(new kr.sedanji.registration.DirectRegistrationRequest(token,chefs[0],videos[0],kr.sedanji.mention.MentionType.VISITED,null,null),UUID.randomUUID().toString(),"admin",session);
        assertThat(getJson("/api/admin/restaurants/"+result.restaurantId()).at("/summary/publicRelationCount").asInt()).isEqualTo(1);
        String videoId=UUID.randomUUID().toString().substring(0,11);
        when(apis.video(videoId)).thenReturn(new kr.sedanji.Models.Video(videoId,"후보 영상","채널",java.time.OffsetDateTime.now(),"🥘 후보식당\n📍서울 마포구 월드컵로 10","확인"));
        imports.importVideo("https://youtu.be/"+videoId);
        long candidate=candidates.findCandidates().stream().filter(c -> c.videoId().equals(videoId)).findFirst().orElseThrow().id();
        var candidatePlace=new kr.sedanji.Models.Place(Long.toUnsignedString(UUID.randomUUID().getMostSignificantBits()),"후보 승인","서울 마포구 월드컵로 10","서울 마포구 망원동 10",127,37,"https://place.map.kakao.com/2");
        kr.sedanji.ExternalApiFixtures.places(apis,new kr.sedanji.Models.PlaceSearch(List.of(candidatePlace),1,true));
        mvc.perform(post("/api/admin/candidates/"+candidate+"/places").with(csrf()).contentType("application/json")
            .content("{\"query\":\"후보식당\"}")).andExpect(status().isOk());
        registrations.approveFromCandidate(candidate,chefs[0],candidatePlace.id(),kr.sedanji.mention.MentionType.VISITED,null,"방문 확인",null,"admin");
        assertThat(list("?query=후보 승인").at("/items/0/publicRelationCount").asInt()).isEqualTo(1);
    }
}
