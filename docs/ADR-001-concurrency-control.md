# ADR-001: Concurrency control for warehouse shared state

## Context

The simulation has a bunch of `WarehouseRobot` threads running at the same time, all sharing 4 objects: `PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics` and `SimulationControl`. In the starter code none of these are synchronized, so we were getting the classic bugs: `PackageQueue.takeNext()` could hand out the same parcel twice, `DeliveryRegistry.register()` could assign the same position to two robots, `WarehouseStatistics.recordProcessed()` could lose an increment, and `SimulationControl` was just spinning in a loop checking a flag instead of actually waiting.

## Decision

We synchronized each of the 4 classes on its own, not with one shared lock for everything. Each class got `synchronized` on the methods that touch its own state, keeping the locked part as small as we could (basically just the read+write that causes the race). For `SimulationControl` specifically we swapped the busy-wait loop for `wait()`/`notifyAll()`, since that's the only way in Java to actually sleep a thread and wake it back up instead of polling.

## Alternatives considered

- **One global lock for everything** — rejected, it would serialize operations that have nothing to do with each other (e.g. taking a parcel would block on delivery registration for no reason).
- **Private lock object instead of `synchronized` methods** (this is "Solución 2" from the class slides) — the professor showed this as usually the safer option, since putting `synchronized` on a method uses the object itself as the lock, and that's flagged as an antipattern ("exponer el lock") if the object is ever exposed to code outside. We looked at it but didn't use it because none of these 4 classes are ever touched by anything outside `WarehouseRobot`/`WarehouseSimulation`, so there's nothing external that could accidentally lock on them.
- **`AtomicInteger`/`AtomicLong`** — works fine for a single counter, but `WarehouseStatistics` has two fields (`processedParcels` and `totalProcessingMillis`) that need to update together, so two separate atomics wouldn't guarantee they stay in sync with each other.
- **Keeping the busy-wait as-is** — not an option, it wastes CPU the whole time the simulation is paused and the assignment explicitly asks to get rid of it.

## Quality attributes affected

- **Correctness/reliability**: gets rid of the race conditions we found (see `RaceConditionProbe` results in `docs/REPORT.md`, target is 0/100 anomalous runs).
- **Performance/throughput**: locking each class separately instead of everything together means robots can still work in parallel as long as they're touching different shared objects.
- **Maintainability**: each class handles its own locking, so you don't need to understand all 4 classes at once to know if one of them is thread-safe.

## Evidence

See `docs/REPORT.md` sections 2 (Observed anomalies) and 7 (Verification results) for the before/after `RaceConditionProbe` runs across 3 configurations (8/100, 16/250, 32/500 robots/parcels).

## Consequences

Operations on the same shared object now run one at a time instead of concurrently, which is fine since each of those operations is short. Robots working on different objects at the same instant can still run fully in parallel.

## Risks

- If someone adds a new method later that touches these same fields without going through the synchronized methods, the protection breaks and the compiler won't warn about it.
- `wait()`/`notifyAll()` in `SimulationControl` only works correctly if every caller goes through the synchronized methods — calling the internal logic from somewhere that skips the monitor would bring back the race.
