package edu.eci.arsw.warehouse.core;

import edu.eci.arsw.warehouse.model.Parcel;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe queue of pending parcels.
 * Each robot calls takeNext() to get its next parcel to process.
 */
public class PackageQueue {

    private final List<Parcel> pending = new ArrayList<>();

    public PackageQueue(List<Parcel> parcels) {
        pending.addAll(parcels);
    }

    // Synchronized so the check, read and remove happen as one atomic operation.
    // Prevents two robots from taking the same parcel.
    public synchronized Parcel takeNext() {
        if (pending.isEmpty()) {
            return null;
        }
        return pending.remove(0);
    }

    // Synchronized to avoid reading the list while another robot is modifying it.
    public synchronized int pendingCount() {
        return pending.size();
    }
}
