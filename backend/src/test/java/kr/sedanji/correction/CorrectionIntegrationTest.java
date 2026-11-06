package kr.sedanji.correction;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import kr.sedanji.ExternalApis;
import kr.sedanji.Models.Place;
import kr.sedanji.PlaceSelectionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
@WithMockUser(username="admin",roles="ADMIN")
class CorrectionIntegrationTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate db;
    @Autowired ReapprovalFacade facade;@Autowired CorrectionService service;@Autowired CorrectionStore store;
    @Autowired PlaceSelectionService selections;
    @Autowired kr.sedanji.mentiondraft.MentionDraftApprovalFacade createApprovals;
    @Autowired kr.sedanji.restaurant.RestaurantService publishedRestaurants;
    @MockitoSpyBean kr.sedanji.mention.MentionService mentionReads;
    @MockitoBean ExternalApis external;
    long r1,r2,c1,c2,m1,m2,m3,actor;String video="11111111111";
    @BeforeEach void seed(){
        db.execute("truncate restaurants cascade");db.execute("truncate admin_request_receipts");
        actor=db.queryForObject("select id from admin_users where username='admin'",Long.class);db.update("update admin_users set enabled=true where id=?",actor);
        c1=db.queryForObject("insert into chefs(name) values ('같은 셰프') returning id",Long.class);c2=db.queryForObject("insert into chefs(name) values ('다른 셰프') returning id",Long.class);
        db.update("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values (?,'출처','채널',now(),'원본 설명','') on conflict(id) do nothing",video);
        r1=restaurant("123");r2=restaurant("456");m1=mention(r1,c1,"PUBLISHED");m2=mention(r1,c2,"SUSPENDED");m3=mention(r2,c1,"PUBLISHED");
        long job=db.queryForObject("insert into import_jobs(video_id,status,started_at) values (?,'SUCCEEDED',now()) returning id",Long.class,video);
        long candidate=db.queryForObject("insert into restaurant_candidates(import_job_id,extracted_name,evidence,disposition) values (?,'원본 후보','원본 설명 근거','RESOLVED') returning id",Long.class,job);
        db.update("update mentions set restaurant_candidate_id=? where id=?",candidate,m1);
        db.update("insert into admin_audit_events(actor_id,mention_id,action,after_value,reason) values (?,?,'PAST_EVENT','{\"chefName\":\"처리 당시 이름\"}'::jsonb,'과거 사유')",actor,m1);
    }
    long restaurant(String kakao){return db.queryForObject("insert into restaurants(kakao_place_id,name,longitude,latitude,place_url) values (?,'식당',127,37,?) returning id",Long.class,kakao,"https://place.map.kakao.com/"+kakao);}
    long mention(long restaurant,long chef,String publication){return db.queryForObject("insert into mentions(restaurant_id,chef_id,video_id,mention_type,verification_status,publication_status,evidence,timestamp_seconds,reviewed_by,reviewed_at) values (?,?,?,'VISITED','APPROVED',?,'원본 근거',0,?,now()) returning id",Long.class,restaurant,chef,video,publication,actor);}
    JsonNode json(Object value){return mapper.valueToTree(value);}
    JsonNode getJson(String path)throws Exception{return mapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    JsonNode create(long mention,Map<String,Object> changes)throws Exception{return postJson("/api/admin/mentions/"+mention+"/corrections",Map.of("expectedVersion",0,"changes",changes,"changeReason","근거를 확인한 정정"),UUID.randomUUID().toString(),201);}
    JsonNode postJson(String path,Object body,String key,int code)throws Exception{return mapper.readTree(mvc.perform(post(path).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().is(code)).andReturn().getResponse().getContentAsString());}
    JsonNode approve(JsonNode revision,String key,int code)throws Exception{return postJson("/api/admin/mention-corrections/"+revision.path("id").asLong()+"/approve",Map.of("expectedVersion",revision.path("version").asLong(),"expectedImpactHash",revision.path("impactHash").asText()),key,code);}
    @Test void savingAndPatchingPreserveOriginalAndPublicJsonThenApprovalPreservesEvidenceAndZero()throws Exception{
        var publicBefore=getJson("/api/restaurants");var before=store.snapshot(CorrectionKind.MENTION,m1);
        var revision=create(m1,Map.of("mentionType","RECOMMENDED","verificationNote","내부 정정 메모"));
        assertThat(store.snapshot(CorrectionKind.MENTION,m1)).isEqualTo(before);assertThat(getJson("/api/restaurants")).isEqualTo(publicBefore);
        long id=revision.path("id").asLong();
        var updated=mapper.readTree(mvc.perform(patch("/api/admin/mention-corrections/"+id).with(csrf()).contentType("application/json").content("{\"expectedVersion\":0,\"changes\":{\"timestampSeconds\":0,\"verificationNote\":null}}" )).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(updated.path("version").asLong()).isEqualTo(1);assertThat(updated.path("proposedValues").get("verificationNote").isNull()).isTrue();
        assertThat(store.snapshot(CorrectionKind.MENTION,m1)).isEqualTo(before);assertThat(getJson("/api/restaurants")).isEqualTo(publicBefore);
        var applied=approve(updated,"apply-zero",200);assertThat(applied.path("status").asText()).isEqualTo("APPLIED");
        var after=store.snapshot(CorrectionKind.MENTION,m1);assertThat(after.path("version").asLong()).isEqualTo(1);assertThat(after.path("mentionType").asText()).isEqualTo("RECOMMENDED");
        assertThat(after.path("evidence")).isEqualTo(before.path("evidence"));assertThat(after.path("candidateId")).isEqualTo(before.path("candidateId"));assertThat(after.path("timestampSeconds").asInt()).isZero();
        assertThat(getJson("/api/restaurants").toString()).doesNotContain("정정 메모","changeReason","actorUsername","baseSnapshot");
        var events=getJson("/api/admin/mentions/"+m1+"/history");assertThat(events.path("totalElements").asInt()).isEqualTo(4);assertThat(events.toString()).contains("처리 당시 이름","과거 사유");
        assertThat(events.path("items").get(0).path("afterValue").path("actorUsername").asText()).isEqualTo("admin");
        var retry=approve(updated,"apply-zero",200);assertThat(retry).isEqualTo(applied);
        assertThat(getJson("/api/admin/mentions/"+m1+"/history").path("totalElements").asInt()).isEqualTo(4);
        assertThat(approve(updated,"different-key",409).path("code").asText()).isEqualTo("REVISION_ALREADY_APPLIED");
        db.update("update admin_request_receipts set expires_at=now()-interval '1 second',created_at=now()-interval '25 hours' where idempotency_key='apply-zero'");
        assertThat(approve(updated,"apply-zero",409).path("code").asText()).isEqualTo("REVISION_ALREADY_APPLIED");
        verifyNoInteractions(external);
    }
    @Test void sourceNullAndSuspendedArePreservedAndDuplicateApprovalHasNoSideEffects()throws Exception{
        var nullable=new HashMap<String,Object>();nullable.put("timestampSeconds",null);nullable.put("mentionType","REGULAR");
        var draft=create(m2,nullable);approve(draft,"suspended",200);
        var row=store.snapshot(CorrectionKind.MENTION,m2);assertThat(row.path("publicationStatus").asText()).isEqualTo("SUSPENDED");assertThat(row.get("timestampSeconds").isNull()).isTrue();
        var before1=store.snapshot(CorrectionKind.MENTION,m1);var before3=store.snapshot(CorrectionKind.MENTION,m3);
        var duplicate=create(m1,Map.of("restaurantId",r2));long audits=count("admin_audit_events"),receipts=count("admin_request_receipts");
        assertThat(approve(duplicate,"duplicate",409).path("code").asText()).isEqualTo("DUPLICATE_RELATION");
        assertThat(store.snapshot(CorrectionKind.MENTION,m1)).isEqualTo(before1);assertThat(store.snapshot(CorrectionKind.MENTION,m3)).isEqualTo(before3);
        assertThat(count("admin_audit_events")).isEqualTo(audits);assertThat(count("admin_request_receipts")).isEqualTo(receipts);
        assertThat(store.revision(CorrectionKind.MENTION,duplicate.path("id").asLong(),false).status()).isEqualTo("DRAFT");
        db.update("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values ('22222222222','새 영상','채널',now(),'새 설명','')");
        var patchBody=mapper.writeValueAsString(Map.of("expectedVersion",0,"changes",Map.of("restaurantId",r1,"videoId","22222222222")));
        var moved=mapper.readTree(mvc.perform(patch("/api/admin/mention-corrections/"+duplicate.path("id").asLong()).with(csrf()).contentType("application/json").content(patchBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        approve(moved,"new-source",200);var newSource=store.snapshot(CorrectionKind.MENTION,m1);
        assertThat(newSource.path("videoId").asText()).isEqualTo("22222222222");assertThat(newSource.path("candidateId")).isEqualTo(before1.path("candidateId"));assertThat(newSource.path("evidence")).isEqualTo(before1.path("evidence"));
    }
    long count(String table){return db.queryForObject("select count(*) from "+table,Long.class);}
    @Test void explicitlyRebasesStaleTargetsAndAuditsTheOldProposalWithoutApplyingIt()throws Exception{
        var revision=create(m1,Map.of("mentionType","REGULAR"));long id=revision.path("id").asLong();
        db.update("update mentions set version=version+1,review_note='다른 수정' where id=?",m1);
        assertThat(approve(revision,"stale",409).path("code").asText()).isEqualTo("STALE_BASE_VERSION");
        var rebased=postJson("/api/admin/mention-corrections/"+id+"/rebase",Map.of("expectedVersion",0,"expectedBaseVersion",1),"unused",200);
        assertThat(rebased.path("baseVersion").asLong()).isEqualTo(1);assertThat(rebased.path("proposedValues").path("mentionType").asText()).isEqualTo("REGULAR");
        assertThat(rebased.path("proposedValues").path("verificationNote").asText()).isEqualTo("다른 수정");
        assertThat(store.snapshot(CorrectionKind.MENTION,m1).path("mentionType").asText()).isEqualTo("VISITED");
        var event=getJson("/api/admin/mentions/"+m1+"/history").path("items").get(0);assertThat(event.path("action").asText()).isEqualTo("CORRECTION_REBASED");
        assertThat(event.path("beforeValue").path("baseVersion").asLong()).isZero();
        approve(rebased,"rebased",200);
    }
    @Test void sharedNameImpactChangesBlockApprovalAndUnrelatedTargetsRemainUnchanged()throws Exception{
        var revision=postJson("/api/admin/chefs/"+c1+"/revisions",Map.of("expectedVersion",0,"proposedName","표기 정정","changeReason","같은 사람 표기"),"chef-create",201);
        assertThat(revision.path("impact").path("totalElements").asInt()).isEqualTo(2);assertThat(revision.path("publicImpactCount").asInt()).isEqualTo(2);
        String path="/api/admin/chef-revisions/"+revision.path("id").asLong();
        db.update("update mentions set version=version+1 where id=?",m3);
        var request=Map.of("expectedVersion",0,"expectedImpactHash",revision.path("impactHash").asText());
        assertThat(postJson(path+"/approve",request,"chef-stale",409).path("code").asText()).isEqualTo("IMPACT_CHANGED");
        var latest=getJson(path);long before=count("admin_audit_events");
        postJson(path+"/approve",Map.of("expectedVersion",0,"expectedImpactHash",latest.path("impactHash").asText()),"chef-apply",200);
        assertThat(store.snapshot(CorrectionKind.CHEF,c1).path("name").asText()).isEqualTo("표기 정정");
        assertThat(store.snapshot(CorrectionKind.CHEF,c2).path("name").asText()).isEqualTo("다른 셰프");
        assertThat(store.snapshot(CorrectionKind.MENTION,m1).path("chefName").asText()).isEqualTo("표기 정정");
        assertThat(count("admin_audit_events")).isEqualTo(before+1);
        assertThat(getJson("/api/admin/chefs/"+c1+"/history").toString()).contains("같은 셰프","표기 정정");
    }
    @Test void officialPlaceSnapshotMustKeepKakaoIdAndDoesNotNeedTheTokenAtApproval()throws Exception{
        var session=new MockHttpSession();var place=new Place("123","공식 이름",null,"공식 지번",128,36,"https://place.map.kakao.com/123");
        kr.sedanji.PlaceSelectionFixtures.places(external,"공식",place);
        var selection=selections.search("공식","admin",session).candidates().get(0);clearInvocations(external);
        var body=json(Map.of("expectedVersion",0,"placeSelectionToken",selection.placeSelectionToken(),"changeReason","공식 정보 갱신"));
        var revision=facade.write(CorrectionKind.RESTAURANT,r1,"create",body,"place-create","/api/admin/restaurants/"+r1+"/revisions","admin",session);
        session.invalidate();
        var applied=facade.write(CorrectionKind.RESTAURANT,revision.path("id").asLong(),"approve",json(Map.of("expectedVersion",0,"expectedImpactHash",revision.path("impactHash").asText())),"place-approve","/api/admin/restaurant-revisions/"+revision.path("id").asLong()+"/approve","admin",null);
        assertThat(applied.path("status").asText()).isEqualTo("APPLIED");assertThat(store.snapshot(CorrectionKind.RESTAURANT,r1).path("roadAddress").isNull()).isTrue();
        assertThat(store.snapshot(CorrectionKind.RESTAURANT,r1).path("kakaoPlaceId").asText()).isEqualTo("123");
        assertThat(store.snapshot(CorrectionKind.RESTAURANT,r2).path("name").asText()).isEqualTo("식당");
        assertThat(getJson("/api/admin/restaurants/"+r1+"/history").toString()).doesNotContain(selection.placeSelectionToken());verifyNoInteractions(external);
    }
    @Test void strictInputSecurityOpenUniquenessAndNoChangesContracts()throws Exception{
        String path="/api/admin/mentions/"+m1+"/corrections";
        mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/mentions/"+m1+"/history").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/mentions/"+m1+"/history").with(user("user").roles("USER"))).andExpect(status().isForbidden());
        for(var change:List.of(Map.of("chefId","1"),Map.of("chefId",0),Map.of("mentionType","FAKE"),Map.of("timestampSeconds",-1),Map.of("publicationStatus","PUBLISHED")))
            postJson(path,Map.of("expectedVersion",0,"changes",change,"changeReason","사유"),UUID.randomUUID().toString(),400);
        var revision=create(m1,Map.of());assertThat(approve(revision,"no-change",400).path("code").asText()).isEqualTo("NO_CHANGES");
        assertThat(postJson(path,Map.of("expectedVersion",0,"changes",Map.of(),"changeReason","사유"),"second-open",409).path("code").asText()).isEqualTo("OPEN_REVISION_EXISTS");
        mvc.perform(get(path+"?page=0&page=1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/mentions/99999999/history")).andExpect(status().isNotFound());
        db.update("update admin_users set enabled=false where id=?",actor);mvc.perform(get(path)).andExpect(status().isForbidden());
    }
    @Test void legacyCorrectionRequiresExplicitRebaseAndNeverGuessesBaseline()throws Exception{
        long id=db.queryForObject("insert into mention_drafts(restaurant_id,chef_id,video_id,mention_type,target_mention_id,kind,change_reason,base_version,created_by,updated_by) values (?,?,?,'REGULAR',?,'CORRECTION','과거 제안',0,?,?) returning id",Long.class,r1,c1,video,m1,actor,actor);
        var old=getJson("/api/admin/mention-corrections/"+id);assertThat(old.path("baseSnapshot").isNull()).isTrue();
        assertThat(approve(old,"legacy-block",409).path("code").asText()).isEqualTo("STALE_BASE_VERSION");
        var rebased=postJson("/api/admin/mention-corrections/"+id+"/rebase",Map.of("expectedVersion",0,"expectedBaseVersion",0),"none",200);
        assertThat(rebased.path("baseSnapshot").isObject()).isTrue();assertThat(rebased.path("proposedValues").path("mentionType").asText()).isEqualTo("REGULAR");
        assertThat(store.snapshot(CorrectionKind.MENTION,m1).path("mentionType").asText()).isEqualTo("VISITED");
    }
    @Test void creationReceiptAndVersionConflictsNeverOverwriteTheDraft()throws Exception{
        String path="/api/admin/mentions/"+m1+"/corrections";var body=Map.of("expectedVersion",0,"changes",Map.of("mentionType","REGULAR"),"changeReason","같은 요청");
        var created=postJson(path,body,"same-create",201);long audits=count("admin_audit_events");
        assertThat(postJson(path,body,"same-create",201)).isEqualTo(created);assertThat(count("admin_audit_events")).isEqualTo(audits);
        assertThat(postJson(path,Map.of("expectedVersion",0,"changes",Map.of(),"changeReason","다른 요청"),"same-create",409).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        long id=created.path("id").asLong();var before=store.revision(CorrectionKind.MENTION,id,false);
        mvc.perform(patch("/api/admin/mention-corrections/"+id).with(csrf()).contentType("application/json").content("{\"expectedVersion\":9,\"changeReason\":\"잘못된 버전\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
        assertThat(store.revision(CorrectionKind.MENTION,id,false).version()).isEqualTo(before.version());assertThat(store.revision(CorrectionKind.MENTION,id,false).reason()).isEqualTo(before.reason());
        db.update("update chefs set version=version+1,name='공유 이름 변경' where id=?",c1);
        assertThat(approve(created,"reference-stale",409).path("code").asText()).isEqualTo("STALE_BASE_VERSION");
        assertThat(count("admin_audit_events")).isEqualTo(audits);
        var rebased=postJson("/api/admin/mention-corrections/"+id+"/rebase",Map.of("expectedVersion",0,"expectedBaseVersion",0),"none",200);
        assertThat(rebased.path("referenceConflict").asBoolean()).isFalse();approve(rebased,"reference-rebased",200);
        assertThat(getJson(path+"?status=APPLIED&page=9&size=20").path("items").isEmpty()).isTrue();
        assertThat(getJson(path+"?status=APPLIED&page=9&size=20").path("totalElements").asInt()).isEqualTo(1);
    }
    @Test void approvalRollsBackAtEachWriteStageIncludingAfterTargetFlush()throws Exception{
        var revision=create(m1,Map.of("mentionType","REGULAR"));long id=revision.path("id").asLong();
        var before=store.snapshot(CorrectionKind.MENTION,m1);long audits=count("admin_audit_events"),receipts=count("admin_request_receipts");
        for(String table:List.of("mentions","mention_drafts","admin_audit_events","admin_request_receipts")){
            // Failure injection exists only in this Testcontainers database.
            db.execute("create function correction_test_fail() returns trigger language plpgsql as $$ begin raise exception 'test-only stage failure'; end $$");
            db.execute("create trigger correction_test_failure before "+(table.equals("mentions")||table.equals("mention_drafts")?"update":"insert")+" on "+table+" for each row execute function correction_test_fail()");
            try{
                assertThatThrownBy(()->facade.write(CorrectionKind.MENTION,id,"approve",json(Map.of("expectedVersion",0,"expectedImpactHash",revision.path("impactHash").asText())),"failure-"+table,"/api/admin/mention-corrections/"+id+"/approve","admin",null)).isInstanceOf(RuntimeException.class);
            }finally{db.execute("drop trigger correction_test_failure on "+table);db.execute("drop function correction_test_fail()");}
            assertThat(store.snapshot(CorrectionKind.MENTION,m1)).isEqualTo(before);
            assertThat(store.revision(CorrectionKind.MENTION,id,false).status()).isEqualTo("DRAFT");
            assertThat(store.revision(CorrectionKind.MENTION,id,false).version()).isZero();
            assertThat(count("admin_audit_events")).isEqualTo(audits);assertThat(count("admin_request_receipts")).isEqualTo(receipts);
        }
    }
    @Test void concurrentSameReceiptApprovalAppliesAndAuditsOnlyOnce()throws Exception{
        var revision=create(m1,Map.of("mentionType","REGULAR"));long id=revision.path("id").asLong();long audits=count("admin_audit_events");
        var body=json(Map.of("expectedVersion",0,"expectedImpactHash",revision.path("impactHash").asText()));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            Callable<JsonNode> attempt=()->{start.await();return facade.write(CorrectionKind.MENTION,id,"approve",body,"concurrent-receipt","/api/admin/mention-corrections/"+id+"/approve","admin",null);};
            var first=pool.submit(attempt);var second=pool.submit(attempt);start.countDown();
            // Compare the full serialized JSON contract, not Jackson IntNode/LongNode implementation classes.
            assertThat(mapper.readTree(mapper.writeValueAsString(first.get(15,TimeUnit.SECONDS)))).isEqualTo(mapper.readTree(mapper.writeValueAsString(second.get(15,TimeUnit.SECONDS))));
            assertThat(store.snapshot(CorrectionKind.MENTION,m1).path("version").asLong()).isEqualTo(1);assertThat(count("admin_audit_events")).isEqualTo(audits+1);
        }finally{pool.shutdownNow();}
    }
    @Test void existingCreateApprovalParticipatesInSharedImpactSerialization()throws Exception{
        String other="correction-other";db.update("insert into admin_users(username,password_hash,enabled) values (?,'test-only',true) on conflict(username) do update set enabled=true",other);
        long secondActor=db.queryForObject("select id from admin_users where username=?",Long.class,other);
        long draft=db.queryForObject("insert into mention_drafts(restaurant_id,chef_id,video_id,mention_type,kind,created_by,updated_by) values (?,?,?,'VISITED','CREATE',?,?) returning id",Long.class,r2,c2,video,secondActor,secondActor);
        var revision=postJson("/api/admin/chefs/"+c2+"/revisions",Map.of("expectedVersion",0,"proposedName","변경 표기","changeReason","동일인 이름"),"race-create",201);
        long id=revision.path("id").asLong();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var creation=pool.submit(()->{start.await();return createApprovals.approve(draft,new kr.sedanji.mentiondraft.MentionDraftApprovalRequest(0),"existing-create",other);});
            var correction=pool.submit(()->{start.await();try{return facade.write(CorrectionKind.CHEF,id,"approve",json(Map.of("expectedVersion",0,"expectedImpactHash",revision.path("impactHash").asText())),"race-apply","/api/admin/chef-revisions/"+id+"/approve","admin",null).path("status").asText();}catch(CorrectionException e){return e.code();}});
            start.countDown();assertThat(creation.get(15,TimeUnit.SECONDS).restaurantId()).isEqualTo(r2);
            String outcome=correction.get(15,TimeUnit.SECONDS);assertThat(outcome).isIn("APPLIED","IMPACT_CHANGED");
            assertThat(db.queryForObject("select count(*) from mentions where restaurant_id=? and chef_id=? and video_id=?",Long.class,r2,c2,video)).isEqualTo(1);
            assertThat(store.snapshot(CorrectionKind.CHEF,c2).path("name").asText()).isEqualTo(outcome.equals("APPLIED")?"변경 표기":"다른 셰프");
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentPatchAndApprovalCannotLoseOrApplyAnUnreviewedUpdate()throws Exception{
        db.update("insert into admin_users(username,password_hash,enabled) values ('patch-other','test-only',true) on conflict(username) do update set enabled=true");
        var revision=postJson("/api/admin/chefs/"+c1+"/revisions",Map.of("expectedVersion",0,"proposedName","승인 제안","changeReason","표기 사유"),"patch-race-create",201);
        long id=revision.path("id").asLong(),audits=count("admin_audit_events"),receipts=count("admin_request_receipts");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            var patching=pool.submit(()->{start.await();try{facade.write(CorrectionKind.CHEF,id,"patch",json(Map.of("expectedVersion",0,"proposedName","수정 제안")),null,"/api/admin/chef-revisions/"+id,"patch-other",null);return "PATCHED";}catch(CorrectionException e){return e.code();}});
            var approving=pool.submit(()->{start.await();try{facade.write(CorrectionKind.CHEF,id,"approve",json(Map.of("expectedVersion",0,"expectedImpactHash",revision.path("impactHash").asText())),"patch-race-apply","/api/admin/chef-revisions/"+id+"/approve","admin",null);return "APPLIED";}catch(CorrectionException e){return e.code();}});
            start.countDown();String patchResult=patching.get(15,TimeUnit.SECONDS),approveResult=approving.get(15,TimeUnit.SECONDS);
            var stored=store.revision(CorrectionKind.CHEF,id,false);assertThat(stored.version()).isEqualTo(1);assertThat(count("admin_audit_events")).isEqualTo(audits+1);
            if(patchResult.equals("PATCHED")){
                assertThat(approveResult).isEqualTo("STALE_VERSION");assertThat(stored.status()).isEqualTo("DRAFT");assertThat(stored.proposal().path("name").asText()).isEqualTo("수정 제안");
                assertThat(store.snapshot(CorrectionKind.CHEF,c1).path("name").asText()).isEqualTo("같은 셰프");assertThat(count("admin_request_receipts")).isEqualTo(receipts);
            }else{
                assertThat(patchResult).isEqualTo("REVISION_ALREADY_APPLIED");assertThat(approveResult).isEqualTo("APPLIED");assertThat(stored.status()).isEqualTo("APPLIED");
                assertThat(store.snapshot(CorrectionKind.CHEF,c1).path("name").asText()).isEqualTo("승인 제안");assertThat(count("admin_request_receipts")).isEqualTo(receipts+1);
            }
        }finally{pool.shutdownNow();}
    }
    @Test void publicMultiQueryResponseKeepsOneSnapshotAcrossConcurrentSharedApproval()throws Exception{
        var revision=postJson("/api/admin/chefs/"+c1+"/revisions",Map.of("expectedVersion",0,"proposedName","새 공개 표기","changeReason","동일인 표기"),"public-snapshot-create",201);
        var reachedSources=new CountDownLatch(1);var release=new CountDownLatch(1);var reading=new ThreadLocal<Boolean>();
        doAnswer(inv->{if(Boolean.TRUE.equals(reading.get())){reachedSources.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("source read timeout");}return inv.callRealMethod();})
            .when(mentionReads).findPublishedSources(r1);
        var pool=Executors.newSingleThreadExecutor();
        try{
            var response=pool.submit(()->{reading.set(true);try{return publishedRestaurants.findPublishedRestaurants();}finally{reading.remove();}});
            assertThat(reachedSources.await(10,TimeUnit.SECONDS)).isTrue();
            long id=revision.path("id").asLong();facade.write(CorrectionKind.CHEF,id,"approve",json(Map.of("expectedVersion",0,"expectedImpactHash",revision.path("impactHash").asText())),"public-snapshot-apply","/api/admin/chef-revisions/"+id+"/approve","admin",null);
            release.countDown();var old=response.get(15,TimeUnit.SECONDS);
            assertThat(old.stream().flatMap(r->r.mentions().stream()).map(s->s.chefName()).toList()).contains("같은 셰프").doesNotContain("새 공개 표기");
            assertThat(publishedRestaurants.findPublishedRestaurants().stream().flatMap(r->r.mentions().stream()).map(s->s.chefName()).toList()).contains("새 공개 표기").doesNotContain("같은 셰프");
        }finally{release.countDown();pool.shutdownNow();}
    }
}
