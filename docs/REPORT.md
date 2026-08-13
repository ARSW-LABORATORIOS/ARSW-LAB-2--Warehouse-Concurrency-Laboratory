# Laboratory 2 Report

Team: Mabel · Nicolás · Vera

## 1. Shared-state inventory

| Shared object         | Mutable state                                | Readers                 | Writers                           | Possible invariant                                   |
|-----------------------|----------------------------------------------|--------------------------|-----------------------------------|------------------------------------------------------|
| `PackageQueue`        | Lista `pending` de paquetes                  | Robots y snapshot        | Robots usando `takeNext()`        | Un paquete no debe ser tomado más de una vez         |
| `DeliveryRegistry`    | Lista de entregas y `nextPosition`           | Snapshot y verificación  | Robots usando `register()`        | Las posiciones deben ser únicas y consecutivas       |
| `WarehouseStatistics` | `processedParcels` y `totalProcessingMillis` | Snapshot y reporte       | Robots usando `recordProcessed()` | El contador procesado debe coincidir con el registro |
| `SimulationControl`   | Bandera `paused`                             | Todos los robots         | `pause()` y `resume()`            | Un robot pausado no debe seguir avanzando            |

## 2. Observed anomalies

### Evidence 1 - Queue anomaly

Command used:

java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain

Observed output:

[warehouse-robot-12] Queue anomaly: IndexOutOfBoundsException

Suspected class/method:

PackageQueue.takeNext()

Explanation:

Several robots access and modify the pending package list at the same time,
which means that a robot can check or read the list while
another robot removes an element, causing the list state
to change before the first robot finishes its operation.

### Evidence 2 - Processed counter mismatch

Command used:

java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe

Execution:

Run 01

Observed output:

processedCounter=242, registry=245

Suspected class/method:

WarehouseStatistics.recordProcessed()

Explanation:

The processed counter is lower than the number of deliveries,
this shows that some increments were lost when multiple
robots updated the shared counter concurrently. This matches the
"contador++ no es atómico" pattern from the course material: the
starter reads, waits, and writes the counter as three separate
steps instead of one indivisible operation.

### Evidence 3 - Invalid delivery positions

Command used:

java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe

Execution:

Run 01

Observed output:

registry=245, uniquePositions=232, positionsContiguous=false

Suspected class/method:

DeliveryRegistry.register()

Explanation:

The number of unique positions is lower than the number of
registered deliveries, which means that multiple deliveries
received the same position resulting in positions that don't
form a continuous sequence.

## 3. Interleaving analysis

For this interleaving we use the `processedParcels` counter in `WarehouseStatistics`.

| Step | Thread A                       | Thread B                       | Shared state             |
|------|---------------------------------|---------------------------------|---------------------------|
| 1    | Reads `processedParcels = 10`  |                                 | `processedParcels = 10`  |
| 2    |                                 | Reads `processedParcels = 10`  | `processedParcels = 10`  |
| 3    | Writes `processedParcels = 11` |                                 | `processedParcels = 11`  |
| 4    |                                 | Writes `processedParcels = 11` | `processedParcels = 11`  |

Why is the final result dependent on scheduling?

The final result depends on scheduling because the threads can execute
their read and write operations in different orders. If two robots read
the same value before one of them updates it, one increment can be lost.
Also if we make another execution the threads can run in another order
and give a different result — the code itself never changes, only the
order in which the scheduler interleaves the two threads changes, which
is outside our control.

## 4. System invariants

| Candidate invariant                                                    | Classification |
|--------------------------------------------------------------------------|-----------------|
| Every parcel is processed at most once                                 | Required        |
| No parcel disappears from the system                                   | Required        |
| Arrival positions are unique                                           | Required        |
| Arrival positions form a valid sequence from `1..N`                    | Required        |
| The processed counter matches the number of delivery records           | Required        |
| When the simulation is reported as complete, no parcels remain pending | Required        |

Final invariants:

I1: Every parcel must be processed at most once.

I2: No parcel can disappear from the system.

I3: Every arrival position must be unique.

I4: Arrival positions must form a continuous sequence from `1` to `N`.

I5: The processed counter must match the number of delivery records.

I6: When the simulation is complete, there must be no pending parcels.

## 5. Critical regions and synchronization decisions

