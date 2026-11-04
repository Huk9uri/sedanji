package kr.sedanji.admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AdminUserRepository extends JpaRepository<AdminUserEntity, Long> {
    Optional<AdminUserEntity> findByUsername(String username);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AdminUserEntity a where a.username = :username")
    Optional<AdminUserEntity> findForUpdate(String username);
}
