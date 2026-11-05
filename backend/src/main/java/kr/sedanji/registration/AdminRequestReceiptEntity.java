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
        this(admin, key, "/api/admin/mentions", hash, 201, body, now);
    }
    public AdminRequestReceiptEntity(long admin, String key, String path, String hash, int status, String body, OffsetDateTime now) {
        adminId = admin; idempotencyKey = key; requestPath = path;
        requestHash = hash; responseStatus = status; responseBody = body; createdAt = now; expiresAt = now.plusHours(24);
    }
    public boolean active(OffsetDateTime now) { return expiresAt.isAfter(now); }
    public boolean matches(String hash) { return requestHash.equals(hash) && requestPath.equals("/api/admin/mentions"); }
    public boolean matches(String path, String hash, int status) {
        return requestPath.equals(path) && requestHash.equals(hash) && responseStatus == status;
    }
    public String body() { return responseBody; }
}
