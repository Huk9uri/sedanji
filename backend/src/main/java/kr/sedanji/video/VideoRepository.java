package kr.sedanji.video;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<VideoEntity, String> {
    List<VideoEntity> findAllByOrderByImportedAtDesc();
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "insert into videos(id,title,channel_title,published_at,description,extraction_warning) values (:id,:title,:channel,:published,:description,:warning) on conflict(id) do nothing", nativeQuery=true)
    void insertMetadata(String id, String title, String channel, java.time.OffsetDateTime published, String description, String warning);
}
