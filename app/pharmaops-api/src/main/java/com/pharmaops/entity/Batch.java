package com.pharmaops.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "batches")
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String batchNumber;

    @Column(nullable = false, length = 64)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BatchStatus status;


    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        if (this.status == null) this.status = BatchStatus.CREATED;
    }


    // Getters / Setters
    public java.util.UUID getId() { return id; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
