package edu.eci.arsw.warehouse.verification;

public record InvariantReport(
        boolean valid,
        boolean allParcelsAccountedFor,
        boolean uniqueParcelIds,
        boolean uniqueArrivalPositions,
        boolean contiguousArrivalPositions,
        boolean processedMatchesRegistry,
        String detail) {
}
