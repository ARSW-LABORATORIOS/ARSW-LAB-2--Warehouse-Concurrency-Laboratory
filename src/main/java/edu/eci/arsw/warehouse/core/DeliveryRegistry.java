package edu.eci.arsw.warehouse.core;

import edu.eci.arsw.warehouse.model.DeliveryRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe registry that records each parcel delivery with a unique arrival position.
 */
public class DeliveryRegistry {

    private int nextPosition = 1;
    private final List<DeliveryRecord> deliveries = new ArrayList<>();

    // Synchronized so the read, increment and add of nextPosition happen as one atomic operation.
    // Prevents two robots from getting the same arrival position.
    public synchronized void register(int robotId, int parcelId, long elapsedMillis) {
        int assignedPosition = nextPosition;
        nextPosition = nextPosition + 1;
        deliveries.add(new DeliveryRecord(assignedPosition, robotId, parcelId, elapsedMillis));
    }

    // Synchronized to avoid reading the list while another robot is writing to it.
    public synchronized List<DeliveryRecord> snapshot() {
        return List.copyOf(deliveries);
    }
}
