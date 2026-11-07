package kr.sedanji.mention;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface MentionRepository extends JpaRepository<MentionEntity, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MentionEntity m where m.id = :id")
    java.util.Optional<MentionEntity> findForUpdate(long id);
    @EntityGraph(attributePaths = {"video", "chef", "restaurant", "candidate", "reviewer"})
    List<MentionEntity> findAllByOrderByIdDesc();
    @EntityGraph(attributePaths = {"video", "chef"})
    List<MentionEntity> findByRestaurantIdAndVerificationStatusAndPublicationStatusOrderByIdAsc(long restaurantId, String status, String publicationStatus);
    boolean existsByChefIdAndRestaurantIdAndVideoId(long chefId, long restaurantId, String videoId);
    @Query("select m.id from MentionEntity m where m.chef.id = :chefId and m.restaurant.kakaoPlaceId = :placeId and m.video.id = :videoId")
    java.util.Optional<Long> findRelationshipId(long chefId, String placeId, String videoId);
    @Query("select m.id from MentionEntity m where m.chef.id = :chefId and m.restaurant.id = :restaurantId and m.video.id = :videoId")
    java.util.Optional<Long> findStoredRelationshipId(long chefId, long restaurantId, String videoId);
}
