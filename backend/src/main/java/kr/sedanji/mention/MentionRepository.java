package kr.sedanji.mention;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface MentionRepository extends JpaRepository<MentionEntity, Long> {
    @EntityGraph(attributePaths = {"video", "chef", "restaurant", "candidate", "reviewer"})
    List<MentionEntity> findAllByOrderByIdDesc();
    @EntityGraph(attributePaths = {"video", "chef"})
    List<MentionEntity> findByRestaurantIdAndVerificationStatusOrderByIdAsc(long restaurantId, String status);
    boolean existsByChefIdAndRestaurantIdAndVideoIdAndMentionType(long chefId, long restaurantId, String videoId, MentionType type);
}
