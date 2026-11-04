package kr.sedanji.registration;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Table(name="admin_audit_events")
public class AdminAuditEventEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="actor_id", nullable=false) private long actorId;
    @Column(name="mention_id") private Long mentionId;
    @Column(name="restaurant_id") private Long restaurantId;
    @Column(nullable=false, length=50) private String action;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="after_value", columnDefinition="jsonb") private String afterValue;
    @Column(name="occurred_at", nullable=false) private OffsetDateTime occurredAt;
    protected AdminAuditEventEntity() {}
    public AdminAuditEventEntity(long actor, DirectRegistrationResult result, String snapshot) {
        actorId = actor; mentionId = result.mentionId(); restaurantId = result.restaurantId();
        action = "APPROVE_DIRECT"; afterValue = snapshot; occurredAt = OffsetDateTime.now();
    }
}
