package kr.sedanji;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.mockito.Mockito.*;

/** Test runtime only. No production endpoints, database URLs, or real external keys. */
public final class E2eLauncher {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final Map<String, HttpSession> SESSIONS = new ConcurrentHashMap<>();
    static String password;

    @TestConfiguration(proxyBeanMethods = false)
    static class Doubles {
        @Bean @Primary ExternalApis e2eExternal() {
            var external = mock(ExternalApis.class);
            when(external.places(anyString())).thenAnswer(call -> {
                String query = call.getArgument(0);
                if (query.equals("B3-updated")) return new Models.PlaceSearch(List.of(
                    new Models.Place("21003", "B3 공식 변경", null, null, 128, 38,
                        "https://place.map.kakao.com/21003")), 1, true);
                var ids = Map.of("A1", "21001", "A3", "21002", "B3", "21003", "CONFLICT", "21004", "SECURITY", "21005");
                String id = ids.get(query);
                if (id == null) return new Models.PlaceSearch(List.of(), 0, true);
                return new Models.PlaceSearch(List.of(new Models.Place(id, query + " 식당", "테스트 도로", "테스트 주소", 127, 37,
                    "https://place.map.kakao.com/" + id)), 1, true);
            });
            when(external.video(anyString())).thenAnswer(call -> new Models.Video(call.getArgument(0),
                "E2E 영상 " + call.getArgument(0), "격리 채널", OffsetDateTime.parse("2026-01-01T00:00:00Z"), "보존 근거", "테스트 대역"));
            return external;
        }
        @Bean ServletListenerRegistrationBean<HttpSessionListener> e2eSessions() {
            return new ServletListenerRegistrationBean<>(new HttpSessionListener() {
                @Override public void sessionCreated(HttpSessionEvent e) { SESSIONS.put(e.getSession().getId(), e.getSession()); }
                @Override public void sessionDestroyed(HttpSessionEvent e) { SESSIONS.remove(e.getSession().getId()); }
            });
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(Objects.requireNonNull(System.getenv("SEDANJI_E2E_DIR")));
        password = UUID.randomUUID() + "-e2e";
        String capability = UUID.randomUUID().toString();
        var postgres = new PostgreSQLContainer("postgres:17-alpine").withReuse(false)
            .withImagePullPolicy(image -> false).withLabel("sedanji.e2e", directory.getFileName().toString());
        try (postgres; var socket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))) {
            postgres.start();
            // Command-line properties have priority over every inherited property source.
            try (var app = new SpringApplicationBuilder(SedanjiApplication.class, Doubles.class).run(
                "--server.address=127.0.0.1", "--server.port=18090",
                "--spring.datasource.url=" + postgres.getJdbcUrl(), "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(), "--sedanji.admin.username=e2e-admin",
                "--sedanji.admin.password=" + password, "--sedanji.youtube.key=", "--sedanji.kakao.key=",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true", "--logging.level.root=ERROR")) {
                var db = app.getBean(JdbcTemplate.class);
                db.update("insert into admin_users(username,password_hash) values (?,?)", "e2e-other", "{bcrypt}" + new BCryptPasswordEncoder().encode(password));
                seed(db);
                JSON.writeValue(directory.resolve("ready.tmp").toFile(), Map.of("probePort", socket.getLocalPort(),
                    "capability", capability, "password", password, "containerId", postgres.getContainerId()));
                Files.move(directory.resolve("ready.tmp"), directory.resolve("ready.json"), StandardCopyOption.ATOMIC_MOVE);
                boolean running = true;
                while (running) try (var client = socket.accept()) {
                    client.setSoTimeout(5000);
                    var request = JSON.readTree(new BufferedReader(new InputStreamReader(client.getInputStream())).readLine());
                    Object response;
                    if (!capability.equals(request.path("capability").asText())) response = Map.of("error", "unauthorized");
                    else if (request.path("operation").asText().equals("stop")) { response = Map.of("stopped", true); running = false; }
                    else if (request.path("operation").asText().equals("expire")) {
                        for (var session : List.copyOf(SESSIONS.values())) try { session.invalidate(); } catch (IllegalStateException ignored) { }
                        response = Map.of("expired", true);
                    } else response = snapshot(db);
                    client.getOutputStream().write((JSON.writeValueAsString(response) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } finally { Files.deleteIfExists(directory.resolve("ready.json")); }
    }

    static void seed(JdbcTemplate db) {
        for (String name : List.of("A 셰프", "B1 셰프", "B2 공유 셰프", "무관 셰프", "B3 셰프", "C 셰프")) db.update("insert into chefs(name) values (?)", name);
        for (int i = 1; i <= 6; i++) db.update("insert into videos(id,title,channel_title,published_at,description,extraction_warning) values (?,?,'격리 채널',now(),'보존 근거','테스트')", "E2EVIDEO00" + i, "E2E 영상 " + i);
        long actor = db.queryForObject("select id from admin_users where username='e2e-admin'", Long.class);
        for (String scenario : List.of("B1", "B2", "B2-other", "B2-unrelated", "B3", "C", "C-other")) {
            String place = scenario.equals("B3") ? "21003" : Integer.toString(22000 + List.of("B1", "B2", "B2-other", "B2-unrelated", "B3", "C", "C-other").indexOf(scenario));
            long restaurant = db.queryForObject("insert into restaurants(kakao_place_id,name,road_address,address,longitude,latitude,place_url) values (?,?,'기존 도로','기존 지번',127,37,?) returning id", Long.class, place, scenario + " 식당", "https://place.map.kakao.com/" + place);
            String chef = scenario.startsWith("B2") ? (scenario.equals("B2-unrelated") ? "무관 셰프" : "B2 공유 셰프") : scenario.startsWith("C") ? "C 셰프" : scenario + " 셰프";
            long chefId = db.queryForObject("select id from chefs where name=?", Long.class, chef);
            for (int i = 1; i <= (scenario.equals("C") ? 2 : 1); i++) {
                db.update("insert into mentions(restaurant_id,chef_id,video_id,mention_type,verification_status,publication_status,evidence,review_note,timestamp_seconds,reviewed_by,reviewed_at) values (?,?,?,'VISITED','APPROVED','PUBLISHED','보존 근거','내부 메모',0,?,now())", restaurant, chefId, "E2EVIDEO00" + i, actor);
            }
        }
        long job = db.queryForObject("insert into import_jobs(video_id,status,started_at) values ('E2EVIDEO001','SUCCEEDED',now()) returning id", Long.class);
        long candidate = db.queryForObject("insert into restaurant_candidates(import_job_id,extracted_name,evidence,disposition) values (?,'B1 후보','보존 근거','RESOLVED') returning id", Long.class, job);
        db.update("update mentions set restaurant_candidate_id=? where restaurant_id=(select id from restaurants where name='B1 식당')", candidate);
    }

    static Map<String, Object> snapshot(JdbcTemplate db) {
        // Explicit safe columns only: never passwords, sessions, CSRF, selection/receipt keys.
        return Map.of("restaurants", db.queryForList("select id,kakao_place_id,name,road_address,address,longitude,latitude,place_url,version from restaurants order by id"),
            "chefs", db.queryForList("select id,name,version from chefs order by id"),
            "mentions", db.queryForList("select id,restaurant_id,chef_id,video_id,mention_type,timestamp_seconds,evidence,restaurant_candidate_id,review_note,verification_status,publication_status,version from mentions order by id"),
            "drafts", db.queryForList("select id,restaurant_id,chef_id,video_id,mention_type,timestamp_seconds,verification_note,status,kind,version,applied_mention_id from mention_drafts order by id"),
            "audits", db.queryForList("select id,action,mention_id,draft_id,restaurant_id,chef_id,reason from admin_audit_events order by id"),
            "receipts", db.queryForList("select id,response_status from admin_request_receipts order by id"),
            "candidates", db.queryForList("select id,evidence,disposition from restaurant_candidates order by id"));
    }
}
