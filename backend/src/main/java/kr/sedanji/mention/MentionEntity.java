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
    @Column(columnDefinition = "text") private String evidence;
    @Column(name = "publication_status", nullable = false, length = 20) private String publicationStatus;
    @Version private long version;
    @Column(name = "review_note", columnDefinition = "text") private String reviewNote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private AdminUserEntity reviewer;
    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;
    protected MentionEntity() {}
    public MentionEntity(VideoEntity video, RestaurantCandidateEntity candidate, ChefEntity chef, RestaurantEntity restaurant,
                         MentionType type, Integer timestamp, String evidence, String note, AdminUserEntity reviewer) {
        if (type == null || evidence == null || evidence.isBlank()
            || (timestamp != null && timestamp < 0)) throw new IllegalArgumentException("언급 유형과 확인 근거를 입력하세요.");
        if (video == null) throw new IllegalArgumentException("출처 영상을 선택하세요.");
        if (candidate != null && !candidate.getImportJob().getVideo().getId().equals(video.getId()))
            throw new IllegalArgumentException("후보의 출처 영상과 승인할 영상이 다릅니다.");
        this.candidate = candidate; this.video = video; this.chef = chef; this.restaurant = restaurant;
        mentionType = type; timestampSeconds = timestamp; this.evidence = evidence.strip();
        reviewNote = note == null || note.isBlank() ? null : note.strip();
        this.reviewer = reviewer; reviewedAt = OffsetDateTime.now(); verificationStatus = "APPROVED"; publicationStatus = "PUBLISHED";
    }
    public Mention toDto() {
        return new Mention(id, candidate == null ? null : candidate.getId(), video.getId(), video.getTitle(), chef.getId(), chef.getName(),
            restaurant.getId(), restaurant.getName(), mentionType, timestampSeconds, verificationStatus,
            evidence, reviewNote, reviewer == null ? null : reviewer.getUsername(), reviewedAt);
    }
    public Source toSource() { return new Source(id, chef.getName(), video.getId(), video.getTitle(), mentionType, timestampSeconds); }
    public static MentionEntity direct(VideoEntity video, ChefEntity chef, RestaurantEntity restaurant,
                                        MentionType type, Integer timestamp, String note, AdminUserEntity reviewer) {
        if (video == null || chef == null || restaurant == null || type == null || reviewer == null || !reviewer.isEnabled()
            || (timestamp != null && timestamp < 0) || (note != null && note.length() > 2000))
            throw new IllegalArgumentException("직접 등록 필수 항목을 확인하세요.");
        var mention = new MentionEntity();
        mention.video = video; mention.chef = chef; mention.restaurant = restaurant;
        mention.mentionType = type; mention.timestampSeconds = timestamp;
        mention.reviewNote = note == null || note.isBlank() ? null : note.strip();
        mention.reviewer = reviewer; mention.reviewedAt = OffsetDateTime.now();
        mention.verificationStatus = "APPROVED"; mention.publicationStatus = "PUBLISHED";
        return mention;
    }
    public Long getId() { return id; }
    public long getVersion() { return version; }
    public void suspend() {
        if (!"APPROVED".equals(verificationStatus) || !"PUBLISHED".equals(publicationStatus))
            throw new IllegalArgumentException("공개된 승인 관계만 중단할 수 있습니다.");
        publicationStatus = "SUSPENDED";
    }
    public void restore(AdminUserEntity actor) {
        if (!"APPROVED".equals(verificationStatus) || !"SUSPENDED".equals(publicationStatus)
            || actor == null || !actor.isEnabled())
            throw new IllegalArgumentException("중단된 승인 관계만 재승인할 수 있습니다.");
        publicationStatus = "PUBLISHED";
        reviewer = actor; reviewedAt = OffsetDateTime.now();
    }
    public void correct(ChefEntity nextChef, RestaurantEntity nextRestaurant, VideoEntity nextVideo,
                        MentionType type, Integer timestamp, String note, AdminUserEntity actor) {
        if (!"APPROVED".equals(verificationStatus) || nextChef == null || nextRestaurant == null || nextVideo == null
            || type == null || actor == null || !actor.isEnabled() || (timestamp != null && timestamp < 0)
            || (note != null && note.length() > 2000)) throw new IllegalArgumentException("정정 내용을 확인하세요.");
        chef=nextChef; restaurant=nextRestaurant; video=nextVideo; mentionType=type;
        timestampSeconds=timestamp; reviewNote=note; reviewer=actor; reviewedAt=OffsetDateTime.now();
        // candidate, evidence, verification and publication states are deliberately preserved.
    }
}
