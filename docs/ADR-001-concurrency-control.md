# ADR-001: Concurrency control for warehouse shared state

## Context

In the warehouse simulator, several robots (Java threads) share four objects: `PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics` and `SimulationControl`. In the initial code we have no protection over these objects, which generates race conditions, so two robots can take the same package, also receive the same delivery position, lose counter increments, or burn CPU in active waiting. The task is to fix those problems with the minimum necessary synchronization, without removing concurrency or using a global lock.

## Decision

### Nicolás — PackageQueue and DeliveryRegistry

I used `synchronized` at method level in each class one by one. In `PackageQueue`, `takeNext()` and `pendingCount()` were synchronized so the check, the read and the remove are a single atomic operation. In `DeliveryRegistry`, `register()` and `snapshot()` were synchronized so the read of `nextPosition`, the increment and the `add` cannot be interrupted by another robot. Each class has its own assigned monitor (`this`), independent from each other.

### Vera — WarehouseStatistics

*(borrador — Vera, ajusta si quieres cambiar algo)* I synchronized `recordProcessed()`, `processedParcels()` and `totalProcessingMillis()` so the counter and the total time always update together. Before, the method read the current value, waited, and wrote the new one in separate steps, so two robots could read the same value and one increment got lost. With `synchronized` only one robot can be inside `recordProcessed()` at a time, so that can't happen anymore.

### Mabel — SimulationControl and WarehouseMain

I got rid of the busy-wait in `SimulationControl` — the old code just kept checking `while (paused) { Thread.onSpinWait(); }` over and over, which burns CPU for nothing. Now `pause()`, `resume()`, `awaitIfPaused()` and `isPaused()` are all `synchronized` on the same object, so a robot calling `awaitIfPaused()` actually goes to sleep with `wait()` instead of spinning, and `resume()` wakes everyone up at once with `notifyAll()`. On top of that, `WarehouseMain` used to print the final report after just `Thread.sleep(60)`, which doesn't really guarantee the robots are done — I changed it to call `simulation.awaitCompletion()` instead, which does `robot.join()` on every robot, so now the report only prints once everyone has actually finished.

## Alternatives considered

- **Global lock shared across all classes:** discarded because it would block robots that only want to request a package while another is registering a delivery, reducing throughput unnecessarily.
- **`AtomicInteger`:** valid for simple counters, but in `DeliveryRegistry` the position, the increment and the `add` must be atomic together, and `AtomicInteger` only protects one variable at a time. Same problem in `WarehouseStatistics`, which has two fields (`processedParcels` and `totalProcessingMillis`) that need to stay in sync.
- **`ReentrantLock`:** the assignment requires `synchronized` and for this case the same granularity is achieved with it.
- **Private lock object instead of `synchronized` methods** ("Solución 2" from the class slides): this avoids exposing `this` as the monitor, which is flagged as an antipattern if the object is ever accessed from outside. We didn't use it because none of these 4 classes are ever referenced from outside `WarehouseRobot`/`WarehouseSimulation`, so there's nothing external that could lock on them by accident.

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
- Before: `RaceConditionProbe` showed `processedCounter=242, registry=245` — a mismatch between the counter and the actual number of deliveries.
- After: with all three branches merged, `RaceConditionProbe` came back 0/100 anomalous runs across all three required configurations (8/100, 16/250, 32/500) — the counter matches the registry size every time now.

### Mabel
- Before: `WarehouseMain` printed "STARTER REPORT (intentionally premature)" while robots were still running; `SimulationControl` spun in a loop instead of sleeping.
- After: `WarehouseMain` and `PauseResumeDemo` run cleanly, the final report only prints once all robots finish, and pause/resume works without busy-waiting.

## Consequences

- Each class protects its own invariants independently.
- There is no global lock, so robots operating on different classes do not block each other.
- The public behavior of each class did not change: same signatures, same semantics.
- Correctness no longer depends on the operating system scheduler.

## Risks

- If in the future logic is added that requires atomicity across two different classes, the separate monitors would not be sufficient and the design would need to be revisited.
- If someone adds a new method later that touches these same fields without going through the synchronized methods, the protection breaks and the compiler won't warn about it.
- `wait()`/`notifyAll()` in `SimulationControl` only works correctly if every caller goes through the synchronized methods — calling the internal logic from somewhere that skips the monitor would bring back the race.
- In a scenario with multiple JVM instances behind a load balancer, `synchronized` does not protect anything across separate processes — the consistency guarantee would have to move to the database.
