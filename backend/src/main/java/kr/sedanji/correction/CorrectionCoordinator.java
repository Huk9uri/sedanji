package kr.sedanji.correction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/** One transaction-scoped namespace for relation creation and shared corrections. */
@Component
public class CorrectionCoordinator {
    private final JdbcTemplate db;
    public CorrectionCoordinator(JdbcTemplate db) { this.db = db; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void lock() { db.query("select pg_advisory_xact_lock(190019)", rs -> { return null; }); }
}
