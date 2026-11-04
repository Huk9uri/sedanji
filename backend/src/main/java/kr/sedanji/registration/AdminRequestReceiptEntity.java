package kr.sedanji.registration;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="admin_request_receipts")
public class AdminRequestReceiptEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="admin_id", nullable=false) private long adminId;
    @Column(name="idempotency_key", nullable=false, length=128) private String idempotencyKey;
    @Column(name="request_path", nullable=false, length=200) private String requestPath;
    @Column(name="request_hash", nullable=false, length=64) private String requestHash;
    @Column(name="response_status", nullable=false) private int responseStatus;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="response_body", nullable=false, columnDefinition="jsonb") private String responseBody;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="expires_at", nullable=false) private OffsetDateTime expiresAt;
    protected AdminRequestReceiptEntity() {}
    public AdminRequestReceiptEntity(long admin, String key, String hash, String body, OffsetDateTime now) {
        adminId = admin; idempotencyKey = key; requestPath = "/api/admin/mentions";
        requestHash = hash; responseStatus = 201; responseBody = body; createdAt = now; expiresAt = now.plusHours(24);
    }
    public boolean active(OffsetDateTime now) { return expiresAt.isAfter(now); }
    public boolean matches(String hash) { return requestHash.equals(hash) && requestPath.equals("/api/admin/mentions"); }
    public String body() { return responseBody; }
}
