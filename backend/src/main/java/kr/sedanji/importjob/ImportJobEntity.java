package kr.sedanji.importjob;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import kr.sedanji.video.VideoEntity;

@Entity @Table(name = "import_jobs")
public class ImportJobEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "video_id", nullable = false)
    private VideoEntity video;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "error_code", length = 100) private String errorCode;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt;
    @Column(name = "finished_at") private OffsetDateTime finishedAt;
    protected ImportJobEntity() {}
    public ImportJobEntity(VideoEntity video, OffsetDateTime startedAt) {
        this.video = video; this.startedAt = startedAt; status = "SUCCEEDED"; finishedAt = OffsetDateTime.now();
    }
    public Long getId() { return id; }
    public VideoEntity getVideo() { return video; }
}
