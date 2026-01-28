package com.pharmaops.controller;

import com.pharmaops.dto.CreateBatchRequest;
import com.pharmaops.entity.Batch;
import com.pharmaops.service.BatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import com.pharmaops.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.pharmaops.dto.UpdateBatchStatusRequest;


@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService service;

    public BatchController(BatchService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Batch> create(@Valid @RequestBody CreateBatchRequest req) {
        return ResponseEntity.ok(service.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Batch> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));

    }

    @GetMapping
    public PageResponse<Batch> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String status
    ) {
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sort));

        Page<Batch> result = service.list(status, pageable);

        return new PageResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Batch> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateBatchStatusRequest req
    ) {
        return ResponseEntity.ok(service.updateStatus(id, req.getStatus()));
    }
}
