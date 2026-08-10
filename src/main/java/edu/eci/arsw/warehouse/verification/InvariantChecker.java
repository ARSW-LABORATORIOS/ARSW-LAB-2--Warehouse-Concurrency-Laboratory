package edu.eci.arsw.warehouse.verification;

import edu.eci.arsw.warehouse.model.DeliveryRecord;
import edu.eci.arsw.warehouse.model.WarehouseSnapshot;

import java.util.HashSet;
import java.util.Set;

public final class InvariantChecker {

    private InvariantChecker() {
    }

    public static InvariantReport check(WarehouseSnapshot snapshot) {
        Set<Integer> parcelIds = new HashSet<>();
        Set<Integer> positions = new HashSet<>();

        for (DeliveryRecord record : snapshot.deliveries()) {
            parcelIds.add(record.parcelId());
            positions.add(record.position());
        }

        boolean uniqueParcelIds = parcelIds.size() == snapshot.deliveries().size();
        boolean uniquePositions = positions.size() == snapshot.deliveries().size();
        boolean contiguousPositions = true;
        for (int expected = 1; expected <= snapshot.deliveries().size(); expected++) {
            if (!positions.contains(expected)) {
                contiguousPositions = false;
                break;
            }
        }

        boolean processedMatchesRegistry = snapshot.processedParcels() == snapshot.deliveries().size();
        boolean allParcelsAccountedFor =
                snapshot.pendingParcels() + snapshot.deliveries().size() == snapshot.initialParcels();

        boolean valid = uniqueParcelIds
                && uniquePositions
                && contiguousPositions
                && processedMatchesRegistry
                && allParcelsAccountedFor
                && snapshot.pendingParcels() == 0
                && snapshot.deliveries().size() == snapshot.initialParcels();

        String detail = "pending=%d, processedCounter=%d, registry=%d, uniqueParcels=%d, uniquePositions=%d, positionsContiguous=%s"
                .formatted(
                        snapshot.pendingParcels(),
                        snapshot.processedParcels(),
                        snapshot.deliveries().size(),
                        parcelIds.size(),
                        positions.size(),
                        contiguousPositions);

        return new InvariantReport(
                valid,
                allParcelsAccountedFor,
                uniqueParcelIds,
                uniquePositions,
                contiguousPositions,
                processedMatchesRegistry,
                detail);
    }
}
