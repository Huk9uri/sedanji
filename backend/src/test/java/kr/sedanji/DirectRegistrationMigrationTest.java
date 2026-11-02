package kr.sedanji;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class DirectRegistrationMigrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(target).load();
    }
    private String schema() { return "direct_" + UUID.randomUUID().toString().replace("-", ""); }
    private Connection seed(String schema) throws SQLException {
        flyway(schema, "2").migrate();
        var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setSchema(schema);
        try (var sql = connection.createStatement()) {
            sql.execute("insert into chefs(name) values ('동명셰프')");
            sql.execute("insert into admin_users(username,password_hash) values ('admin','test-only-hash')");
            sql.execute("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values ('RMTJPFKRbEk','영상','채널',now(),'원본 설명','검토')");
            sql.execute("insert into restaurants(kakao_place_id,name,road_address,address,longitude,latitude,place_url) values ('123','식당','도로명','지번',127,37,'https://place.map.kakao.com/123')");
        }
        return connection;
    }
    private int count(Connection c, String query) throws SQLException {
        try (var sql = c.createStatement(); var rows = sql.executeQuery(query)) { rows.next(); return rows.getInt(1); }
    }
    private String relation(String type, String status) {
        return "insert into mentions(chef_id,restaurant_id,video_id,mention_type,verification_status,evidence,review_note,reviewed_by,reviewed_at) values (1,1,'RMTJPFKRbEk','" + type + "','" + status + "','기존 근거','기존 메모',1,now())";
    }
    @Test void preservesExistingEvidenceAndPublicStateWithoutApprovingPendingData() throws Exception {
        String schema = schema();
        try (var c = seed(schema); var sql = c.createStatement()) {
            sql.execute(relation("VISITED", "APPROVED"));
            sql.execute("insert into chefs(name) values ('대기셰프'),('제외셰프')");
            sql.execute("insert into mentions(chef_id,restaurant_id,video_id,verification_status,evidence) values (2,1,'RMTJPFKRbEk','PENDING','대기 근거'),(3,1,'RMTJPFKRbEk','REJECTED','제외 근거')");
            flyway(schema, "3").migrate();
            assertThat(count(c, "select count(*) from mentions where id=1 and publication_status='PUBLISHED' and evidence='기존 근거' and review_note='기존 메모' and reviewed_by=1 and reviewed_at is not null")).isEqualTo(1);
            assertThat(count(c, "select count(*) from mentions where verification_status in ('PENDING','REJECTED') and publication_status='SUSPENDED'")).isEqualTo(2);
            assertThat(count(c, "select count(*) from videos where description='원본 설명'")).isEqualTo(1);
            assertThat(count(c, "select count(*) from admin_audit_events")).isZero();
            assertThat(flyway(schema, "3").validateWithResult().validationSuccessful).isTrue();
        }
    }
    @Test void duplicateAcrossTypesBlocksMigrationWithoutChangingOriginalSchemaOrRows() throws Exception {
        String schema = schema();
        try (var c = seed(schema); var sql = c.createStatement()) {
            sql.execute(relation("VISITED", "APPROVED"));
            sql.execute(relation("RECOMMENDED", "APPROVED"));
            assertThatThrownBy(() -> flyway(schema, "3").migrate()).hasStackTraceContaining("V3 blocked: duplicate");
            assertThat(count(c, "select count(*) from mentions")).isEqualTo(2);
            assertThat(count(c, "select count(*) from information_schema.columns where table_schema='" + schema + "' and table_name='mentions' and column_name='publication_status'")).isZero();
            assertThatThrownBy(() -> sql.execute("insert into chefs(name) values ('동명셰프')")).isInstanceOf(SQLException.class);
        }
    }
    @Test void allowsHomonymsAndOptionalNoteButRejectsIncompleteApprovalAndDuplicateTypes() throws Exception {
        String schema = schema();
        try (var c = seed(schema); var sql = c.createStatement()) {
            flyway(schema, "3").migrate();
            sql.execute("insert into chefs(name) values ('동명셰프')");
            sql.execute("insert into mentions(chef_id,restaurant_id,video_id,mention_type,verification_status,publication_status,reviewed_by,reviewed_at) values (1,1,'RMTJPFKRbEk','VISITED','APPROVED','PUBLISHED',1,now())");
            assertThat(count(c, "select count(*) from mentions where evidence is null and review_note is null")).isEqualTo(1);
            assertThatThrownBy(() -> sql.execute(relation("RECOMMENDED", "APPROVED"))).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("insert into mentions(chef_id,restaurant_id,video_id,verification_status) values (2,1,'RMTJPFKRbEk','APPROVED')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("insert into mentions(chef_id,restaurant_id,video_id,verification_status,publication_status) values (2,1,'RMTJPFKRbEk','PENDING','PUBLISHED')")).isInstanceOf(SQLException.class);
        }
    }
    @Test void restaurantOnlyDraftDoesNotCreateRelationshipOrPublicData() throws Exception {
        String schema = schema();
        try (var c = seed(schema); var sql = c.createStatement()) {
            flyway(schema, "3").migrate();
            sql.execute("insert into mention_drafts(restaurant_id,kind,created_by,updated_by) values (1,'CREATE',1,1)");
            assertThat(count(c, "select count(*) from mention_drafts where chef_id is null and video_id is null and mention_type is null and status='DRAFT'")).isEqualTo(1);
            assertThat(count(c, "select count(*) from mentions")).isZero();
            assertThatThrownBy(() -> sql.execute("update mention_drafts set status='APPLIED' where id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("insert into mention_drafts(kind,created_by,updated_by) values ('CREATE',1,1)")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("delete from restaurants where id=1")).isInstanceOf(SQLException.class);
        }
    }
    @Test void correctionRequiresTargetReasonAndBaseVersionAndHasOnlyOneOpenDraft() throws Exception {
        String schema = schema();
        try (var c = seed(schema); var sql = c.createStatement()) {
            flyway(schema, "3").migrate();
            sql.execute(relation("VISITED", "APPROVED"));
            assertThatThrownBy(() -> sql.execute("insert into mention_drafts(restaurant_id,kind,created_by,updated_by) values (1,'CORRECTION',1,1)")).isInstanceOf(SQLException.class);
            String insert = "insert into mention_drafts(restaurant_id,kind,target_mention_id,base_version,change_reason,created_by,updated_by) values (1,'CORRECTION',1,0,'정정 사유',1,1)";
            sql.execute(insert);
            assertThatThrownBy(() -> sql.execute(insert)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("update mention_drafts set timestamp_seconds=-1")).isInstanceOf(SQLException.class);
        }
    }
    @Test void auditIsAppendOnlyAndReceiptsAreUniquePerAdministrator() throws Exception {
        String schema = schema();
        try (var c = seed(schema); var sql = c.createStatement()) {
            flyway(schema, "3").migrate();
            sql.execute("insert into admin_audit_events(actor_id,restaurant_id,action,after_value) values (1,1,'DRAFT_CREATED','{}')");
            assertThatThrownBy(() -> sql.execute("update admin_audit_events set action='OTHER'")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("delete from admin_audit_events")).isInstanceOf(SQLException.class);
            String insert = "insert into admin_request_receipts(admin_id,idempotency_key,request_path,request_hash,response_status,response_body,expires_at) values (1,'request-key','/api/admin/mentions',repeat('a',64),201,'{}',now()+interval '24 hours')";
            sql.execute(insert);
            assertThatThrownBy(() -> sql.execute(insert)).isInstanceOf(SQLException.class);
        }
    }
}
