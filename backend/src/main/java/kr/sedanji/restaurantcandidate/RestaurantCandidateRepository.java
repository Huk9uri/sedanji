package kr.sedanji.restaurantcandidate;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface RestaurantCandidateRepository extends JpaRepository<RestaurantCandidateEntity, Long> {
    @EntityGraph(attributePaths = {"importJob", "importJob.video"})
    List<RestaurantCandidateEntity> findAllByOrderByIdDesc();
    boolean existsByImportJobIdAndSourceLineAndExtractedName(Long jobId, Integer line, String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RestaurantCandidateEntity c where c.id=:id")
    Optional<RestaurantCandidateEntity> findLocked(long id);
}
