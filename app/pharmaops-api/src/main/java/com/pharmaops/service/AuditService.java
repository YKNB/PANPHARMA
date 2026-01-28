package com.pharmaops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmaops.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.pharmaops.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import com.pharmaops.audit.RequestContext;

@Service
public class AuditService {

    private final AuditEventRepository repo;
    private final ObjectMapper objectMapper;


    public AuditService(AuditEventRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    public void record(String eventType, String entityType, String entityId, Object payloadObject) {
        try {
            AuditEvent event = new AuditEvent();
            event.setEventType(eventType);
            event.setEntityType(entityType);
            event.setEntityId(entityId);
            String payload = (payloadObject == null) ? null : objectMapper.writeValueAsString(payloadObject);
            event.setPayload(payload);
            event.setActor(RequestContext.getActor());
            event.setRequestId(RequestContext.getRequestId());
            repo.save(event);
        } catch (Exception e) {
            // IMPORTANT: audit ne doit JAMAIS casser le flux métier
            // En vrai: on log l'erreur
        }
    }

    public Page<AuditEvent> list(
            String entityType,
            String entityId,
            String actor,
            String requestId,
            Pageable pageable
    ) {
        // priorité: requestId (corrélation)
        if (requestId != null && !requestId.isBlank()) {
            return repo.findByRequestId(requestId, pageable);
        }

        // puis actor
        if (actor != null && !actor.isBlank()) {
            return repo.findByActor(actor, pageable);
        }

        // puis filtre entityType+entityId (si fourni)
        if (entityType != null && !entityType.isBlank() && entityId != null && !entityId.isBlank()) {
            return repo.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        }

        // sinon tout
        return repo.findAll(pageable);
    }
}
