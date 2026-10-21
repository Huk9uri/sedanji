package kr.sedanji.mention;

import org.springframework.data.jpa.repository.*;

public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidateEntity, PlaceCandidateId> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PlaceCandidateEntity p where p.id.mentionId = :mentionId")
    void deleteForMention(long mentionId);
}
