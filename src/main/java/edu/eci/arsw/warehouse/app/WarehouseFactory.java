package edu.eci.arsw.warehouse.app;

import edu.eci.arsw.warehouse.model.Parcel;

import java.util.ArrayList;
import java.util.List;

final class WarehouseFactory {

    private WarehouseFactory() {
    }

    static List<Parcel> parcels(int count) {
        List<Parcel> parcels = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            int processingMillis = 10 + (i % 7) * 4;
            parcels.add(new Parcel(i, processingMillis));
        }
        return parcels;
    }
}
