# ADR-001: Concurrency control for warehouse shared state

## Context

In the warehouse simulator, several robots (Java threads) share four objects: `PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics` and `SimulationControl`. In the initial code we have no protection over these objects, which generates race conditions, so two robots can take the same package, also receive the same delivery position, lose counter increments, or burn CPU in active waiting. The task is to fix those problems with the minimum necessary synchronization, without removing concurrency or using a global lock.

## Decision

### Nicolás — PackageQueue and DeliveryRegistry

I used `synchronized` at method level in each class one by one. In `PackageQueue`, `takeNext()` and `pendingCount()` were synchronized so the check, the read and the remove are a single atomic operation. In `DeliveryRegistry`, `register()` and `snapshot()` were synchronized so the read of `nextPosition`, the increment and the `add` cannot be interrupted by another robot. Each class has its own assigned monitor (`this`), independent from each other.

### Vera — WarehouseStatistics

[TO COMPLETE — Vera]

### Mabel — SimulationControl and WarehouseMain

[TO COMPLETE — Mabel]

## Alternatives considered

- **Global lock shared across all classes:** discarded because it would block robots that only want to request a package while another is registering a delivery, reducing throughput unnecessarily.
- **`AtomicInteger`:** valid for simple counters, but in `DeliveryRegistry` the position, the increment and the `add` must be atomic together, and `AtomicInteger` only protects one variable at a time.
- **`ReentrantLock`:** the assignment requires `synchronized` and for this case the same granularity is achieved with it.

## Quality attributes affected

| Attribute | Impact |
|---|---|
| Correctness | Improves: invariants of unique positions and consistent counters are guaranteed |
| Performance | Slight reduction: robots wait their turn to enter critical regions, but blocking is minimal since each class has its own monitor |
| Maintainability | Improves: the protected region is explicit and justified, no hidden locks or unnecessary synchronization |

## Evidence

### Nicolás
- Before: `RaceConditionProbe` showed `Queue anomaly: IndexOutOfBoundsException` and duplicate positions in the registry.
- After: `mvn clean test` passes with BUILD SUCCESS, 2/2 tests.

### Vera

[TO COMPLETE — Vera]

### Mabel

[TO COMPLETE — Mabel]

## Consequences

- Each class protects its own invariants independently.
- There is no global lock, so robots operating on different classes do not block each other.
- The public behavior of each class did not change: same signatures, same semantics.
- Correctness no longer depends on the operating system scheduler.

## Risks

- If in the future logic is added that requires atomicity across two different classes, the separate monitors would not be sufficient and the design would need to be revisited.
- In a scenario with multiple JVM instances behind a load balancer, `synchronized` does not protect anything across separate processes — the consistency guarantee would have to move to the database.
