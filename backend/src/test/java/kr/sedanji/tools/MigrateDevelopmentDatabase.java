package kr.sedanji.tools;

import org.flywaydb.core.Flyway;

/** Explicit local-development migration command; never starts the application or imports data. */
public final class MigrateDevelopmentDatabase {
    public static void main(String[] args) {
        String url = System.getenv().getOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:15432/sedanji");
        if (!url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("This command accepts only a local development database URL.");
        String password = System.getenv("POSTGRES_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("POSTGRES_PASSWORD is required.");
        var flyway = Flyway.configure()
            .dataSource(url, System.getenv().getOrDefault("POSTGRES_USER", "sedanji"), password)
            .locations("classpath:db/migration").cleanDisabled(true).load();
        // migrate validates applied checksums while allowing newly pending migrations.
        var result = flyway.migrate();
        flyway.validate();
        var current = flyway.info().current();
        System.out.println("Development migration complete: " + result.migrationsExecuted + " applied; schema "
            + (current == null ? "empty" : current.getVersion()));
    }
}
