package edu.eci.arsw.warehouse.model;

import java.util.List;

public record WarehouseSnapshot(
        int initialParcels,
        int pendingParcels,
        int processedParcels,
        long totalProcessingMillis,
        List<DeliveryRecord> deliveries) {
}
