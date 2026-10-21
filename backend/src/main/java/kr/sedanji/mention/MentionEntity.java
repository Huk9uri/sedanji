package kr.sedanji.mention;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import kr.sedanji.Models.*;
import kr.sedanji.chef.ChefEntity;
import kr.sedanji.restaurant.RestaurantEntity;
import kr.sedanji.video.VideoEntity;

/** Current V1 mapping. Candidate/relationship schema separation belongs to the next migration. */
@Entity
@Table(name = "mentions")
public class MentionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "video_id", nullable = false)
    private VideoEntity video;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "chef_id")
    private ChefEntity chef;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "restaurant_id")
    private RestaurantEntity restaurant;
    @Column(name = "extracted_name", nullable = false, columnDefinition = "text")
    private String extractedName;
    @Column(name = "extracted_address", nullable = false, columnDefinition = "text")
    private String extractedAddress;
    @Column(name = "source_line", nullable = false)
    private int sourceLine;
    @Column(nullable = false, columnDefinition = "text")
    private String evidence;
    @Column(nullable = false, length = 20)
    private String status = "PENDING";
    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;
    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;
    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    protected MentionEntity() {}
    public MentionEntity(VideoEntity video, Extracted candidate) {
        this.video = video; extractedName = candidate.name(); extractedAddress = candidate.address();
        sourceLine = candidate.sourceLine(); evidence = candidate.evidence();
    }
    public Long getId() { return id; }
    public String getStatus() { return status; }
    public String getExtractedName() { return extractedName; }
    public void approve(ChefEntity chef, RestaurantEntity restaurant, String note, String reviewer) {
        this.chef = chef; this.restaurant = restaurant; review("APPROVED", note, reviewer);
    }
    public void reject(String note, String reviewer) { review("REJECTED", note, reviewer); }
    private void review(String status, String note, String reviewer) {
        if (!this.status.equals("PENDING")) throw new IllegalStateException("이미 검토된 후보입니다.");
        this.status = status; reviewNote = note.strip(); reviewedBy = reviewer; reviewedAt = OffsetDateTime.now();
    }
    public Mention toDto() {
        return new Mention(id, video.getId(), video.getTitle(), extractedName, extractedAddress, sourceLine, evidence, status,
            chef == null ? null : chef.getId(), restaurant == null ? null : restaurant.getId(), reviewNote);
    }
    public Source toSource() { return new Source(id, chef.getName(), video.getId(), video.getTitle()); }
}
