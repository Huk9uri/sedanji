package kr.sedanji;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Explicit, separately authorized real-connection check; never used by normal E2E. */
public final class ExternalConnectionLauncher {
    static final AtomicInteger placeSearches = new AtomicInteger(), videoLookups = new AtomicInteger();
    @TestConfiguration(proxyBeanMethods = false)
    static class Guards {
        @Bean @Primary ExternalApis approvedExternal(com.fasterxml.jackson.databind.ObjectMapper mapper) {
            return new ExternalApis(required("YOUTUBE_API_KEY"), required("KAKAO_REST_API_KEY"), mapper) {
                @Override Models.PlaceSearch places(String query) {
                    if (!query.equals("모리본") || placeSearches.incrementAndGet() > 1)
                        throw new IllegalArgumentException("Approved search budget exceeded");
                    return super.places(query);
                }
                @Override public Models.Video video(String id) {
                    if (!id.equals("RMTJPFKRbEk") || videoLookups.incrementAndGet() > 1)
                        throw new IllegalArgumentException("Approved video budget exceeded");
                    return super.video(id);
                }
            };
        }
    }
    static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Required check credential is missing");
        return value;
    }
    public static void main(String[] args) throws Exception {
        var directory = Path.of(required("SEDANJI_E2E_DIR"));
        var password = UUID.randomUUID() + "-external-check";
        var capability = UUID.randomUUID().toString();
        var postgres = new PostgreSQLContainer("postgres:17-alpine").withReuse(false)
            .withImagePullPolicy(image -> false).withLabel("sedanji.external", directory.getFileName().toString());
        try (postgres; var socket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))) {
            postgres.start();
            try (var app = new SpringApplicationBuilder(SedanjiApplication.class, Guards.class).run(
                "--server.address=127.0.0.1", "--server.port=18090", "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(), "--spring.datasource.password=" + postgres.getPassword(),
                "--sedanji.admin.username=external-check", "--sedanji.admin.password=" + password,
                "--sedanji.youtube.key=", "--sedanji.kakao.key=", "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=true", "--logging.level.root=ERROR")) {
                var db = app.getBean(JdbcTemplate.class);
                E2eLauncher.JSON.writeValue(directory.resolve("ready.tmp").toFile(), Map.of("password", password,
                    "capability", capability, "probePort", socket.getLocalPort()));
                Files.move(directory.resolve("ready.tmp"), directory.resolve("ready.json"), StandardCopyOption.ATOMIC_MOVE);
                boolean running = true;
                while (running) try (var client = socket.accept()) {
                    client.setSoTimeout(5000);
                    var request = E2eLauncher.JSON.readTree(new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)).readLine());
                    Object response;
                    if (!capability.equals(request.path("capability").asText())) response = Map.of("error", "unauthorized");
                    else if (request.path("operation").asText().equals("stop")) { running = false; response = Map.of("stopped", true); }
                    else response = Map.of("placeSearches", placeSearches.get(), "videoLookups", videoLookups.get(),
                        "videos", db.queryForList("select id,title,channel_title from videos order by id"),
                        "snapshot", E2eLauncher.snapshot(db));
                    client.getOutputStream().write((E2eLauncher.JSON.writeValueAsString(response) + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        } finally { Files.deleteIfExists(directory.resolve("ready.json")); }
    }
}
