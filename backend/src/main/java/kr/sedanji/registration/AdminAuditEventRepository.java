package kr.sedanji.registration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEventEntity, Long> {}
