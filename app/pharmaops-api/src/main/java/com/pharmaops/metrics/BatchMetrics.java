package com.pharmaops.metrics;

import com.pharmaops.entity.BatchStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BatchMetrics {

    private final Counter batchCreatedTotal;
    private final Counter batchStatusChangeTotal;

    private final Map<BatchStatus, AtomicInteger> batchesCurrentByStatus = new EnumMap<>(BatchStatus.class);

    public BatchMetrics(MeterRegistry registry) {

        // Counter: batches created
        this.batchCreatedTotal = Counter.builder("pharmaops.batch.created.count")
                .description("Total number of batches created")
                .register(registry);


        // Counter: batch status changes
        this.batchStatusChangeTotal = registry.counter("pharmaops.batch.status.change.total");

        // Gauges: current batches by status
        for (BatchStatus s : BatchStatus.values()) {
            AtomicInteger holder = new AtomicInteger(0);
            batchesCurrentByStatus.put(s, holder);

            Gauge.builder("pharmaops.batch.current", holder, AtomicInteger::get)
                    .tag("status", s.name())
                    .register(registry);
        }
    }

    public void incrementCreated() {
        batchCreatedTotal.increment();
    }

    public void incrementStatusChange() {
        batchStatusChangeTotal.increment();
    }

    public void setCurrent(BatchStatus status, int value) {
        AtomicInteger holder = batchesCurrentByStatus.get(status);
        if (holder != null) holder.set(value);
    }
}
