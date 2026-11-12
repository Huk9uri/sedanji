package kr.sedanji.video;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<VideoEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VideoEntity v where v.id = :id")
    Optional<VideoEntity> findLocked(String id);

    List<VideoEntity> findAllByOrderByImportedAtDesc();
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "insert into videos(id,title,channel_title,published_at,description,extraction_warning) values (:id,:title,:channel,:published,:description,:warning) on conflict(id) do nothing", nativeQuery=true)
    void insertMetadata(String id, String title, String channel, java.time.OffsetDateTime published, String description, String warning);
}