| Class                  | Critical region                                                     | Protected invariant                                                                              | Synchronization mechanism                        | Why this granularity?                                                                                                                                    |
|--------------------------|------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|-----------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `WarehouseStatistics`   | Update and read of `processedParcels` and `totalProcessingMillis`   | I5                                                                                                  | `synchronized` on the object monitor (methods)      | Only the methods that access the shared mutable statistics are synchronized, to avoid a global lock or synchronizing unrelated operations                |
| `PackageQueue`          | [COMPLETAR — Nicolás]                                                | I1                                                                                                  | [COMPLETAR]                                          | [COMPLETAR]                                                                                                                                                 |
| `DeliveryRegistry`      | [COMPLETAR — Nicolás]                                                | I3 / I4                                                                                             | [COMPLETAR]                                          | [COMPLETAR]                                                                                                                                                 |
| `SimulationControl`     | Read/write of `paused`, and the wait/notify coordination in `awaitIfPaused()` | I6 (indirectly — pausing must not let robots keep consuming parcels)                    | `synchronized` methods + `wait()` / `notifyAll()`    | All 4 methods (`pause`, `resume`, `awaitIfPaused`, `isPaused`) synchronize on the same monitor (`this`) because they all read/write the same single boolean; `wait()`/`notifyAll()` require that shared monitor to coordinate blocking without polling |

**Why `synchronized` methods instead of a private lock object?**

The course material (Semana 2, "Solución 2: bloque sincronizado") shows a private `Object lock` as the safer default, since a `synchronized` method exposes the object itself (`this`) as the monitor — the antipattern flagged as "exponer el lock". We kept `synchronized` methods here because none of these 4 classes are exposed as a public API outside the project: only `WarehouseRobot` and `WarehouseSimulation`, inside the same trusted codebase, hold references to them. A private lock would add no extra safety in this specific case, but it would be the correct default if these classes were ever exposed externally.

**Why not `AtomicInteger`/`AtomicLong` for `WarehouseStatistics`?**

The course material's decision map (Semana 2, "Elegir mecanismo") says atomic types are appropriate only for a single simple variable — and explicitly warns against them "cuando hay una invariante entre varias variables". `WarehouseStatistics` has exactly that case: `processedParcels` and `totalProcessingMillis` must be updated together to stay consistent with each other, so a `synchronized` block around both was the correct choice over two independent `AtomicInteger`/`AtomicLong` fields (which would each be individually atomic, but not atomic *together*).

**What would happen to throughput if the protected region were unnecessarily large?**

If the protected region is unnecessarily large — for example a single global lock shared by all 4 classes — more robots have to wait for the same lock even when they could execute independently. A robot registering a delivery would block another robot that only wants to take a new parcel, even though `PackageQueue` and `DeliveryRegistry` protect independent invariants. This reduces concurrency and can decrease the throughput of the simulation without adding any extra correctness.

## 6. Thread completion and pause/resume coordination

**Why is `Thread.sleep(...)` not a valid substitute for `join()` when waiting for a worker to finish?**

`Thread.sleep(ms)` only guarantees that at least `ms` milliseconds have passed — it says nothing about whether the target thread actually finished its work. Since parcel processing time varies (randomized jitter, different robot/parcel counts), any fixed sleep duration is either too short (report printed while robots are still running, as in the starter) or wastefully too long. `join()` blocks the calling thread exactly until the target thread terminates, regardless of how long that takes, which is the only way to guarantee the final report is printed after all robots are truly done. `WarehouseMain` now calls `simulation.awaitCompletion()`, which loops `robot.join()` over every `WarehouseRobot`, instead of `Thread.sleep(60)`.

**Pause/resume design**

`SimulationControl` replaced the busy-wait (`while (paused) { Thread.onSpinWait(); }`) with a monitor:

- `pause()`, `resume()`, `awaitIfPaused()` and `isPaused()` are all `synchronized` on the same object monitor, since they all read/write the single shared `paused` flag.
- `awaitIfPaused()` uses `while (paused) { wait(); }` — a `while`, not an `if`, so a robot that wakes up (spuriously or otherwise) re-checks the condition before proceeding, as required for correct monitor usage.
- `resume()` sets `paused = false` and calls `notifyAll()` once, waking every robot blocked in `wait()` in a single coordinated action, instead of polling.

