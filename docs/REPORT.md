# Laboratory 2 Report

Team: Mabel · Nicolás · Vera

## 1. Shared-state inventory

| Shared object         | Mutable state                                | Readers                 | Writers                           | Possible invariant                                   |
|------------------------|----------------------------------------------|--------------------------|-----------------------------------|--------------------------------------------------------|
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
and give a different result. The code doesn't change between runs, only
the order the scheduler picks to run the threads, and we don't control
that order.

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

| Class                  | Critical region                                                                                                        | Protected invariant                                                                                              | Synchronization mechanism                         | Why this granularity?                                                                                                                                                                                                                                    |
|--------------------------|---------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `WarehouseStatistics`   | Update and read of `processedParcels` and `totalProcessingMillis`                                                       | I5                                                                                                                     | `synchronized` on the object monitor (methods)         | Only the methods that access the shared mutable statistics are synchronized, to avoid a global lock or synchronizing unrelated operations                                                                                                              |
| `PackageQueue`          | El chequeo `isEmpty()`, la lectura y el `remove(0)` dentro de `takeNext()`, y la lectura del tamaño en `pendingCount()` | I1 — un paquete no puede ser tomado por dos robots al mismo tiempo ni desaparecer de la lista sin haber sido entregado | `synchronized` en los dos métodos que acceden a `pending` | Se sincronizaron solo los métodos que leen o modifican la lista. El constructor no necesita sincronización porque en ese momento todavía no hay robots corriendo. Si se hubiera sincronizado solo el `remove` y no el `isEmpty` con el `get`, igual se podría colar otro robot entre esas dos operaciones, por eso toda la secuencia tiene que ser atómica |
| `DeliveryRegistry`      | La lectura de `nextPosition`, su incremento y el `deliveries.add()` dentro de `register()`, y la iteración de `deliveries` en `snapshot()` | I3 / I4 — las posiciones de llegada deben ser únicas y formar la secuencia `1..N` sin huecos                         | `synchronized` en `register()` y `snapshot()`         | Se protegió todo `register()` junto porque separar la lectura del incremento del add no tiene sentido, si otro robot entra entre esas líneas se lleva la misma posición. `snapshot()` también se sincronizó porque si un robot está a mitad de `register()` cuando otro llama `snapshot()`, la copia puede quedar inconsistente. Cada clase usa su propio monitor, independiente del de `PackageQueue`, para no bloquear robots que solo quieren pedir un paquete mientras otro está registrando una entrega |
| `SimulationControl`     | Read/write of `paused`, and the wait/notify coordination in `awaitIfPaused()`                                          | I6 (indirectly — pausing must not let robots keep consuming parcels)                                                 | `synchronized` methods + `wait()` / `notifyAll()`      | All 4 methods (`pause`, `resume`, `awaitIfPaused`, `isPaused`) synchronize on the same monitor (`this`) because they all read/write the same single boolean; `wait()`/`notifyAll()` require that shared monitor to coordinate blocking without polling |

**Why `synchronized` methods instead of a private lock object?**

In class we saw that a private `Object lock` is usually the safer option, because putting `synchronized` directly on a method uses the object itself (`this`) as the monitor, and that's the "exponer el lock" antipattern the slides warn about. We thought about it but decided it doesn't really apply here: `PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics` and `SimulationControl` are internal classes, nobody outside `WarehouseRobot`/`WarehouseSimulation` ever gets a reference to them, so there's no risk of someone accidentally locking on our object from outside. If these classes were ever made public we'd switch to a private lock, but for this lab `synchronized` on the methods does the same job with less code.

**Why not `AtomicInteger`/`AtomicLong` for `WarehouseStatistics`?**

We considered it since `processedParcels` is just a counter, but `WarehouseStatistics` actually has two fields that need to stay in sync with each other (`processedParcels` and `totalProcessingMillis`). Two separate `AtomicInteger`/`AtomicLong` would each be safe on their own, but nothing stops one thread from updating one field and being interrupted before updating the other — so the two numbers could drift apart. That's exactly the case the course material says atomics aren't enough for ("varias variables relacionadas"), so we went with `synchronized` around both updates instead.

**What would happen to throughput if the protected region were unnecessarily large?**

If we had used one single lock for all 4 classes, robots would end up waiting on each other for no reason — a robot registering a delivery would block another robot that's just trying to take a new parcel, even though those two operations don't touch the same data. Keeping each class with its own lock means robots only wait when they actually compete for the same thing.

## 6. Thread completion and pause/resume coordination

**Why is `Thread.sleep(...)` not a valid substitute for `join()` when waiting for a worker to finish?**

`Thread.sleep(ms)` just waits `ms` milliseconds and then keeps going, it doesn't actually know if the robots are done or not. That's the bug in the starter: it sleeps 60ms and prints the report, but 60ms might not be enough if there are more robots/parcels, so the report comes out wrong. `join()` is different because it actually blocks until that specific thread finishes, no matter how long it takes. So we changed `WarehouseMain` to call `simulation.awaitCompletion()` (which does `robot.join()` for every robot) instead of `Thread.sleep(60)`, and now the report only prints once everyone is actually done.

