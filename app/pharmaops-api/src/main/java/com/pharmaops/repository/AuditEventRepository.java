package com.pharmaops.repository;

import com.pharmaops.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Page<AuditEvent> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);
    Page<AuditEvent> findByActor(String actor, Pageable pageable);
    Page<AuditEvent> findByRequestId(String requestId, Pageable pageable);

}
