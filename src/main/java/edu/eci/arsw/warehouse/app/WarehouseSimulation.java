package edu.eci.arsw.warehouse.app;

import edu.eci.arsw.warehouse.core.DeliveryRegistry;
import edu.eci.arsw.warehouse.core.PackageQueue;
import edu.eci.arsw.warehouse.core.SimulationControl;
import edu.eci.arsw.warehouse.core.WarehouseStatistics;
import edu.eci.arsw.warehouse.model.WarehouseSnapshot;
import edu.eci.arsw.warehouse.worker.WarehouseRobot;

import java.util.ArrayList;
import java.util.List;

public final class WarehouseSimulation {

    private final int robotCount;
    private final int parcelCount;
    private final PackageQueue packageQueue;
    private final DeliveryRegistry registry;
    private final WarehouseStatistics statistics;
    private final SimulationControl control;
    private final List<WarehouseRobot> robots = new ArrayList<>();

    public WarehouseSimulation(int robotCount, int parcelCount) {
        if (robotCount < 1 || parcelCount < 1) {
            throw new IllegalArgumentException("robotCount and parcelCount must be positive");
        }
        this.robotCount = robotCount;
        this.parcelCount = parcelCount;
        this.packageQueue = new PackageQueue(WarehouseFactory.parcels(parcelCount));
        this.registry = new DeliveryRegistry();
        this.statistics = new WarehouseStatistics();
        this.control = new SimulationControl();
    }

    public void start() {
        if (!robots.isEmpty()) {
            throw new IllegalStateException("Simulation already started");
        }
        long startNanos = System.nanoTime();
        for (int robotId = 1; robotId <= robotCount; robotId++) {
            WarehouseRobot robot = new WarehouseRobot(
                    robotId, packageQueue, registry, statistics, control, startNanos);
            robots.add(robot);
            robot.start();
        }
    }

    public void pause() {
        control.pause();
    }

    public void resume() {
        control.resume();
    }

    public void awaitCompletion() throws InterruptedException {
        for (WarehouseRobot robot : robots) {
            robot.join();
        }
    }

    public WarehouseSnapshot snapshot() {
        return new WarehouseSnapshot(
                parcelCount,
                packageQueue.pendingCount(),
                statistics.processedParcels(),
                statistics.totalProcessingMillis(),
                registry.snapshot());
    }

    public boolean isPaused() {
        return control.isPaused();
    }
}
