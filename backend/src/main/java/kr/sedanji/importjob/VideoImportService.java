package kr.sedanji.importjob;

import java.time.OffsetDateTime;
import kr.sedanji.DescriptionExtractor;
import kr.sedanji.Models.Extracted;
import kr.sedanji.Models.Video;
import kr.sedanji.restaurantcandidate.RestaurantCandidateService;
import kr.sedanji.video.VideoService;
import kr.sedanji.video.VideoUrl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoImportService {
    private final VideoService videos;
    private final ImportJobRepository jobs;
    private final DescriptionExtractor extractor;
    private final RestaurantCandidateService candidates;

    public VideoImportService(VideoService videos, ImportJobRepository jobs, DescriptionExtractor extractor,
                              RestaurantCandidateService candidates) {
        this.videos = videos; this.jobs = jobs; this.extractor = extractor; this.candidates = candidates;
    }
    @Transactional
    public Video importVideo(String url) {
        String id = VideoUrl.parse(url);
        var existing = videos.findExisting(id);
        if (existing.isPresent()) return existing.get();
        OffsetDateTime started = OffsetDateTime.now();
        Video video = videos.fetchMetadata(id);
        var entity = videos.saveMetadata(video);
        var job = jobs.saveAndFlush(new ImportJobEntity(entity, started));
        for (Extracted candidate : extractor.extract(video.description()))
            candidates.addExtractedCandidate(job, candidate);
        return entity.toDto();
    }
}
