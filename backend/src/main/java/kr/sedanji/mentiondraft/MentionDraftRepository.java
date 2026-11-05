package kr.sedanji.mentiondraft;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface MentionDraftRepository extends JpaRepository<MentionDraftEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from MentionDraftEntity d where d.id = :id")
    Optional<MentionDraftEntity> findForUpdate(long id);
    Page<MentionDraftEntity> findByRestaurantIdAndKindAndStatus(long restaurantId, String kind, String status, Pageable pageable);
}
