package kr.sedanji.publication;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import kr.sedanji.ExternalApis;
import kr.sedanji.correction.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class PublicationIntegrationTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate db;
    @Autowired PublicationFacade facade; @Autowired PublicationQueryService query; @Autowired CorrectionStore store;
    @Autowired ReapprovalFacade corrections;
    @Autowired kr.sedanji.mentiondraft.MentionDraftApprovalFacade draftApprovals;
    @Autowired kr.sedanji.restaurant.RestaurantService publicReads;
    @MockitoSpyBean kr.sedanji.mention.MentionService mentionReads;
    @MockitoBean ExternalApis external;
    long actor,other,r1,r2,c1,c2,m1,m2,m3,m4,m5,m6;
    @BeforeEach void seed() {
        db.execute("truncate restaurants cascade");db.execute("truncate admin_request_receipts");
        db.update("update admin_users set enabled=true where username='admin'");
        actor=db.queryForObject("select id from admin_users where username='admin'",Long.class);
        db.update("insert into admin_users(username,password_hash,enabled) values ('publication-other','test-only',true) on conflict(username) do update set enabled=true");
        other=db.queryForObject("select id from admin_users where username='publication-other'",Long.class);
        c1=db.queryForObject("insert into chefs(name) values ('중단 전용 셰프') returning id",Long.class);
        c2=db.queryForObject("insert into chefs(name) values ('유지 셰프') returning id",Long.class);
        for(String v:List.of("11111111111","22222222222","33333333333"))
            db.update("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values (?,?,'채널',now(),'원문','') on conflict(id) do nothing",v,"영상 "+v);
        r1=restaurant("publication-1");r2=restaurant("publication-2");
        m1=mention(r1,c1,"11111111111","APPROVED","PUBLISHED",0);
        m2=mention(r1,c2,"22222222222","APPROVED","PUBLISHED",null);
        m3=mention(r2,c1,"11111111111","APPROVED","PUBLISHED",null);
        m4=mention(r2,c2,"22222222222","APPROVED","SUSPENDED",0);
        m5=mention(r1,c1,"33333333333","PENDING","SUSPENDED",null);
        m6=mention(r1,c2,"33333333333","REJECTED","SUSPENDED",null);
        db.update("update mentions set mention_type='MENTIONED' where id=?",m2);
        long job=db.queryForObject("insert into import_jobs(video_id,status,started_at) values ('11111111111','SUCCEEDED',now()) returning id",Long.class);
        long candidate=db.queryForObject("insert into restaurant_candidates(import_job_id,extracted_name,evidence,disposition) values (?,'원본 후보','원본 근거','RESOLVED') returning id",Long.class,job);
        db.update("update mentions set restaurant_candidate_id=? where id=?",candidate,m1);
        db.update("insert into admin_audit_events(actor_id,mention_id,action,reason,after_value) values (?,?,'PAST_EVENT','과거 사유','{\"original\":true}'::jsonb)",actor,m1);
    }
    long restaurant(String place) {return db.queryForObject("insert into restaurants(kakao_place_id,name,road_address,longitude,latitude,place_url) values (?,?,'지점 주소',127,37,'https://place.map.kakao.com/123') returning id",Long.class,place,place);}
    long mention(long r,long c,String v,String verification,String publication,Integer timestamp) {
        return db.queryForObject("insert into mentions(restaurant_id,chef_id,video_id,mention_type,verification_status,publication_status,evidence,review_note,timestamp_seconds,reviewed_by,reviewed_at) values (?,?,?,'VISITED',?,?,'보존 근거','내부 메모',?,?,now()-interval '1 day') returning id",Long.class,r,c,v,verification,publication,timestamp,actor);
    }
    String path(long id,String action) {return "/api/admin/mentions/"+id+"/"+action;}
    JsonNode getJson(String path)throws Exception {return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    JsonNode postJson(long id,String action,Object body,String key,int status)throws Exception {
        return mapper.readTree(mvc.perform(post(path(id,action)).with(csrf()).header("Idempotency-Key",key).contentType("application/json").content(mapper.writeValueAsString(body)))
            .andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
    }
    JsonNode snapshot(long id) {return store.snapshot(CorrectionKind.MENTION,id);}
    Map<String,Object> suspend(long version) {return Map.of("expectedVersion",version,"reason"," 관계 근거 검토 ");}
    Map<String,Object> restore(long id,String username) {
        var context=query.get(id,username);return Map.of("expectedVersion",context.path("mention").path("version").asLong(),
            "expectedContextHash",context.path("contextHash").asText(),"reason","복구 사유","evidenceRechecked",true,"recheckNote","다시 확인한 내용");
    }
    long count(String table) {return db.queryForObject("select count(*) from "+table,Long.class);}
    JsonNode write(long id,boolean restore,Object body,String key,String username) {return facade.write(id,restore,mapper.valueToTree(body),key,username);}

    @Test void suspendsOnlyTheTargetAndHidesTheLastPublicRestaurantWithoutLeakingInternalData()throws Exception {
        var before=snapshot(m1);var otherMention=snapshot(m2);var otherRestaurant=store.snapshot(CorrectionKind.RESTAURANT,r2);
        var ownRestaurant=store.snapshot(CorrectionKind.RESTAURANT,r1);var otherSource=snapshot(m3);
        var chefBefore=store.snapshot(CorrectionKind.CHEF,c1);var videoBefore=db.queryForMap("select * from videos where id='11111111111'");
        var candidate=db.queryForMap("select * from restaurant_candidates where id=?",before.path("candidateId").asLong());
        var past=getJson(path(m1,"history")).path("items").get(0);
        var result=postJson(m1,"suspend",suspend(0),"suspend-first",200);
        assertThat(result.path("previousVersion").asLong()).isZero();assertThat(result.path("version").asLong()).isEqualTo(1);
        var after=snapshot(m1);
        for(String field:List.of("reviewedBy","reviewedAt","chefId","restaurantId","videoId","mentionType","timestampSeconds","verificationNote","evidence","candidateId"))
            assertThat(after.path(field)).as(field).isEqualTo(before.path(field));
        assertThat(after.path("publicationStatus").asText()).isEqualTo("SUSPENDED");assertThat(snapshot(m2)).isEqualTo(otherMention);
        assertThat(store.snapshot(CorrectionKind.RESTAURANT,r2)).isEqualTo(otherRestaurant);
        assertThat(store.snapshot(CorrectionKind.RESTAURANT,r1)).isEqualTo(ownRestaurant);assertThat(snapshot(m3)).isEqualTo(otherSource);
        assertThat(store.snapshot(CorrectionKind.CHEF,c1)).isEqualTo(chefBefore);assertThat(db.queryForMap("select * from videos where id='11111111111'")).isEqualTo(videoBefore);
        assertThat(db.queryForMap("select * from restaurant_candidates where id=?",before.path("candidateId").asLong())).isEqualTo(candidate);
        var publicJson=getJson("/api/restaurants");assertThat(publicJson.size()).isEqualTo(2);
        for(var restaurant:publicJson)if(restaurant.path("id").asLong()==r1) {
            assertThat(restaurant.path("mentions").size()).isEqualTo(1);assertThat(restaurant.path("mentions").get(0).path("mentionId").asLong()).isEqualTo(m2);
            assertThat(restaurant.toString()).doesNotContain("중단 전용 셰프","11111111111","VISITED","내부 메모","보존 근거","reviewedBy","관계 근거 검토");
            assertThat(restaurant.path("mentions").get(0).path("timestampSeconds").isNull()).isTrue();
        }
        postJson(m3,"suspend",suspend(0),"last-source",200);
        assertThat(getJson("/api/restaurants").size()).isEqualTo(1);
        var detail=getJson("/api/admin/restaurants/"+r2);
        assertThat(detail.path("summary").path("relationCount").asInt()).isEqualTo(2);
        assertThat(detail.path("summary").path("publicRelationCount").asInt()).isZero();
        var hidden=getJson("/api/admin/restaurants?publication=NO_PUBLIC_RELATION");
        assertThat(hidden.path("items").size()).isEqualTo(1);assertThat(hidden.path("items").get(0).path("id").asLong()).isEqualTo(r2);
        var history=getJson(path(m1,"history"));assertThat(history.path("items").get(1)).isEqualTo(past);
        assertThat(query.get(m1,"admin").path("lastSuspendReason").asText()).isEqualTo("관계 근거 검토");
        assertThat(query.get(m4,"admin").path("lastSuspendReason").isNull()).isTrue();
        postJson(m3,"restore",restore(m3,"admin"),"restore-last-source",200);
        assertThat(getJson("/api/restaurants").size()).isEqualTo(2);
        assertThat(getJson("/api/admin/restaurants/"+r2).path("summary").path("publicRelationCount").asInt()).isEqualTo(1);
        verifyNoInteractions(external);
    }
    @Test void restorationReapprovesAndAuditsTheValidatedPreApplyHashThenOldReceiptCannotUndoIt()throws Exception {
        var initial=snapshot(m1);var suspended=postJson(m1,"suspend",suspend(0),"roundtrip",200);
        var request=restore(m1,"publication-other");String h0=request.get("expectedContextHash").toString();
        var restored=write(m1,true,request,"restore","publication-other");var after=snapshot(m1);
        assertThat(after.path("reviewedBy").asLong()).isEqualTo(other);assertThat(after.path("reviewedAt")).isNotEqualTo(initial.path("reviewedAt"));
        for(String field:List.of("chefId","restaurantId","videoId","mentionType","timestampSeconds","verificationNote","evidence","candidateId"))assertThat(after.path(field)).isEqualTo(initial.path(field));
        var event=getJson(path(m1,"history")).path("items").get(0);
        assertThat(event.path("beforeValue").path("reviewedBy").asLong()).isEqualTo(actor);
        var confirmation=event.path("afterValue").path("publicationConfirmation");
        assertThat(confirmation.path("validatedContextHash").asText()).isEqualTo(h0);
        assertThat(confirmation.path("recheckNote").asText()).isEqualTo("다시 확인한 내용");
        assertThat(query.get(m1,"admin").path("contextHash").asText()).isNotEqualTo(h0);
        assertThat(event.path("id").asLong()).isEqualTo(restored.path("auditEventId").asLong());
        assertThat(java.time.OffsetDateTime.parse(event.path("occurredAt").asText()).toInstant()).isEqualTo(java.time.OffsetDateTime.parse(restored.path("occurredAt").asText()).toInstant());
        long audits=count("admin_audit_events");
        assertThat(postJson(m1,"suspend",suspend(0),"roundtrip",200)).isEqualTo(suspended);
        assertThat(snapshot(m1)).isEqualTo(after);assertThat(count("admin_audit_events")).isEqualTo(audits);
        assertThat(store.parse(store.json(write(m1,true,request,"restore","publication-other")))).isEqualTo(store.parse(store.json(restored)));
        assertThat(count("admin_audit_events")).isEqualTo(audits);
        assertThat(postJson(m1,"suspend",Map.of("expectedVersion",0,"reason","관계 근거 검토"),"roundtrip",200)).isEqualTo(suspended);
        assertThat(postJson(m1,"suspend",suspend(0),"new-key",409).path("code").asText()).isEqualTo("STALE_VERSION");
        assertThat(postJson(m1,"restore",restore(m1,"admin"),"already",409).path("code").asText()).isEqualTo("ALREADY_PUBLISHED");
        assertThat(postJson(m1,"restore",restore(m1,"admin"),"roundtrip",409).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(postJson(m2,"suspend",suspend(0),"roundtrip",409).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(postJson(m1,"suspend",Map.of("expectedVersion",2,"reason","다른 본문"),"roundtrip",409).path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(()->corrections.write(CorrectionKind.MENTION,m1,"create",mapper.valueToTree(Map.of("expectedVersion",2,"changes",Map.of("mentionType","REGULAR"),"changeReason","다른 작업")),"roundtrip",path(m1,"corrections"),"admin",null))
            .isInstanceOfSatisfying(CorrectionException.class,e->assertThat(e.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }
    @Test void validationPriorityContextChangesAndUnsupportedStatesNeverWrite()throws Exception {
        long audits=count("admin_audit_events");
        for(long id:List.of(m5,m6))for(String action:List.of("suspend","restore"))
            assertThat(postJson(id,action,action.equals("restore")?restore(id,"admin"):suspend(0),UUID.randomUUID().toString(),409).path("code").asText()).isEqualTo("INVALID_STATE");
        assertThat(postJson(m4,"suspend",suspend(1),"stale-first",409).path("code").asText()).isEqualTo("STALE_VERSION");
        assertThat(postJson(m4,"suspend",suspend(0),"same-state",409).path("code").asText()).isEqualTo("ALREADY_SUSPENDED");
        var old=restore(m4,"admin");
        db.update("update chefs set version=version+1,name='표시 정정' where id=?",c2);
        assertThat(postJson(m4,"restore",old,"changed-chef",409).path("code").asText()).isEqualTo("CONTEXT_CHANGED");
        var beforePlace=restore(m4,"admin");db.update("update restaurants set road_address='새 지점 주소',version=version+1 where id=?",r2);
        assertThat(postJson(m4,"restore",beforePlace,"changed-place",409).path("code").asText()).isEqualTo("CONTEXT_CHANGED");
        assertThat(count("admin_audit_events")).isEqualTo(audits);assertThat(count("admin_request_receipts")).isZero();
        db.update("update videos set source_check_status='AVAILABLE',source_checked_at=now(),source_check_reason=null where id='22222222222'");
        assertThat(snapshot(m4).path("publicationStatus").asText()).isEqualTo("SUSPENDED");
        db.update("update videos set source_check_status='REVIEW_REQUIRED',source_checked_at=now(),source_check_reason='UNAVAILABLE' where id='22222222222'");
        postJson(m4,"restore",restore(m4,"admin"),"rechecked",200);
    }
    @Test void strictInputInactiveReplayAndSecurityKeepTheirContracts()throws Exception {
        String suspendPath=path(m1,"suspend"),readPath=path(m1,"publication");
        mvc.perform(get(readPath).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get(readPath).with(user("user").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post(suspendPath).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(suspendPath).with(csrf()).contentType("application/json").content(store.json(suspend(0)))).andExpect(status().isBadRequest());
        for(Object version:List.of("0",0.5,-1,true))postJson(m1,"suspend",Map.of("expectedVersion",version,"reason","사유"),UUID.randomUUID().toString(),400);
        for(Object reason:List.of(" ","x".repeat(2001),1,false))postJson(m1,"suspend",Map.of("expectedVersion",0,"reason",reason),UUID.randomUUID().toString(),400);
        postJson(m1,"suspend",Map.of("expectedVersion",0,"reason","사유","extra",true),"extra",400);
        postJson(m1,"suspend",suspend(0),"x".repeat(129),400);
        for(Object checked:List.of(false,"true",1)) {
            var body=new HashMap<>(restore(m4,"admin"));body.put("evidenceRechecked",checked);
            var error=postJson(m4,"restore",body,UUID.randomUUID().toString(),400);
            assertThat(error.path("code").asText()).isEqualTo(checked.equals(false)?"RECHECK_REQUIRED":"INVALID_INPUT");
        }
        for(String field:List.of("reason","recheckNote","expectedContextHash","expectedVersion")) {
            var body=new HashMap<>(restore(m4,"admin"));body.remove(field);postJson(m4,"restore",body,UUID.randomUUID().toString(),400);
        }
        for(Object note:List.of(" ","x".repeat(2001),true,7)) {
            var body=new HashMap<>(restore(m4,"admin"));body.put("recheckNote",note);postJson(m4,"restore",body,UUID.randomUUID().toString(),400);
        }
        mvc.perform(get("/api/admin/mentions/0/publication")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/mentions/1.5/publication")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/mentions/9223372036854775807/publication")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        postJson(m1,"suspend",suspend(0),"active-replay",200);
        db.update("update admin_users set enabled=false where id=?",actor);
        assertThat(postJson(m1,"suspend",suspend(0),"active-replay",403).path("code").asText()).isEqualTo("ADMIN_INACTIVE");
        mvc.perform(get(readPath)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_INACTIVE"));
    }
    @Test void expiredReceiptDeletionAndEveryWriteStageRollBackTogether()throws Exception {
        postJson(m1,"suspend",suspend(0),"expired",200);
        db.update("update admin_request_receipts set created_at=now()-interval '25 hours',expires_at=now()-interval '1 hour' where idempotency_key='expired'");
        var before=snapshot(m1);long audits=count("admin_audit_events");
        var receipt=db.queryForMap("select * from admin_request_receipts where idempotency_key='expired'");
        var body=restore(m1,"admin");
        for(String table:List.of("mentions","admin_audit_events","admin_request_receipts")) {
            db.execute("create function publication_test_fail() returns trigger language plpgsql as $$ begin raise exception 'test-only failure'; end $$");
            db.execute("create trigger publication_test_failure before "+(table.equals("mentions")?"update":"insert")+" on "+table+" for each row execute function publication_test_fail()");
            try {assertThatThrownBy(()->write(m1,true,body,"expired","admin")).isInstanceOf(RuntimeException.class);}
            finally {db.execute("drop trigger publication_test_failure on "+table);db.execute("drop function publication_test_fail()");}
            assertThat(snapshot(m1)).isEqualTo(before);assertThat(count("admin_audit_events")).isEqualTo(audits);
            assertThat(db.queryForMap("select * from admin_request_receipts where idempotency_key='expired'")).isEqualTo(receipt);
        }
        assertThat(postJson(m1,"suspend",suspend(0),"expired",409).path("code").asText()).isEqualTo("STALE_VERSION");
        assertThat(db.queryForMap("select * from admin_request_receipts where idempotency_key='expired'")).isEqualTo(receipt);
        write(m1,true,body,"expired","admin");
        assertThat(snapshot(m1).path("publicationStatus").asText()).isEqualTo("PUBLISHED");assertThat(count("admin_request_receipts")).isEqualTo(1);
    }
    JsonNode createCorrection(long id)throws Exception {
        return mapper.readTree(mvc.perform(post(path(id,"corrections")).with(csrf()).header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(store.json(Map.of("expectedVersion",snapshot(id).path("version").asLong(),"changes",Map.of("mentionType","REGULAR"),"changeReason","유형 정정"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode approveCorrection(JsonNode revision,String user) {
        long id=revision.path("id").asLong();return corrections.write(CorrectionKind.MENTION,id,"approve",mapper.valueToTree(Map.of("expectedVersion",revision.path("version").asLong(),"expectedImpactHash",revision.path("impactHash").asText())),UUID.randomUUID().toString(),"/api/admin/mention-corrections/"+id+"/approve",user,null);
    }
    @Test void correctionOnSuspendedStaysSuspendedAndPublicationInvalidatesAnOlderCorrection()throws Exception {
        var revision=createCorrection(m1);postJson(m1,"suspend",suspend(0),"invalidate",200);
        assertThatThrownBy(()->approveCorrection(revision,"admin")).isInstanceOfSatisfying(CorrectionException.class,e->assertThat(e.code()).isEqualTo("STALE_BASE_VERSION"));
        var suspendedRevision=createCorrection(m4);approveCorrection(suspendedRevision,"admin");
        assertThat(snapshot(m4).path("publicationStatus").asText()).isEqualTo("SUSPENDED");
        assertThat(snapshot(m4).path("mentionType").asText()).isEqualTo("REGULAR");
        var before=snapshot(m4);
        long draft=db.queryForObject("insert into mention_drafts(restaurant_id,chef_id,video_id,mention_type,kind,created_by,updated_by) values (?,?,?,'VISITED','CREATE',?,?) returning id",Long.class,r2,c2,"22222222222",actor,actor);
        assertThatThrownBy(()->draftApprovals.approve(draft,new kr.sedanji.mentiondraft.MentionDraftApprovalRequest(0),"duplicate-create","admin"))
            .isInstanceOfSatisfying(kr.sedanji.mentiondraft.MentionDraftApprovalException.class,e->assertThat(e.code()).isEqualTo("DUPLICATE_RELATION"));
        assertThat(snapshot(m4)).isEqualTo(before);
    }
    @Test void concurrentSameReceiptIsExactlyOneTransition()throws Exception {
        long audits=count("admin_audit_events");var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<JsonNode> attempt=()->{start.await();return write(m1,false,suspend(0),"same-concurrent","admin");};
            var first=pool.submit(attempt);var second=pool.submit(attempt);start.countDown();
            assertThat(store.parse(store.json(first.get(15,TimeUnit.SECONDS)))).isEqualTo(store.parse(store.json(second.get(15,TimeUnit.SECONDS))));
            assertThat(snapshot(m1).path("version").asLong()).isEqualTo(1);assertThat(count("admin_audit_events")).isEqualTo(audits+1);
        } finally {pool.shutdownNow();}
    }
    @Test void receiptNamespaceIsPerAdministratorAndSharedWithExistingWrites() {
        write(m1,false,suspend(0),"per-admin","admin");
        write(m2,false,suspend(0),"per-admin","publication-other");
        assertThat(count("admin_request_receipts")).isEqualTo(2);
        assertThat(snapshot(m1).path("publicationStatus").asText()).isEqualTo("SUSPENDED");
        assertThat(snapshot(m2).path("publicationStatus").asText()).isEqualTo("SUSPENDED");
    }
    @Test void concurrentSuspendRestoreAndCorrectionNeverLoseUpdatesOrPublishTheWrongState()throws Exception {
        var revision=createCorrection(m1);var pool=Executors.newFixedThreadPool(3);var start=new CountDownLatch(1);
        var restoreBody=restore(m1,"publication-other");long audits=count("admin_audit_events");
        try {
            var suspend=pool.submit(()->{start.await();try{write(m1,false,suspend(0),"race-suspend","admin");return "SUSPENDED";}catch(PublicationException e){return e.code();}});
            var restore=pool.submit(()->{start.await();try{write(m1,true,restoreBody,"race-restore","publication-other");return "PUBLISHED";}catch(PublicationException e){return e.code();}});
            var correction=pool.submit(()->{start.await();try{approveCorrection(revision,"publication-other");return "APPLIED";}catch(CorrectionException e){return e.code();}});
            start.countDown();String s=suspend.get(15,TimeUnit.SECONDS),r=restore.get(15,TimeUnit.SECONDS),c=correction.get(15,TimeUnit.SECONDS);
            assertThat(r).isIn("ALREADY_PUBLISHED","STALE_VERSION");
            assertThat(snapshot(m1).path("version").asLong()).isEqualTo(1);
            assertThat(count("admin_audit_events")).isEqualTo(audits+1);
            if(s.equals("SUSPENDED")) {assertThat(c).isEqualTo("STALE_BASE_VERSION");assertThat(snapshot(m1).path("publicationStatus").asText()).isEqualTo("SUSPENDED");}
            else {assertThat(s).isEqualTo("STALE_VERSION");assertThat(c).isEqualTo("APPLIED");assertThat(snapshot(m1).path("mentionType").asText()).isEqualTo("REGULAR");}
        } finally {pool.shutdownNow();}
    }
    @Test void publicResponseRetainsOneSnapshotDuringTheLastSourceSuspension()throws Exception {
        var reached=new CountDownLatch(1);var release=new CountDownLatch(1);var reading=new ThreadLocal<Boolean>();
        doAnswer(inv->{if(Boolean.TRUE.equals(reading.get())) {reached.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("read timeout");}return inv.callRealMethod();})
            .when(mentionReads).findPublishedSources(r2);
        var pool=Executors.newSingleThreadExecutor();
        try {
            var response=pool.submit(()->{reading.set(true);try{return publicReads.findPublishedRestaurants();}finally{reading.remove();}});
            assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();write(m3,false,suspend(0),"snapshot","admin");release.countDown();
            var old=response.get(15,TimeUnit.SECONDS);assertThat(old.stream().filter(r->r.id()==r2).findFirst().orElseThrow().mentions()).hasSize(1);
            assertThat(publicReads.findPublishedRestaurants().stream().map(r->r.id()).toList()).doesNotContain(r2);
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void concurrentActualRestoreAndCorrectionCannotApplyAnUnreviewedBaseline()throws Exception {
        var revision=createCorrection(m4);var restoreBody=restore(m4,"admin");long audits=count("admin_audit_events");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var restoring=pool.submit(()->{start.await();try{write(m4,true,restoreBody,"restore-race","admin");return "RESTORED";}catch(PublicationException e){return e.code();}});
            var correcting=pool.submit(()->{start.await();try{approveCorrection(revision,"publication-other");return "APPLIED";}catch(CorrectionException e){return e.code();}});
            start.countDown();String r=restoring.get(15,TimeUnit.SECONDS),c=correcting.get(15,TimeUnit.SECONDS);
            assertThat(snapshot(m4).path("version").asLong()).isEqualTo(1);assertThat(count("admin_audit_events")).isEqualTo(audits+1);
            if(r.equals("RESTORED")) {assertThat(c).isEqualTo("STALE_BASE_VERSION");assertThat(snapshot(m4).path("publicationStatus").asText()).isEqualTo("PUBLISHED");assertThat(snapshot(m4).path("mentionType").asText()).isEqualTo("VISITED");}
            else {assertThat(r).isEqualTo("STALE_VERSION");assertThat(c).isEqualTo("APPLIED");assertThat(snapshot(m4).path("publicationStatus").asText()).isEqualTo("SUSPENDED");}
        } finally {pool.shutdownNow();}
    }
}
