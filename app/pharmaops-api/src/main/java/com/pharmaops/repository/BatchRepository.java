package com.pharmaops.repository;

import com.pharmaops.entity.Batch;
import com.pharmaops.entity.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BatchRepository extends JpaRepository<Batch, UUID> {
    Optional<Batch> findByBatchNumber(String batchNumber);
    Page<Batch> findByStatus(BatchStatus status, Pageable pageable);

}