package kr.sedanji.restaurantcandidate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidateEntity, Long> {
    Optional<PlaceCandidateEntity> findByCandidateIdAndPlaceId(long candidateId, String placeId);
    List<PlaceCandidateEntity> findByCandidateIdOrderByIdAsc(long candidateId);
    @Modifying(flushAutomatically = true)
    @Query("delete from PlaceCandidateEntity p where p.candidate.id=:candidateId")
    void deleteForCandidate(long candidateId);
}
