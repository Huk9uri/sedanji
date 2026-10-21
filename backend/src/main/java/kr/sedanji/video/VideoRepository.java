package kr.sedanji.video;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepository extends JpaRepository<VideoEntity, String> {
    List<VideoEntity> findAllByOrderByImportedAtDesc();
}
