package kr.sedanji.video;

import java.util.List;
import java.util.Optional;
import kr.sedanji.ExternalApis;
import kr.sedanji.Models.Video;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class VideoService {
    private final VideoRepository videos;
    private final ExternalApis apis;

    public VideoService(VideoRepository videos, ExternalApis apis) {
        this.videos = videos; this.apis = apis;
    }
    public List<Video> findVideos() {
        return videos.findAllByOrderByImportedAtDesc().stream().map(VideoEntity::toDto).toList();
    }
    public Optional<Video> findExisting(String id) {
        return videos.findById(id).map(VideoEntity::toDto);
    }
    public VideoEntity getRequired(String id) {
        return videos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "영상을 찾을 수 없습니다."));
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Video fetchMetadata(String id) { return apis.video(id); }

    @Transactional
    public VideoEntity saveMetadata(Video metadata) {
        return videos.findById(metadata.id()).orElseGet(() -> videos.saveAndFlush(new VideoEntity(metadata)));
    }
    @Transactional
    public Video saveDirectMetadata(Video metadata) {
        videos.insertMetadata(metadata.id(), metadata.title(), metadata.channelTitle(), metadata.publishedAt(),
            metadata.description(), metadata.extractionWarning());
        return videos.findById(metadata.id()).orElseThrow().toDto();
    }
}
