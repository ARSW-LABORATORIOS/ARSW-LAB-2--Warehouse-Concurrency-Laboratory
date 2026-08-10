package edu.eci.arsw.warehouse.app;

public final class PauseResumeDemo {

    private PauseResumeDemo() {
    }

    public static void main(String[] args) throws Exception {
        WarehouseSimulation simulation = new WarehouseSimulation(12, 180);
        simulation.start();

        Thread.sleep(250);
        simulation.pause();
        Thread.sleep(100);

        System.out.println("\n--- PAUSED SNAPSHOT ---");
        WarehouseMain.printSnapshot(simulation.snapshot());
        System.out.println("Simulation paused = " + simulation.isPaused());

        Thread.sleep(500);
        simulation.resume();
        simulation.awaitCompletion();

        System.out.println("\n--- FINAL SNAPSHOT ---");
        WarehouseMain.printSnapshot(simulation.snapshot());
    }
}
