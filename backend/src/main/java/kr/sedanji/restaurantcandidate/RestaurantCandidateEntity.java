package kr.sedanji.restaurantcandidate;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import kr.sedanji.Models.*;
import kr.sedanji.importjob.ImportJobEntity;

@Entity @Table(name = "restaurant_candidates")
public class RestaurantCandidateEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "import_job_id", nullable = false)
    private ImportJobEntity importJob;
    @Column(name = "extracted_name", nullable = false, columnDefinition = "text") private String extractedName;
    @Column(name = "extracted_address", columnDefinition = "text") private String extractedAddress;
    @Column(name = "source_line") private Integer sourceLine;
    @Column(nullable = false, columnDefinition = "text") private String evidence;
    @Column(name = "match_status", length = 20) private String matchStatus;
    @Column(name = "matching_error_code", length = 100) private String matchingErrorCode;
    @Column(name = "matched_at") private OffsetDateTime matchedAt;
    @Column(name = "search_total_count") private Integer searchTotalCount;
    @Column(name = "search_complete") private Boolean searchComplete;
    @Column(nullable = false, length = 20) private String disposition = "OPEN";
    @Column(name = "resolution_note", columnDefinition = "text") private String resolutionNote;
    protected RestaurantCandidateEntity() {}
    public RestaurantCandidateEntity(ImportJobEntity job, Extracted candidate) {
        importJob = job; extractedName = candidate.name().strip();
        extractedAddress = candidate.address() == null || candidate.address().isBlank() ? null : candidate.address().strip();
        sourceLine = candidate.sourceLine() > 0 ? candidate.sourceLine() : null; evidence = candidate.evidence();
    }
    public Long getId() { return id; }
    public ImportJobEntity getImportJob() { return importJob; }
    public String getExtractedName() { return extractedName; }
    public String getExtractedAddress() { return extractedAddress; }
    public String getEvidence() { return evidence; }
    public String getDisposition() { return disposition; }
    public String getMatchStatus() { return matchStatus; }
    public Integer getSearchTotalCount() { return searchTotalCount; }
    public boolean isSearchComplete() { return Boolean.TRUE.equals(searchComplete); }
    public void matched(String status, int total, boolean complete) {
        matchStatus = status; searchTotalCount = total; searchComplete = complete;
        matchingErrorCode = null; matchedAt = OffsetDateTime.now();
    }
    public void matchingFailed(String code) { matchingErrorCode = code; }
    public void resolve() { disposition = "RESOLVED"; }
    public void dismiss(String note) { disposition = "DISMISSED"; resolutionNote = note.strip(); }
    public Candidate toDto() {
        var video = importJob.getVideo();
        return new Candidate(id, video.getId(), video.getTitle(), extractedName, extractedAddress, sourceLine,
            evidence, matchStatus, matchingErrorCode, matchedAt, disposition, resolutionNote);
    }
}
