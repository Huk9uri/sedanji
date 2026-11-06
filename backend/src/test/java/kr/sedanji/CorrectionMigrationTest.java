package kr.sedanji;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CorrectionMigrationTest {
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
    Flyway migration(String schema,String target){return Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();}
    int count(Connection c,String query)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getInt(1);}}
    @Test void upgradesV4WithoutInventingLegacyBaselinesAndEnforcesOpenRevisionAndAuditProtection()throws Exception{
        String schema="correction_"+UUID.randomUUID().toString().replace("-","");migration(schema,"4").migrate();
        try(var c=DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());var s=c.createStatement()){
            c.setSchema(schema);
            s.execute("insert into admin_users(username,password_hash) values ('admin','test-only')");
            s.execute("insert into chefs(name) values ('기존 이름')");
            s.execute("insert into restaurants(kakao_place_id,name,longitude,latitude,place_url) values ('123','기존 장소',127,37,'https://place.map.kakao.com/123')");
            s.execute("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values ('11111111111','원본','채널',now(),'설명','')");
            s.execute("insert into mentions(chef_id,restaurant_id,video_id,mention_type,verification_status,publication_status,evidence,reviewed_by,reviewed_at) values (1,1,'11111111111','VISITED','APPROVED','SUSPENDED','근거',1,now())");
            s.execute("insert into mention_drafts(restaurant_id,chef_id,video_id,mention_type,target_mention_id,kind,change_reason,base_version,created_by,updated_by) values (1,1,'11111111111','REGULAR',1,'CORRECTION','과거 정정 사유',0,1,1)");
            s.execute("insert into admin_audit_events(actor_id,mention_id,action,after_value) values (1,1,'PAST','{\"name\":\"과거 이름\"}')");
            migration(schema,"5").migrate();
            assertThat(count(c,"select count(*) from mention_drafts where kind='CORRECTION' and base_snapshot is null and proposed_values is null and change_reason='과거 정정 사유'")).isEqualTo(1);
            assertThat(count(c,"select count(*) from mentions where evidence='근거' and publication_status='SUSPENDED' and version=0")).isEqualTo(1);
            assertThat(count(c,"select count(*) from admin_audit_events where after_value->>'name'='과거 이름'")).isEqualTo(1);
            String insert="insert into chef_revisions(target_id,base_version,base_snapshot,proposed_values,changed_fields,reference_snapshot,change_reason,created_by,updated_by) values (1,0,'{}','{\"name\":\"새 이름\"}','[\"name\"]','{}','표기 정정',1,1)";
            s.execute(insert);assertThatThrownBy(()->s.execute(insert)).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("update chef_revisions set status='APPLIED' where id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("update admin_audit_events set action='ERASED'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("delete from admin_audit_events")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("update chef_revisions set change_reason='' where id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.execute("update chef_revisions set target_id=999 where id=1")).isInstanceOf(SQLException.class);
            assertThat(migration(schema,"5").validateWithResult().validationSuccessful).isTrue();
        }
    }
}
