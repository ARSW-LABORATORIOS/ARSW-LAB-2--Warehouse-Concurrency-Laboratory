package edu.eci.arsw.warehouse.verification;

import edu.eci.arsw.warehouse.app.WarehouseSimulation;

public final class RaceConditionProbe {

    private RaceConditionProbe() {
    }

    public static void main(String[] args) throws Exception {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 30;
        int robots = args.length > 1 ? Integer.parseInt(args[1]) : 24;
        int parcels = args.length > 2 ? Integer.parseInt(args[2]) : 250;

        int anomalies = 0;

        for (int run = 1; run <= runs; run++) {
            WarehouseSimulation simulation = new WarehouseSimulation(robots, parcels);
            simulation.start();
            simulation.awaitCompletion();

            InvariantReport report = InvariantChecker.check(simulation.snapshot());
            if (!report.valid()) {
                anomalies++;
                System.out.printf("Run %02d -> RACE/ANOMALY | %s%n", run, report.detail());
            } else {
                System.out.printf("Run %02d -> OK           | %s%n", run, report.detail());
            }
        }

        System.out.printf("%nAnomalous runs: %d/%d%n", anomalies, runs);
        System.out.println("The starter is expected to be unsafe. Your final solution should converge to 0 anomalies.");
    }
}
