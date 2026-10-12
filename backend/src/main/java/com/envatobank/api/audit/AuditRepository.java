package com.envatobank.api.audit;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditLog, Long> {

    Optional<AuditLog> findTopByOrderByIdDesc();
}
