package kr.sedanji.registration;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRequestReceiptRepository extends JpaRepository<AdminRequestReceiptEntity, Long> {
    Optional<AdminRequestReceiptEntity> findByAdminIdAndIdempotencyKey(long adminId, String idempotencyKey);
}
