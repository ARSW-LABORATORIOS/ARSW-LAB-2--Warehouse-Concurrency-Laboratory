package edu.eci.arsw.warehouse.app;

import edu.eci.arsw.warehouse.model.DeliveryRecord;
import edu.eci.arsw.warehouse.model.WarehouseSnapshot;

import java.util.Comparator;

public final class WarehouseMain {

    private WarehouseMain() {
    }

    public static void main(String[] args) throws Exception {
        int robots = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int parcels = args.length > 1 ? Integer.parseInt(args[1]) : 100;

        WarehouseSimulation simulation = new WarehouseSimulation(robots, parcels);

        System.out.printf("Starting warehouse with %d robots and %d parcels...%n", robots, parcels);
        simulation.start();

        // Intentionally wrong architecture-level coordination:
        // the application reports a "final" state before workers have finished.
        Thread.sleep(60);
        System.out.println("\n--- STARTER REPORT (intentionally premature) ---");
        printSnapshot(simulation.snapshot());
        System.out.println("----------------------------------------------\n");

        // The JVM stays alive because robot threads are non-daemon threads.
        // TODO LAB 2: coordinate completion explicitly with join() and print exactly one
        // consistent final report after all workers terminate.
    }

    static void printSnapshot(WarehouseSnapshot snapshot) {
        System.out.printf("Initial parcels : %d%n", snapshot.initialParcels());
        System.out.printf("Pending parcels : %d%n", snapshot.pendingParcels());
        System.out.printf("Processed count : %d%n", snapshot.processedParcels());
        System.out.printf("Registry size   : %d%n", snapshot.deliveries().size());

        snapshot.deliveries().stream()
                .min(Comparator.comparingInt(DeliveryRecord::position))
                .ifPresent(first -> System.out.printf(
                        "Current leader  : Robot-%02d / parcel %d / position %d%n",
                        first.robotId(), first.parcelId(), first.position()));
    }
}
