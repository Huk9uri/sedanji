package kr.sedanji.publicweb;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class PublicIntegrationTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl); r.add("spring.datasource.username",postgres::getUsername); r.add("spring.datasource.password",postgres::getPassword);
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate db;
    @Autowired NamedParameterJdbcTemplate named;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PublicQueryService service;
    @MockitoSpyBean PublicQueryRepository repository;
    long c1,c2,admin; long[] restaurants; String[] videos;
    static final String AREA="/api/public/restaurants?west=126.96&south=37.55&east=126.99&north=37.58";
    @BeforeEach void seed() {
        // All mutation in this suite targets only its isolated Testcontainers database.
        db.execute("truncate restaurants,chefs,videos cascade");
        admin=db.queryForObject("select id from admin_users where username='admin'",Long.class);
        c1=db.queryForObject("insert into chefs(name) values ('A 셰프') returning id",Long.class);
        c2=db.queryForObject("insert into chefs(name) values ('B%_! 셰프') returning id",Long.class);
        videos=new String[]{"PUBVIDEO001","PUBVIDEO002","PUBVIDEO003"};
        for (String v:videos) db.update("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values (?,'영상','채널',now(),'내부 원문','내부 경고')",v);
        restaurants=new long[7];
        double[][] coordinates={{126.97,37.56},{126.98,37.57},{127.1,37.56},{126.97,37.56},{126.96,37.55},{126.99,37.58},{126.96,37.58}};
        for(int i=0;i<coordinates.length;i++) restaurants[i]=place("R"+i,coordinates[i][0],coordinates[i][1]);
        mention(0,c1,0,null); mention(0,c1,1,0); mention(0,c2,2,793);
        for(int i:new int[]{1,2,3,4,5,6}) mention(i,c1,0,null);
        db.update("update mentions set publication_status='SUSPENDED' where restaurant_id=?",restaurants[3]);
    }
    long place(String name,double lon,double lat) {
        return db.queryForObject("insert into restaurants(kakao_place_id,name,road_address,address,longitude,latitude,place_url) values (?,?, '도로','지번',?,?,'https://place.map.kakao.com/1') returning id",Long.class,UUID.randomUUID().toString(),name,lon,lat);
    }
    void mention(int r,long chef,int video,Integer seconds) {
        db.update("insert into mentions(restaurant_id,chef_id,video_id,mention_type,verification_status,publication_status,timestamp_seconds,reviewed_by,reviewed_at,review_note) values (?,?,?,'VISITED','APPROVED','PUBLISHED',?,?,now(),'내부 메모')",restaurants[r],chef,videos[video],seconds,admin);
    }
    JsonNode json(String path) throws Exception { return mapper.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    List<Long> ids(JsonNode node) { var ids=new ArrayList<Long>(); node.get("items").forEach(n->ids.add(n.get("id").asLong())); return ids; }
    @Test void inclusiveBoundsDistinctRestaurantsAndGlobalChefs() throws Exception {
        mvc.perform(get(AREA)).andExpect(header().string("Cache-Control","no-store"));
        assertThat(ids(json(AREA))).containsExactly(restaurants[0],restaurants[1],restaurants[4],restaurants[5],restaurants[6]);
        var filtered=json(AREA+"&chefId="+c2); assertThat(ids(filtered)).containsExactly(restaurants[0]);
        assertThat(filtered.at("/items/0/chefCount").asLong()).isEqualTo(2);
        var chefs=json("/api/public/chefs"); assertThat(chefs.at("/items/0/restaurantCount").asLong()).isEqualTo(6);
        var literalSearch=mapper.readTree(mvc.perform(get("/api/public/chefs").param("query","%_!"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(literalSearch.get("totalElements").asLong()).isEqualTo(1);
        assertThat(ids(json(AREA+"&chefId=9223372036854775807"))).isEmpty();
    }
    @Test void sourcePagingVisibilityAndLegacyShape() throws Exception {
        String path="/api/public/restaurants/"+restaurants[0];
        var detail=json(path); assertThat(detail.at("/sources/totalElements").asInt()).isEqualTo(3);
        assertThat(detail.at("/sources/items/0/timestampSeconds").isNull()).isTrue();
        assertThat(detail.at("/sources/items/1/timestampSeconds").asInt()).isZero();
        assertThat(detail.at("/sources/items/2/timestampSeconds").asInt()).isEqualTo(793);
        assertThat(json(path+"?sourcePage=8").at("/sources/items").isEmpty()).isTrue();
        for(String field:List.of("evidence","reviewNote","reviewedBy","sourceCheckStatus","description","extractionWarning")) {
            assertThat(detail.toString()).doesNotContain('"'+field+'"'); assertThat(json("/api/restaurants").toString()).doesNotContain('"'+field+'"');
        }
        assertThat(json("/api/restaurants").isArray()).isTrue();
        db.update("update mentions set publication_status='SUSPENDED' where restaurant_id=? and chef_id=?",restaurants[0],c1);
        assertThat(json(path).at("/sources/totalElements").asInt()).isEqualTo(1);
        db.update("update mentions set publication_status='SUSPENDED' where restaurant_id=?",restaurants[0]);
        var hidden=mvc.perform(get(path)).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(mvc.perform(get("/api/public/restaurants/9223372036854775807")).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString()).isEqualTo(hidden);
        assertThat(ids(json(AREA))).doesNotContain(restaurants[0]);
        db.update("update mentions set publication_status='PUBLISHED' where restaurant_id=?",restaurants[0]); assertThat(ids(json(AREA))).contains(restaurants[0]);
    }
    @Test void chefPreviewCapDistinctCountsAndStableSearchPages() throws Exception {
        var same=new ArrayList<Long>();
        for(int i=0;i<2;i++) {
            long chef=db.queryForObject("insert into chefs(name) values ('동명 셰프') returning id",Long.class);
            same.add(chef); mention(0,chef,0,null);
        }
        var preview=json(AREA).at("/items/0");
        assertThat(preview.get("chefs").size()).isEqualTo(3); assertThat(preview.get("chefCount").asLong()).isEqualTo(4);
        assertThat(preview.get("hasMoreChefs").asBoolean()).isTrue();
        var first=json("/api/public/chefs?query=동명&size=1"); var second=json("/api/public/chefs?query=동명&size=1&page=1");
        assertThat(first.get("totalElements").asLong()).isEqualTo(2); assertThat(first.get("hasNext").asBoolean()).isTrue();
        assertThat(first.at("/items/0/id").asLong()).isEqualTo(same.get(0)); assertThat(second.at("/items/0/id").asLong()).isEqualTo(same.get(1));
        var source=json("/api/public/restaurants/"+restaurants[0]+"?sourceSize=1&sourcePage=1");
        assertThat(source.at("/sources/items").size()).isEqualTo(1); assertThat(source.at("/sources/items/0/timestampSeconds").asInt()).isZero();
        assertThat(source.at("/sources/hasNext").asBoolean()).isTrue();
    }
    @Test void limitAndTruncation() throws Exception {
        db.update("update mentions set publication_status='SUSPENDED'");
        for(int i=0;i<200;i++) {
            long id=place("상한"+i,126.97,37.56);
            db.update("insert into mentions(restaurant_id,chef_id,video_id,mention_type,verification_status,publication_status,reviewed_by,reviewed_at) values (?,?,?,'VISITED','APPROVED','PUBLISHED',?,now())",id,c1,videos[0],admin);
        }
        assertThat(json(AREA).get("truncated").asBoolean()).isFalse();
        long last=place("201",126.97,37.56);
        db.update("insert into mentions(restaurant_id,chef_id,video_id,mention_type,verification_status,publication_status,reviewed_by,reviewed_at) values (?,?,?,'VISITED','APPROVED','PUBLISHED',?,now())",last,c1,videos[0],admin);
        var result=json(AREA); assertThat(result.get("returnedCount").asInt()).isEqualTo(200); assertThat(result.get("truncated").asBoolean()).isTrue(); assertThat(result.get("totalCount").isNull()).isTrue();
        assertThat(ids(result)).doesNotContain(last);
    }
    @Test void invalidQueryAndSecurity() throws Exception {
        for(String path:List.of("/api/public/restaurants",AREA+"&west=126.9",AREA+"&chefId=0",AREA+"&extra=1",AREA.replace("126.96","NaN"),AREA.replace("126.99","Infinity"),"/api/public/chefs?size=101","/api/public/restaurants/1?sourcePage=-1")) mvc.perform(get(path)).andExpect(status().isBadRequest());
        mvc.perform(get(AREA.replace("126.99","127.99"))).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VIEWPORT_TOO_WIDE"));
        mvc.perform(get("/api/admin/restaurants")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/public/restaurants")).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/unknown")).andExpect(status().isUnauthorized());
    }
    @Test void oneResponseUsesReadOnlyRepeatableReadAndKeepsSnapshotAcrossConcurrentSuspension() {
        doAnswer(invocation -> {
            assertThat(db.queryForObject("show transaction_isolation",String.class)).isEqualTo("repeatable read");
            assertThat(db.queryForObject("show transaction_read_only",String.class)).isEqualTo("on");
            return invocation.callRealMethod();
        }).when(repository).detail(anyLong(),any(PublicInput.Page.class));
        assertThat(service.detail(restaurants[0],new PublicInput.Page("",0,20)).sources().totalElements()).isEqualTo(3);
        var snapshot=new TransactionTemplate(transactions);
        snapshot.setReadOnly(true); snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.executeWithoutResult(status -> {
            assertThat(service.detail(restaurants[0],new PublicInput.Page("",0,20)).sources().totalElements()).isEqualTo(3);
            var writer=new TransactionTemplate(transactions); writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            writer.executeWithoutResult(write -> db.update("update mentions set publication_status='SUSPENDED' where restaurant_id=?",restaurants[0]));
            assertThat(service.detail(restaurants[0],new PublicInput.Page("",0,20)).sources().totalElements()).isEqualTo(3);
        });
        assertThatThrownBy(()->service.detail(restaurants[0],new PublicInput.Page("",0,20))).isInstanceOfSatisfying(PublicInput.Failure.class,e->assertThat(e.status).isEqualTo(404));
    }
    @Test void measuresExactViewportQueryOnIsolatedFiveThousandRestaurantFixture() throws Exception {
        db.update("""
            insert into restaurants(kakao_place_id,name,road_address,address,longitude,latitude,place_url)
            select 'perf-'||g,'성능 검증 '||g,'도로','지번',126.97,37.56,'https://place.map.kakao.com/1'
            from generate_series(1,5000) g
            """);
        db.update("""
            insert into mentions(restaurant_id,chef_id,video_id,mention_type,verification_status,publication_status,reviewed_by,reviewed_at)
            select id,?,?,'VISITED','APPROVED','PUBLISHED',?,now() from restaurants where kakao_place_id like 'perf-%'
            """,c1,videos[0],admin);
        db.execute("analyze restaurants"); db.execute("analyze mentions");
        for(boolean filter:List.of(false,true)) {
            var plan=named.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "+PublicQueryRepository.areaSql(filter),
                Map.of("w",126.96,"s",37.55,"e",126.99,"n",37.58,"chef",c1),String.class);
            var root=mapper.readTree(plan).get(0);
            assertThat(root.at("/Plan/Actual Rows").asInt()).isEqualTo(201);
            assertThat(root.get("Execution Time").asDouble()).isGreaterThanOrEqualTo(0);
            // This is a fixture measurement, never an assertion about production latency.
            System.out.println("Public viewport isolated fixture, chefFilter="+filter+", plan="+root);
        }
    }
}
