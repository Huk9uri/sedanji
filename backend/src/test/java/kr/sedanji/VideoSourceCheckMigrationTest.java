package kr.sedanji;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class VideoSourceCheckMigrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())
            .schemas(schema).defaultSchema(schema).target(target).load();
    }
    String snapshot(Connection c, String table) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("select coalesce(jsonb_agg(to_jsonb(t) order by id),'[]')::text from "+table+" t")) {
            r.next(); return r.getString(1);
        }
    }
    @Test void upgradesV3WithoutChangingMetadataRelationsDraftsAuditsOrReceipts() throws Exception {
        String schema="source_"+UUID.randomUUID().toString().replace("-","");
        flyway(schema,"3").migrate();
        try(var c=postgres.createConnection("");var sql=c.createStatement()) {
            c.setSchema(schema);
            sql.execute("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values ('12345678901','title','channel',now(),'original','warning')");
            sql.execute("insert into admin_users(username,password_hash) values ('admin','test')");
            sql.execute("insert into chefs(name) values ('chef')");
            sql.execute("insert into restaurants(kakao_place_id,name,longitude,latitude,place_url) values ('1','restaurant',127,37,'url')");
            sql.execute("insert into mentions(chef_id,restaurant_id,video_id,mention_type,verification_status,publication_status,reviewed_by,reviewed_at) values (1,1,'12345678901','VISITED','APPROVED','PUBLISHED',1,now())");
            sql.execute("insert into mention_drafts(restaurant_id,chef_id,video_id,kind,created_by,updated_by) values (1,1,'12345678901','CREATE',1,1)");
            sql.execute("insert into admin_audit_events(actor_id,restaurant_id,action,after_value) values (1,1,'DRAFT_CREATED','{}')");
            sql.execute("insert into admin_request_receipts(admin_id,idempotency_key,request_path,request_hash,response_status,response_body,expires_at) values (1,'key','/test',repeat('a',64),201,'{}',now()+interval '24 hours')");
            Map<String,String> before=new HashMap<>();
            for(String table:List.of("mentions","restaurants","chefs","mention_drafts","admin_audit_events","admin_request_receipts")) before.put(table,snapshot(c,table));
            String metadata;
            try(var r=sql.executeQuery("select to_jsonb(v)::text from videos v")) { r.next(); metadata=r.getString(1); }
            flyway(schema,"4").migrate();
            for(var entry:before.entrySet()) assertThat(snapshot(c,entry.getKey())).isEqualTo(entry.getValue());
            try(var r=sql.executeQuery("select (to_jsonb(v)-'source_check_status'-'source_checked_at'-'source_check_reason')::text,source_check_status,source_checked_at,source_check_reason from videos v")) {
                r.next(); assertThat(r.getString(1)).isEqualTo(metadata); assertThat(r.getString(2)).isEqualTo("UNCHECKED");
                assertThat(r.getObject(3)).isNull(); assertThat(r.getObject(4)).isNull();
            }
            for(String combination:List.of(
                "'BAD',null,null", "null,null,null", "'UNCHECKED',now(),null", "'UNCHECKED',null,'UNAVAILABLE'",
                "'AVAILABLE',null,null", "'AVAILABLE',now(),'UNAVAILABLE'", "'REVIEW_REQUIRED',null,'UNAVAILABLE'",
                "'REVIEW_REQUIRED',now(),null", "'REVIEW_REQUIRED',now(),'BAD'")) {
                assertThatThrownBy(() -> sql.execute("update videos set (source_check_status,source_checked_at,source_check_reason)=("+combination+")"))
                    .isInstanceOf(SQLException.class);
            }
            sql.execute("update videos set source_check_status='AVAILABLE',source_checked_at=now()");
            sql.execute("update videos set source_check_status='REVIEW_REQUIRED',source_check_reason='UNAVAILABLE'");
            sql.execute("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values ('12345678902','new','channel',now(),'original','warning')");
            try(var r=sql.executeQuery("select source_check_status from videos where id='12345678902'")) { r.next(); assertThat(r.getString(1)).isEqualTo("UNCHECKED"); }
            assertThat(flyway(schema,"4").validateWithResult().validationSuccessful).isTrue();
        }
    }
    @Test void migratesEmptyDatabaseToLatest() {
        var migration=flyway("empty_"+UUID.randomUUID().toString().replace("-",""),"4");
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(migration.validateWithResult().validationSuccessful).isTrue();
    }
}