**Consistent paused snapshot — how do you know the snapshot represents a consistent state rather than workers that are still changing shared data?**

Once `pause()` is called, a robot that is mid-iteration finishes it completely — it processes the parcel it already took, calls `deliveryRegistry.register(...)`, and calls `statistics.recordProcessed(...)` — and only then, at the top of the next loop, does it call `awaitIfPaused()` and block. Because `register()` and `recordProcessed()` are themselves synchronized (sections above), any thread reading a snapshot while the simulation is paused can only observe the state either fully before or fully after one of these calls — never mid-update. So even though `pause()` can be called at an arbitrary instant, the snapshot taken shortly after is guaranteed to reflect a set of *complete* robot iterations, not a partially-written one.

## 7. Verification results

| Robots | Parcels | Runs | Anomalies before | Anomalies after |
|--------|---------|------|-------------------|-------------------|
| 8      | 100     | 100  | [COMPLETAR]       | [COMPLETAR]       |
| 16     | 250     | 100  | [COMPLETAR]       | [COMPLETAR]       |
| 32     | 500     | 100  | [COMPLETAR]       | [COMPLETAR]       |

Commands used:
```
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 8 100
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 16 250
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500
```

Run these **after** all three branches (`feature/vera-statistics`, `feature/nicolas-queue-registry`, `feature/mabel-pause-resume`) are merged into `main`. The "before" numbers come from Evidence 2 and 3 above (captured against the unfixed starter).

## 8. Quality-attribute analysis

### Decision analysis (main synchronization decision)
- **What problem were you solving?** Multiple `WarehouseRobot` threads reading and writing the same mutable state (`PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics`, `SimulationControl`) without coordination, producing lost updates, duplicate/missing parcels, and CPU-wasting busy-waiting.
- **What invariant had to be preserved?** I1 through I6 (section 4) — in particular, that compound read-modify-write operations (`nextPosition++`, `processedParcels++`, checking-then-removing from the queue) are observed as atomic by every other thread.
- **What alternatives did you consider?** A single global lock (rejected — see throughput question above), `AtomicInteger`/`AtomicLong` (rejected for `WarehouseStatistics` — see above), a private lock object per class (considered, not necessary — see above).
- **Why did you choose the final mechanism?** `synchronized` methods give each class its own independent monitor, matching the "Elegir mecanismo" decision map from the course material: multiple related variables → lock/synchronized around the invariant. `wait()`/`notifyAll()` was the required replacement for busy-waiting in `SimulationControl`.
- **What are its consequences?** See section 5's throughput answer and the Risks section of the ADR.

### Correctness / reliability
Each class now protects its own compound operations atomically, eliminating the specific races captured in section 2's evidence. Verified empirically via `RaceConditionProbe` (section 7).

### Performance / throughput
Synchronizing per-class instead of globally preserves parallelism between independent operations (e.g., one robot registering a delivery does not block another robot taking a new parcel). The critical sections themselves are short (a few field reads/writes), so lock contention is minimal even with many robots.

### Maintainability
Each shared class encapsulates its own synchronization — a developer reading `WarehouseStatistics` in isolation can reason about its thread-safety without needing to understand `PackageQueue` or `DeliveryRegistry`. This mirrors the course's guidance to protect the invariant at the smallest scope that owns it.

### Architectural boundary — three independent JVM instances behind a load balancer
Would `synchronized` blocks still protect the business invariant across all three instances? Why or why not?

No. `synchronized` protects a monitor that lives in a single JVM's memory. With 3 independent JVM instances, each would have its own copy of `PackageQueue`, `DeliveryRegistry`, etc. — three separate `nextPosition` counters, three separate `paused` flags. A lock held in instance A is invisible to instances B and C, so two robots on different instances could still be assigned the same delivery position or read a stale `pending` list. The invariants would break again, just at a coarser scale.

What type of architectural mechanism would then be required?

A form of distributed coordination: a shared data store that is the single source of truth for the mutable state (e.g. a relational database with transactions/constraints, or a distributed lock service), or a message queue with a single consumer per partition so that only one instance ever processes a given parcel. The key shift is that consistency can no longer be guaranteed purely in-process — it has to be delegated to a component all three instances agree to coordinate through.
