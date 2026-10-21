package kr.sedanji.importjob;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ImportJobRepository extends JpaRepository<ImportJobEntity, Long> {
    Optional<ImportJobEntity> findFirstByVideoIdOrderByIdDesc(String videoId);
}
