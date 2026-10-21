package kr.sedanji.mention;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface MentionRepository extends JpaRepository<MentionEntity, Long> {
    @EntityGraph(attributePaths = {"video", "chef", "restaurant"})
    List<MentionEntity> findAllByOrderByIdDesc();
    boolean existsByVideoIdAndSourceLineAndExtractedName(String videoId, int sourceLine, String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MentionEntity m where m.id = :id")
    Optional<MentionEntity> findLocked(long id);
    @EntityGraph(attributePaths = {"video", "chef"})
    List<MentionEntity> findByRestaurantIdAndStatusOrderByIdAsc(long restaurantId, String status);
}
