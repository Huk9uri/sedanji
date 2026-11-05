package kr.sedanji.mentiondraft;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import kr.sedanji.mention.MentionType;

@Entity @Table(name="mention_drafts")
public class MentionDraftEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="restaurant_id", nullable=false) private long restaurantId;
    @Column(name="chef_id") private Long chefId;
    @Column(name="video_id", length=11) private String videoId;
    @Column(name="candidate_id") private Long candidateId;
    @Column(name="target_mention_id") private Long targetMentionId;
    @Column(name="applied_mention_id") private Long appliedMentionId;
    @Column(nullable=false, length=20) private String kind;
    @Column(nullable=false, length=20) private String status;
    @Enumerated(EnumType.STRING) @Column(name="mention_type", length=20) private MentionType mentionType;
    @Column(name="timestamp_seconds") private Integer timestampSeconds;
    @Column(name="verification_note", columnDefinition="text") private String verificationNote;
    @Column(name="change_reason", columnDefinition="text") private String changeReason;
    @Column(name="base_version") private Long baseVersion;
    @Version private long version;
    @Column(name="created_by", nullable=false) private long createdBy;
    @Column(name="updated_by", nullable=false) private long updatedBy;
    @Column(name="created_at", nullable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
    protected MentionDraftEntity() {}
    public MentionDraftEntity(long restaurant, MentionDraftInput.Create request, long actor) {
        restaurantId = restaurant; kind = "CREATE"; status = "DRAFT";
        var v = request.values(); chefId = v.chefId(); videoId = v.videoId(); mentionType = v.mentionType();
        timestampSeconds = v.timestampSeconds(); verificationNote = v.verificationNote();
        createdBy = actor; updatedBy = actor;
        createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS); updatedAt = createdAt;
    }
    public void patch(MentionDraftInput.Patch request, long restaurant, long actor) {
        var v = request.values(); restaurantId = restaurant;
        if (request.has("chefId")) chefId = v.chefId();
        if (request.has("videoId")) videoId = v.videoId();
        if (request.has("mentionType")) mentionType = v.mentionType();
        if (request.has("timestampSeconds")) timestampSeconds = v.timestampSeconds();
        if (request.has("verificationNote")) verificationNote = v.verificationNote();
        updatedBy = actor;
        // Always make a valid PATCH dirty, even when every submitted value is unchanged.
        var now = OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }
    public long getId() { return id; }
    public Long getChefId() { return chefId; }
    public String getVideoId() { return videoId; }
    public MentionType getMentionType() { return mentionType; }
    public Integer getTimestampSeconds() { return timestampSeconds; }
    public String getVerificationNote() { return verificationNote; }
    public Long getAppliedMentionId() { return appliedMentionId; }
    public void apply(long mentionId, long actor) {
        if (!isEditable() || chefId == null || videoId == null || mentionType == null || mentionId < 1)
            throw new IllegalStateException("승인할 수 없는 초안입니다.");
        appliedMentionId = mentionId; status = "APPLIED"; updatedBy = actor;
        var now = OffsetDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }
    public long getRestaurantId() { return restaurantId; }
    public long getVersion() { return version; }
    public boolean isCreate() { return "CREATE".equals(kind); }
    public boolean isEditable() { return isCreate() && "DRAFT".equals(status); }
    public MentionDraftResult toDto(MentionDraftResult.Restaurant restaurant) {
        return new MentionDraftResult(id, kind, status, version, restaurant, chefId, videoId, mentionType,
            timestampSeconds, verificationNote, candidateId, appliedMentionId, createdBy, updatedBy, createdAt, updatedAt);
    }
}
