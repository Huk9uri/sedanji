package kr.sedanji.video;

import kr.sedanji.Models.Video;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoMetadataService {
    private final VideoService videos;
    public VideoMetadataService(VideoService videos) { this.videos = videos; }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Video register(String url) {
        // 1. url에서 영상의 ID 11자리 추출
        String id = VideoUrl.parse(url);
        // 2. 저장된 영상인지 확인
        var existing = videos.findExisting(id);
        // True 라면 재사용
        if (existing.isPresent()) return existing.get();
        // 3. youtube 영상 조회 api로 제목, 채널, 게시글, 설명란 조회
        var metadata = videos.fetchMetadata(id);
        // 4. 영상 메타데이터 저장
        return videos.saveDirectMetadata(new Video(metadata.id(), metadata.title(), metadata.channelTitle(),
            metadata.publishedAt(), metadata.description(), "영상 메타데이터만 등록했습니다. 식당 후보와 관계는 별도로 확인하세요."));
    }
}
