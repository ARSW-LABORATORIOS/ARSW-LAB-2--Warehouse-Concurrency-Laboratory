package edu.eci.arsw.warehouse;

import edu.eci.arsw.warehouse.model.DeliveryRecord;
import edu.eci.arsw.warehouse.model.WarehouseSnapshot;
import edu.eci.arsw.warehouse.verification.InvariantChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvariantCheckerTest {

    @Test
    void acceptsAConsistentSnapshot() {
        WarehouseSnapshot snapshot = new WarehouseSnapshot(
                2,
                0,
                2,
                100,
                List.of(
                        new DeliveryRecord(1, 1, 10, 40),
                        new DeliveryRecord(2, 2, 11, 60)));

        assertTrue(InvariantChecker.check(snapshot).valid());
    }

    @Test
    void rejectsDuplicateArrivalPositions() {
        WarehouseSnapshot snapshot = new WarehouseSnapshot(
                2,
                0,
                2,
                100,
                List.of(
                        new DeliveryRecord(1, 1, 10, 40),
                        new DeliveryRecord(1, 2, 11, 60)));

        assertFalse(InvariantChecker.check(snapshot).valid());
    }
}
