package edu.eci.arsw.warehouse.core;

public class WarehouseStatistics {

    private int processedParcels;
    private long totalProcessingMillis;

    public synchronized void recordProcessed(long elapsedMillis) {
        processedParcels++;
        totalProcessingMillis += elapsedMillis;
    }

    public synchronized int processedParcels() {
        return processedParcels;
    }

    public synchronized long totalProcessingMillis() {
        return totalProcessingMillis;
    }
}