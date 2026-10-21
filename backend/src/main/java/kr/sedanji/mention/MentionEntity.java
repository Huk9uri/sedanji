package kr.sedanji.mention;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import kr.sedanji.Models.*;
import kr.sedanji.admin.AdminUserEntity;
import kr.sedanji.chef.ChefEntity;
import kr.sedanji.restaurant.RestaurantEntity;
import kr.sedanji.restaurantcandidate.RestaurantCandidateEntity;
import kr.sedanji.video.VideoEntity;

/** A verified chef/restaurant/video relationship, never an extraction placeholder. */
@Entity @Table(name = "mentions")
public class MentionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "video_id", nullable = false) private VideoEntity video;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "chef_id", nullable = false) private ChefEntity chef;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "restaurant_id", nullable = false) private RestaurantEntity restaurant;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "restaurant_candidate_id") private RestaurantCandidateEntity candidate;
    @Enumerated(EnumType.STRING) @Column(name = "mention_type", length = 20) private MentionType mentionType;
    @Column(name = "timestamp_seconds") private Integer timestampSeconds;
    @Column(name = "verification_status", nullable = false, length = 20) private String verificationStatus;
    @Column(nullable = false, columnDefinition = "text") private String evidence;
    @Column(name = "review_note", columnDefinition = "text") private String reviewNote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private AdminUserEntity reviewer;
    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;
    protected MentionEntity() {}
    public MentionEntity(RestaurantCandidateEntity candidate, ChefEntity chef, RestaurantEntity restaurant,
                         MentionType type, Integer timestamp, String evidence, String note, AdminUserEntity reviewer) {
        if (type == null || evidence == null || evidence.isBlank() || note == null || note.isBlank()
            || (timestamp != null && timestamp < 0)) throw new IllegalArgumentException("언급 유형과 확인 근거를 입력하세요.");
        this.candidate = candidate; video = candidate.getImportJob().getVideo(); this.chef = chef; this.restaurant = restaurant;
        mentionType = type; timestampSeconds = timestamp; this.evidence = evidence.strip(); reviewNote = note.strip();
        this.reviewer = reviewer; reviewedAt = OffsetDateTime.now(); verificationStatus = "APPROVED";
    }
    public Mention toDto() {
        return new Mention(id, candidate == null ? null : candidate.getId(), video.getId(), video.getTitle(), chef.getId(), chef.getName(),
            restaurant.getId(), restaurant.getName(), mentionType, timestampSeconds, verificationStatus,
            evidence, reviewNote, reviewer == null ? null : reviewer.getUsername(), reviewedAt);
    }
    public Source toSource() { return new Source(id, chef.getName(), video.getId(), video.getTitle(), mentionType, timestampSeconds); }
}