**Pause/resume design**

We replaced the busy-wait in `SimulationControl` (`while (paused) { Thread.onSpinWait(); }`) with `wait()`/`notifyAll()`:

- `pause()`, `resume()`, `awaitIfPaused()` and `isPaused()` are all `synchronized`, because they all touch the same `paused` variable and we need only one robot messing with it at a time.
- `awaitIfPaused()` uses `while (paused) { wait(); }` instead of `if`, because a thread can wake up from `wait()` without anyone actually calling `resume()` (spurious wakeup), so it needs to check the condition again before continuing.
- `resume()` sets `paused = false` and calls `notifyAll()` so every robot that was sleeping wakes up at once, instead of us having to wake them one by one.

**Consistent paused snapshot — how do you know the snapshot represents a consistent state rather than workers that are still changing shared data?**

When `pause()` gets called, a robot that's already in the middle of an iteration doesn't stop right away — it finishes processing the parcel it has, registers the delivery, updates the statistics, and only after that does it check `awaitIfPaused()` and go to sleep. Since `register()` and `recordProcessed()` are synchronized, whoever reads the snapshot while paused can only see the state before or after one of those calls finished, never halfway through. So the paused snapshot is always a picture of robots that finished their current step, not one caught mid-write.

## 7. Verification results

| Robots | Parcels | Runs | Anomalies before | Anomalies after |
|--------|---------|------|-------------------|-------------------|
| 8      | 100     | 100  | not swept at this exact config — see Evidence 1–3 for anomalies observed on the unfixed starter | 0/100 |
| 16     | 250     | 100  | not swept at this exact config — see Evidence 1–3 for anomalies observed on the unfixed starter | 0/100 |
| 32     | 500     | 100  | not swept at this exact config — see Evidence 1–3 for anomalies observed on the unfixed starter | 0/100 |

Commands used:
```
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 8 100
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 16 250
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500
```

Ran these after all three branches were merged into `main`. All three configurations came back with **0 anomalous runs out of 100**, which matches the target the assignment asks for. We didn't run a full 100-run sweep of these exact configurations on the unfixed starter before merging (the qualitative evidence in Section 2 — captured while the bugs were still present — already showed the specific anomalies), so the "before" column points back to that evidence instead of a repeated count.

## 8. Quality-attribute analysis

### Decision analysis (main synchronization decision)
- **What problem were you solving?** All the `WarehouseRobot` threads were reading and writing the same shared objects (`PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics`, `SimulationControl`) with no coordination at all, so we were getting lost updates, duplicated/missing parcels, and robots burning CPU while paused instead of actually waiting.
- **What invariant had to be preserved?** Basically I1 to I6 from section 4 — things like a parcel only counting once, positions not repeating, and the processed counter actually matching how many deliveries happened.
- **What alternatives did you consider?** A single lock for everything (bad idea, kills throughput for no reason), `AtomicInteger`/`AtomicLong` for the statistics (doesn't work because two fields need to update together), and a private lock object instead of `synchronized` methods (not necessary since nothing external touches these classes).
- **Why did you choose the final mechanism?** `synchronized` on each class separately felt like the right level — small enough to not block unrelated work, big enough to actually cover the invariant. For the pause/resume part specifically we needed `wait()`/`notifyAll()` because that's the only way to make a thread sleep and wake back up without polling.
- **What are its consequences?** See the throughput answer in section 5 and the Risks part of the ADR.

### Correctness / reliability
Every class protects its own read-modify-write operations now, so the specific races we captured in section 2 shouldn't happen anymore. We're checking this with `RaceConditionProbe` (results in section 7).

### Performance / throughput
Because we locked each class separately instead of using one big lock, robots can still work in parallel as long as they're not touching the exact same object. The locked sections themselves are tiny (just a few field updates), so we don't expect robots to spend much time waiting on each other even with more robots running.

### Maintainability
Each class handles its own synchronization, so if someone needs to touch `WarehouseStatistics` later they don't have to understand how `PackageQueue` or `DeliveryRegistry` work to know it's safe.

### Architectural boundary — three independent JVM instances behind a load balancer
Would `synchronized` blocks still protect the business invariant across all three instances? Why or why not?

No, it wouldn't work. `synchronized` only protects memory inside one JVM — if we had 3 separate instances, each one would have its own copy of `nextPosition`, `paused`, etc. A lock in instance A has no idea what's happening in instance B or C, so two robots running on different instances could still grab the same delivery position. Basically the same problem comes back, just between instances instead of between threads.

What type of architectural mechanism would then be required?

We'd need something outside any single JVM to be the actual source of truth — like a database with transactions/constraints, a distributed lock, or a queue that only lets one instance process a given parcel at a time. The point is that `synchronized` can't reach across machines, so the coordination has to move somewhere all three instances can see.
