package kr.sedanji;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CandidateSeparationMigrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }
    private Connection seeded(String schema) throws SQLException {
        flyway(schema,"1").migrate();
        Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        connection.setSchema(schema);
        try (Statement sql = connection.createStatement()) {
            sql.execute("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values ('RMTJPFKRbEk','영상','채널',now(),'원문','확인')");
            sql.execute("insert into mentions(video_id,extracted_name,extracted_address,source_line,evidence) values ('RMTJPFKRbEk','뽀요베리','서울 마포구 월드컵로13길 55-15',7,'원문')");
            sql.execute("insert into mentions(video_id,extracted_name,extracted_address,source_line,evidence,status,review_note) values ('RMTJPFKRbEk','계향각','',0,'원문','REJECTED','기존 제외 사유')");
            sql.execute("insert into place_candidates(mention_id,kakao_place_id,name,road_address,address,longitude,latitude,place_url) values (1,'123','뽀요베리','주소','지번',127,37,'http://place.map.kakao.com/123')");
        }
        return connection;
    }
    private int count(Connection connection, String query) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(query)) { result.next(); return result.getInt(1); }
    }
    @Test void pendingAndRejectedEvidenceAndSnapshotsArePreservedWithoutCreatingRelations() throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = seeded(schema)) {
            flyway(schema,null).migrate();
            assertThat(count(connection,"select count(*) from restaurant_candidates")).isEqualTo(2);
            assertThat(count(connection,"select count(*) from legacy_mention_candidates")).isEqualTo(2);
            assertThat(count(connection,"select count(*) from mentions")).isZero();
            assertThat(count(connection,"select count(*) from import_jobs")).isEqualTo(1);
            assertThat(count(connection,"select count(*) from place_candidates where restaurant_candidate_id=1 and kakao_place_id='123'")).isEqualTo(1);
            assertThat(count(connection,"select count(*) from restaurant_candidates where id=2 and source_line is null and extracted_address is null and disposition='DISMISSED' and resolution_note='기존 제외 사유'")).isEqualTo(1);
            assertThat(count(connection,"select count(*) from restaurant_candidates where id=1 and match_status='REVIEW_REQUIRED'")).isEqualTo(1);
            try (var statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute("update legacy_mention_candidates set extracted_name='수정' where id=1")).isInstanceOf(SQLException.class);
                statement.execute("insert into restaurant_candidates(import_job_id,extracted_name,evidence) values (1,'새 후보','원문')");
            }
            assertThat(count(connection,"select max(id) from restaurant_candidates")).isGreaterThan(2);
        }
    }
    @Test void existingApprovalsBlockMigrationRatherThanInferTypeOrUnpublish() throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = seeded(schema); var sql = connection.createStatement()) {
            sql.execute("insert into chefs(name) values ('셰프')");
            sql.execute("insert into restaurants(kakao_place_id,name,road_address,address,longitude,latitude,place_url) values ('123','식당','주소','지번',127,37,'http://place.map.kakao.com/123')");
            sql.execute("update mentions set status='APPROVED',chef_id=1,restaurant_id=1,review_note='기존 승인',reviewed_at=now() where id=1");
            assertThatThrownBy(() -> flyway(schema,null).migrate()).hasStackTraceContaining("Existing approvals require an explicit relation migration");
            assertThat(count(connection,"select count(*) from mentions where status='APPROVED'")).isEqualTo(1);
        }
    }
    @Test void approvedRelationMustHaveChefRestaurantTypeReviewerAndEvidence() throws Exception {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = seeded(schema); var sql = connection.createStatement()) {
            flyway(schema,null).migrate();
            sql.execute("insert into chefs(name) values ('셰프')");
            sql.execute("insert into restaurants(kakao_place_id,name,road_address,address,longitude,latitude,place_url) values ('123','식당','주소','지번',127,37,'http://place.map.kakao.com/123')");
            assertThatThrownBy(() -> sql.execute("insert into mentions(video_id,verification_status,evidence) values ('RMTJPFKRbEk','APPROVED','근거')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("insert into mentions(chef_id,restaurant_id,video_id,verification_status,evidence) values (1,1,'RMTJPFKRbEk','APPROVED','근거')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("insert into mentions(chef_id,restaurant_id,video_id,mention_type,verification_status,evidence,review_note,reviewed_at) values (1,1,'RMTJPFKRbEk','VISITED','APPROVED','근거','메모',now())")).isInstanceOf(SQLException.class);
            sql.execute("insert into admin_users(username,password_hash) values ('reviewer','test-only-hash')");
            String insert = "insert into mentions(chef_id,restaurant_id,video_id,mention_type,verification_status,evidence,review_note,reviewed_by,reviewed_at) values (1,1,'RMTJPFKRbEk','VISITED','APPROVED','근거','메모',1,now())";
            sql.execute(insert);
            assertThatThrownBy(() -> sql.execute(insert)).isInstanceOf(SQLException.class);
        }
    }
}
