package edu.eci.arsw.warehouse.core;

// Tracks processed parcel count and total processing time across all robots.
public class WarehouseStatistics {

    private int processedParcels;
    private long totalProcessingMillis;

    // Synchronized so two robots don't lose an increment at the same time.
    public synchronized void recordProcessed(long elapsedMillis) {
        processedParcels++;
        totalProcessingMillis += elapsedMillis;
    }

    // Synchronized to read a consistent value while robots may still be writing.
    public synchronized int processedParcels() {
        return processedParcels;
    }

    // Synchronized to read a consistent value while robots may still be writing.
    public synchronized long totalProcessingMillis() {
        return totalProcessingMillis;
    }
}