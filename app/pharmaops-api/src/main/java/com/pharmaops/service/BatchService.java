package com.pharmaops.service;

import com.pharmaops.dto.CreateBatchRequest;
import com.pharmaops.entity.Batch;
import com.pharmaops.exception.ConflictException;
import com.pharmaops.exception.NotFoundException;
import com.pharmaops.repository.BatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.pharmaops.entity.BatchStatus;
import com.pharmaops.exception.BadRequestException;


import java.util.UUID;

@Service
public class BatchService {

    private final BatchRepository repo;
    private final AuditService auditService;
    public BatchService(BatchRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    public Batch create(CreateBatchRequest req) {
        repo.findByBatchNumber(req.getBatchNumber())
                .ifPresent(b -> {
                    throw new ConflictException("batchNumber already exists");
                });

        Batch b = new Batch();
        b.setBatchNumber(req.getBatchNumber());
        b.setProductCode(req.getProductCode());
        b.setStatus(BatchStatus.CREATED);

        Batch saved = repo.save(b);

        auditService.record(
                "CREATE_BATCH",
                "Batch",
                saved.getId().toString(),
                saved
        );

        return saved;
    }

    public Batch getById(UUID id) {
        Batch batch = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Batch not found"));

        auditService.record(
                "READ_BATCH",
                "Batch",
                batch.getId().toString(),
                null
        );

        return batch;
    }

    public Page<Batch> list(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            BatchStatus st;
            try {
                st = BatchStatus.valueOf(status.trim().toUpperCase());
            } catch (Exception e) {
                throw new BadRequestException("Invalid status. Allowed: CREATED, IN_PROGRESS, COMPLETED, REJECTED");
            }
            return repo.findByStatus(st, pageable);
        }
        return repo.findAll(pageable);
    }


    public Batch updateStatus(UUID id, String newStatusRaw) {
        Batch batch = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Batch not found"));

        BatchStatus newStatus;
        try {
            newStatus = BatchStatus.valueOf(newStatusRaw.trim().toUpperCase());
        } catch (Exception e) {
            throw new BadRequestException("Invalid status. Allowed: CREATED, IN_PROGRESS, COMPLETED, REJECTED");
        }

        BatchStatus oldStatus = batch.getStatus();
        if (oldStatus == newStatus) {
            throw new BadRequestException("Status is already " + newStatus);
        }

        batch.setStatus(newStatus);
        Batch saved = repo.save(batch);

        auditService.record(
                "UPDATE_BATCH_STATUS",
                "Batch",
                saved.getId().toString(),
                java.util.Map.of("from", oldStatus.toString(), "to", newStatus.toString())
        );

        return saved;
    }

}
