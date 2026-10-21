package kr.sedanji.video;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import kr.sedanji.Models.Video;

@Entity
@Table(name = "videos")
public class VideoEntity {
    @Id @Column(length = 11)
    private String id;
    @Column(nullable = false, columnDefinition = "text")
    private String title;
    @Column(name = "channel_title", nullable = false, columnDefinition = "text")
    private String channelTitle;
    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @Column(name = "extraction_warning", nullable = false, columnDefinition = "text")
    private String extractionWarning;
    @Column(name = "imported_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime importedAt;

    protected VideoEntity() {}
    public VideoEntity(Video video) {
        id = video.id(); title = video.title(); channelTitle = video.channelTitle();
        publishedAt = video.publishedAt(); description = video.description(); extractionWarning = video.extractionWarning();
    }
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Video toDto() { return new Video(id, title, channelTitle, publishedAt, description, extractionWarning); }
}
