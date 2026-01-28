package com.pharmaops.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String eventType; // CREATE_BATCH, READ_BATCH, ...

    @Column(nullable = false, length = 64)
    private String entityType; // Batch

    @Column(nullable = false, length = 64)
    private String entityId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON string

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 64)
    private String actor; // ex: "karl", "system", "unknown"

    @Column(nullable = false, length = 64)
    private String requestId;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.actor == null) this.actor = "unknown";
        if (this.requestId == null) this.requestId = "n/a";
    }


    // getters
    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getCreatedAt() { return createdAt; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

}
