# ARSW — Lab #2
## Delivery Template — Autonomous Warehouse

**Course:** Software Architectures — ARSW  
**Period:** 2026-2  
**Lab:** #2 — Autonomous Warehouse  
**Topic:** Race Conditions · Critical Sections · Thread Coordination  
**Technology:** Java 21 · Maven · JUnit 5  

---

## 0. Team information

| Name | Student ID | GitHub |
|---|---|---|
| Juan Eduardo Vera Acero | 1000091871 | juanvera |
| Mabel Fernanda Bernal Amaya | 1000100629 | MabelBernalAmaya |
| Nicolás David Prieto Ramos | 1000091873 | NicolasPrieto12 |

**Repository:**  
`https://github.com/ARSW-LABORATORIOS/ARSW-LAB-2--Warehouse-Concurrency-Laboratory`

**Final commit:**  
`f0ff011`

---

# 1. Initial execution evidence

## 1.1 Environment check

Output of:

```bash
java -version
mvn -version
```

**Evidence:**

```text
openjdk version "21.0.11" 2025-04-15 LTS
OpenJDK Runtime Environment Microsoft-10891208 (build 21.0.11+10-LTS)
OpenJDK 64-Bit Server VM Microsoft-10891208 (build 21.0.11+10-LTS, mixed mode, sharing)

Apache Maven 3.9.16 (...)
Java version: 21.0.11, vendor: Microsoft
```

---

## 1.2 First run

Command used:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain
```

or with arguments:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain <robots> <packages>
```

**Configuration used:**

- Robots: 12
- Packages: 100

**What we saw:**

```text
Starting warehouse with 12 robots and 100 parcels...
[warehouse-robot-12] Queue anomaly: IndexOutOfBoundsException
STARTER REPORT (intentionally premature)
Initial parcels : 100
Pending parcels : 3
Processed count : 94
Registry size   : 97
```

---

# 2. Shared mutable state

Objects and variables shared between multiple threads.

| Object / Class | Shared mutable state | Who reads | Who writes | Identified risk |
|---|---|---|---|---|
| `PackageQueue` | `pending` list of packages | Robots and snapshot | Robots using `takeNext()` | Two robots can take the same package at the same time |
| `DeliveryRegistry` | `deliveries` list and `nextPosition` | Snapshot and verification | Robots using `register()` | Two robots can get the same delivery position |
| `WarehouseStatistics` | `processedParcels` and `totalProcessingMillis` | Snapshot and report | Robots using `recordProcessed()` | Lost increments because read-modify-write is not atomic |
| `SimulationControl` | `paused` flag | All robots | `pause()` and `resume()` | A paused robot can keep consuming packages; busy-wait wastes CPU |

---

# 3. Race conditions found

## Race Condition #1

**Class / method:**  
`PackageQueue.takeNext()`

**Shared state involved:**  
`List<Parcel> pending`

**What happened:**  
`IndexOutOfBoundsException` at runtime. A robot tried to read or remove an element that another robot had already removed.

**Why does it happen?**  
The `isEmpty()` check, the `get(0)` and the `remove(0)` are three separate steps. Between the check and the remove, another robot can remove the last element, so the list is empty by the time the first robot tries to remove.

**Execution evidence:**

```text
[warehouse-robot-12] Queue anomaly: IndexOutOfBoundsException
```

---

## Race Condition #2

**Class / method:**  
`WarehouseStatistics.recordProcessed()`

**Shared state involved:**  
`int processedParcels`

**What happened:**  
The `processedParcels` counter was lower than the number of deliveries in the registry.

**Why does it happen?**  
`processedParcels++` is not atomic. The JVM breaks it into three steps: read the value, add 1, write it back. If two robots read the same value before either one writes, one increment gets lost.

**Execution evidence:**

```text
processedCounter=242, registry=245
```

---

## Race Condition #3

**Class / method:**  
`DeliveryRegistry.register()`

**Shared state involved:**  
`int nextPosition`, `List<DeliveryRecord> deliveries`

**What happened:**  
Two deliveries got the same position. The positions did not form a continuous sequence.

**Why does it happen?**  
Reading `nextPosition`, incrementing it and calling `add` are three separate steps. Two robots can read the same `nextPosition` before either one increments it, so both deliveries get the same position.

**Execution evidence:**

```text
registry=245, uniquePositions=232, positionsContiguous=false
```

---

# 4. Interleaving

**Selected condition:**  
`WarehouseStatistics.recordProcessed()` — lost update on `processedParcels`

| Step | Thread A | Thread B | Shared state |
|---:|---|---|---|
| 1 | Reads `processedParcels = 10` | | `processedParcels = 10` |
| 2 | | Reads `processedParcels = 10` | `processedParcels = 10` |
| 3 | Calculates `10 + 1 = 11` | | `processedParcels = 10` |
| 4 | | Calculates `10 + 1 = 11` | `processedParcels = 10` |
| 5 | Writes `processedParcels = 11` | | `processedParcels = 11` |
| 6 | | Writes `processedParcels = 11` | `processedParcels = 11` |

### Explanation

Why does this execution order produce a wrong result?

**Answer:**

Both robots processed a package, so the counter should end up at 12. But since both read the same value (10) before either one wrote, both calculated 11 and the final result is 11 instead of 12. One increment was lost. The result depends on scheduling because if the scheduler had let Thread A finish completely before Thread B read the value, the result would be correct. We have no control over that order.

---

# 5. System invariants

## I1

`Every package must be processed exactly once. It cannot be taken by two robots at the same time and it cannot disappear from the system.`

## I2

`No package can disappear from the system. The sum of pending and delivered packages must always equal the initial total.`

## I3

`Every delivery position must be unique. No two deliveries can have the same position.`

## I4 — optional

`Delivery positions must form a continuous sequence from 1 to N with no gaps. The processed counter must match the number of delivery records.`

---

# 6. Critical regions

| Class | Critical region | Protected invariant | Mechanism used | Why this size? |
|---|---|---|---|---|
| `PackageQueue` | `isEmpty()` + `get(0)` + `remove(0)` inside `takeNext()`, and `size()` in `pendingCount()` | I1, I2 | `synchronized` on both methods | If we only synchronized the `remove` and not the check+get together, another robot could get in between those steps. The whole check-then-act sequence has to be atomic. |
| `DeliveryRegistry` | Reading `nextPosition`, incrementing it and `deliveries.add()` inside `register()`; iterating `deliveries` in `snapshot()` | I3, I4 | `synchronized` on `register()` and `snapshot()` | Splitting the read from the increment would let two robots read the same `nextPosition`. `snapshot()` is also synchronized so we don't read a list that is being written at the same time. |
| `WarehouseStatistics` | Updating `processedParcels` and `totalProcessingMillis` in `recordProcessed()`; reads in the getters | I4 (counter == records) | `synchronized` on all three methods | Both fields need to update together. Two separate `AtomicInteger` values would not guarantee that both are updated as one single operation. |
| `SimulationControl` | Read/write of `paused` in `pause()`, `resume()`, `awaitIfPaused()`, `isPaused()` | I1 indirectly (a paused robot must not keep consuming packages) | `synchronized` + `wait()` / `notifyAll()` | All four methods touch the same boolean and need to coordinate on the same monitor so that `wait()`/`notifyAll()` work correctly. |

---

# 7. Synchronization decisions

## 7.1 Alternatives considered

- [x] `synchronized`
- [ ] `AtomicInteger`
- [ ] Concurrent collections
- [ ] `Lock`
- [x] `wait()` / `notifyAll()`
- [ ] Other: `________________________`

### Alternative 1

**Description:**  
`AtomicInteger` / `AtomicLong` for the counters in `WarehouseStatistics`.

**Advantage:**  
They are lock-free operations. For a single counter they are faster than `synchronized`.

**Disadvantage:**  
`WarehouseStatistics` has two fields (`processedParcels` and `totalProcessingMillis`) that need to update together. Two separate `Atomic*` values do not guarantee that both update as one atomic operation, so the invariant "counter == records" could break between the two writes.

### Alternative 2

**Description:**  
One global lock shared across all classes (`PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics`, `SimulationControl`).

**Advantage:**  
Simple to implement. One lock object, impossible to forget to synchronize something.

**Disadvantage:**  
It kills parallelism for no reason. A robot registering a delivery would block another robot that just wants to take a new package, even though those two operations do not share any data.

### Final decision

**Selected mechanism:**  
`synchronized` at method level in each class, using its own monitor (`this`). For `SimulationControl`, also `wait()` / `notifyAll()`.

**Justification:**  
`synchronized` per class is the right level of granularity: small enough to not block unrelated operations, and big enough to cover the full invariant of each class. `wait()`/`notifyAll()` in `SimulationControl` is the only way to make a robot wait without burning CPU, because `wait()` releases the monitor while sleeping and `notifyAll()` wakes all robots at once when `resume()` is called.

---

# 8. Thread completion

How we made sure the final report only prints when all robots are done.

**Mechanism used:**  
`simulation.awaitCompletion()`, which calls `robot.join()` for every robot.

**Explanation:**  
`WarehouseMain` used to call `Thread.sleep(60)` before printing the report. That only guarantees that 60 ms passed, not that the robots finished. We replaced it with `simulation.awaitCompletion()`, which calls `join()` on every robot thread. `join()` blocks until that specific thread finishes, no matter how long it takes. The report only prints once all `join()` calls return, meaning all robots are actually done.

### Question

Why is `Thread.sleep(...)` not a correct solution for waiting for all workers to finish?

**Answer:**  
`Thread.sleep(ms)` only guarantees that time passed, not that the robots finished. If there are more robots or packages than expected, the sleep can end before the robots are done and the report prints with incomplete data. Also, if the robots finish before the sleep ends, we wait for no reason. `join()` is deterministic: it blocks exactly until the thread finishes, no matter how long that takes.

---

# 9. PAUSE / RESUME

## 9.1 Initial problem

Why the busy-wait in the original code is not a good solution.

**Answer:**  
The original code used `while (paused) { Thread.onSpinWait(); }`. This makes every paused robot run an empty loop over and over, using CPU time without doing any real work. With 12 or more robots paused, all of them compete for CPU for no reason. `Thread.onSpinWait()` is just a hint to the processor and does not release the scheduler, so paused robots are still considered active and keep consuming system resources.

---

## 9.2 Our solution

How we implemented:

- `pause()`
- workers waiting
- `resume()`
- coordinated wake-up of workers

**Answer:**  
- `pause()`: a `synchronized` method that sets `paused = true`. Because it is synchronized, only one thread can change the variable at a time.
- Workers waiting: `awaitIfPaused()` is `synchronized` and uses `while (paused) { wait(); }`. We use `while` instead of `if` to handle spurious wakeups: the robot checks the condition again before continuing. `wait()` releases the monitor and puts the thread to sleep without using CPU.
- `resume()`: a `synchronized` method that sets `paused = false` and calls `notifyAll()`.
- Coordinated wake-up: `notifyAll()` wakes all robots that were in `wait()` at the same time. Each one checks `while (paused)` again and, since it is now `false`, continues running.

---

## 9.3 Consistent snapshot

When the simulation is paused:

```text
Processed parcels: 47
Pending parcels:   53
Registry size:     47
Current leader:    Robot-03 / parcel 12 / position 1
```

How we guarantee those values represent a consistent state.

**Answer:**  
When `pause()` is called, a robot that is already in the middle of an iteration does not stop right away. It finishes processing the current package, calls `register()` and `recordProcessed()`, and only then reaches `awaitIfPaused()` and goes to sleep. Since `register()` and `recordProcessed()` are `synchronized`, anyone reading the snapshot while paused can only see the state before or after one of those calls finished, never in the middle of a write. The snapshot is always a picture of robots that finished their current step, not one caught halfway through an update.

---

# 10. Verification with RaceConditionProbe

Command:

```bash
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500
```

## Results

| Robots | Packages | Runs | Anomalies before | Anomalies after |
|---:|---:|---:|---:|---:|
| 8 | 100 | 100 | see Evidence 1-3 (broken starter) | 0 |
| 16 | 250 | 100 | see Evidence 1-3 (broken starter) | 0 |
| 32 | 500 | 100 | see Evidence 1-3 (broken starter) | 0 |

### Expected final result

```text
Anomalous runs: 0/100
```

**Output we got:**

```text
Running 100 simulations with 8 robots and 100 parcels...
Anomalous runs: 0/100  ✓

Running 100 simulations with 16 robots and 250 parcels...
Anomalous runs: 0/100  ✓

Running 100 simulations with 32 robots and 500 parcels...
Anomalous runs: 0/100  ✓
```

---

# 11. Correctness evidence

Brief explanation of how we show our solution is correct.

**Conclusion:**

The invariants I1–I4 are checked automatically by `InvariantChecker` in every run of `RaceConditionProbe`. In 300 total runs (100 per load configuration), none of them produced anomalies: no duplicate packages, no repeated positions, the processed counter matched the registry size, and no packages were left pending at the end. Consistency during pause is guaranteed because robots finish their current step before going to sleep, and `register()`/`recordProcessed()` are atomic. Correct completion is guaranteed with `join()` instead of `Thread.sleep()`. The 2/2 tests in `InvariantCheckerTest` pass with `mvn clean test`.

---

# 12. Impact on quality attributes

| Attribute | Impact of our solution | Evidence / metric |
|---|---|---|
| Correctness / Reliability | Better: the invariants for unique positions and consistent counters are now guaranteed | 0/100 anomalies in RaceConditionProbe; 2/2 tests pass |
| Performance / Throughput | Slightly lower: robots wait their turn to enter critical regions, but the wait is short because each class has its own monitor | Critical sections are only a few lines; robots in different classes do not block each other |
| Maintainability | Better: each class handles its own synchronization; the protected region is clear and justified | No hidden locks or unnecessary synchronization |
| Scalability | Acceptable inside one JVM: more robots only increase contention in small critical sections. Does not scale to multiple JVMs | See section 14 for the multi-instance analysis |

---

# 13. Main trade-off

What we gained and what we gave up by adding synchronization.

**Answer:**

We gained deterministic correctness: the system invariants hold in every run, no matter what order the scheduler picks for the threads. Packages are not duplicated, positions are unique and the final report is always correct.

We gave up some parallelism: when two robots want to access the same object at the same time, one has to wait. But by using one monitor per class instead of one global lock, we kept contention low. A robot registering a delivery does not block another robot that just wants to pick up a new package.

---

# 14. Architectural analysis

Suppose there are now three instances of the application:

```text
                 Load Balancer
                       |
            +----------+----------+
            |          |          |
          App A      App B      App C
            \          |          /
                    Database
```

## 14.1 Question

Do the `synchronized` blocks inside one JVM guarantee consistency between `App A`, `App B` and `App C`?

- [ ] Yes
- [x] No

**Justification:**

`synchronized` only protects memory inside one JVM. Each instance has its own copy of `nextPosition`, `paused`, `pending`, etc. in its heap. A lock in App A has no effect on what App B or App C are doing at the same time. Two robots running on different instances could read the same `nextPosition` from their own local copies and assign the same delivery position. It is exactly the same problem we had between threads, but now between processes.

---

## 14.2 Architectural evolution

What alternative would we use to guarantee consistency between multiple instances?

- [x] Database transaction
- [x] Database constraint
- [ ] Optimistic locking / versioning
- [ ] Distributed lock
- [ ] Other: `________________________`

**Proposed decision:**

Move the shared state (`nextPosition`, counters, package queue) to a shared database and use transactions with uniqueness constraints.

**Justification:**

A database with a `UNIQUE` constraint on the position column guarantees that two instances cannot insert the same position, no matter which JVM they run on. Transactions guarantee that the read-increment-write of `nextPosition` is atomic at the database level. This moves the consistency guarantee to a layer that all instances share, which is exactly what `synchronized` cannot do across JVMs.

---

# 15. Mini ADR

## ADR-001 — Concurrency control for warehouse shared state

### Context

In the warehouse simulator, several robots (Java threads) share four objects: `PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics` and `SimulationControl`. The original code had no protection on these objects, which caused race conditions: two robots could take the same package, get the same delivery position, lose counter increments, or burn CPU in active waiting. The goal was to fix those problems with the minimum necessary synchronization, without removing concurrency or using a global lock.

### Decision

We used `synchronized` at method level in each class separately. In `PackageQueue`, `takeNext()` and `pendingCount()` were synchronized so the check, read and remove happen as one atomic operation. In `DeliveryRegistry`, `register()` and `snapshot()` were synchronized so the read of `nextPosition`, the increment and the `add` cannot be interrupted by another robot. In `WarehouseStatistics`, all three methods were synchronized so both fields update together. In `SimulationControl`, we replaced the busy-wait with `wait()`/`notifyAll()` so robots sleep without using CPU. Each class uses its own monitor (`this`), independent from the others.

### Alternatives considered

1. One global lock shared across all classes: rejected because it would block robots working on unrelated data, reducing throughput for no reason.
2. `AtomicInteger`/`AtomicLong` for the counters: works for a single counter, but `WarehouseStatistics` has two fields that need to update together, and `DeliveryRegistry` needs atomicity across three steps. One `Atomic*` per variable does not cover those cases.

### Quality attributes affected

Correctness improves: the invariants for unique positions and consistent counters are guaranteed. Performance has a small reduction due to contention in critical regions, minimized by using one monitor per class. Maintainability improves: each class encapsulates its own synchronization with clear and justified critical regions.

### Evidence

`RaceConditionProbe` returns 0/100 anomalies in all three required configurations (8/100, 16/250, 32/500). `mvn clean test` passes with BUILD SUCCESS, 2/2 tests. No `IndexOutOfBoundsException`, duplicate positions or out-of-sync counters after the fixes.

### Consequences

Each class protects its own invariants independently. There is no global lock, so robots working on different classes do not block each other. The public behavior of each class did not change: same method signatures, same semantics. Correctness no longer depends on the OS scheduler.

### Risks

If logic is added in the future that needs atomicity across two different classes, the separate monitors will not be enough and the design will need to be revisited. If someone adds a new method that touches these fields without going through the synchronized methods, the protection breaks and the compiler will not warn about it. In a scenario with multiple JVM instances behind a load balancer, `synchronized` does not protect anything across separate processes.

---

# 16. Changes made

| File / Class | Change | Reason |
|---|---|---|
| `PackageQueue.java` | `takeNext()` and `pendingCount()` marked as `synchronized` | Remove the check-then-act race condition: isEmpty+get+remove must be atomic |
| `DeliveryRegistry.java` | `register()` and `snapshot()` marked as `synchronized`; explicit increment of `nextPosition` | Remove the lost-update on `nextPosition` and guarantee unique consecutive positions |
| `WarehouseStatistics.java` | `recordProcessed()`, `processedParcels()` and `totalProcessingMillis()` marked as `synchronized` | Make sure both fields update together and reads are consistent |
| `SimulationControl.java` | Replaced busy-wait with `synchronized` + `wait()`/`notifyAll()` on all 4 methods | Remove active waiting; robots sleep without using CPU and wake up in a coordinated way |
| `WarehouseMain.java` | Replaced `Thread.sleep(60)` with `simulation.awaitCompletion()` (which calls `join()` per robot) | Make sure the final report only prints when all robots have actually finished |

---

# 17. Tests run

| Test | Command | Result |
|---|---|---|
| Compile and tests | `mvn clean test` | BUILD SUCCESS — 2/2 tests passed |
| Standard simulation | `java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain 12 100` | Correct final report, 0 pending packages, counter == registry size |
| RaceConditionProbe 8/100 | `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 8 100` | 0/100 anomalous runs |
| RaceConditionProbe 16/250 | `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 16 250` | 0/100 anomalous runs |
| RaceConditionProbe 32/500 | `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500` | 0/100 anomalous runs |
| Pause / Resume | `java -cp target/classes edu.eci.arsw.warehouse.app.PauseResumeDemo` | Pauses and resumes correctly; consistent snapshot while paused |

---

# 18. Conclusions

1. Race conditions are not visible in the source code. The starter looked reasonable but produced IndexOutOfBoundsException, duplicate positions and out-of-sync counters because multi-step operations were not atomic.
2. `synchronized` at method level per class is the right level of granularity for this problem. It protects each invariant without blocking unrelated operations in different classes.
3. `wait()`/`notifyAll()` is the only correct way to implement pause/resume without active waiting. The thread releases the monitor while sleeping and wakes up exactly when `resume()` tells it to, without using CPU.
4. `join()` is the only correct way to wait for workers to finish. `Thread.sleep()` only guarantees that time passed, not that the threads are done, which produces early reports with incomplete data.
5. `synchronized` only protects inside one JVM. In a distributed scenario with multiple instances, consistency must move to a shared layer like a database with transactions and constraints, because no shared-memory mechanism can work across processes.

---

# 19. Delivery checklist

- [x] The project compiles with `mvn clean test`.
- [x] The code uses Java 21.
- [x] Concurrency was not removed.
- [x] There is no busy waiting in the final solution.
- [x] The program correctly waits for all robots to finish.
- [x] Critical regions are justified.
- [x] The defined invariants are preserved.
- [x] The final `RaceConditionProbe` shows no anomalies.
- [x] The architectural analysis is documented.
- [x] The ADR is included.
- [x] The repository has clear commits.
- [x] The repository URL and final commit are included.

---

## Note

The amount of text is not what is graded. What matters is showing:

> **problem → evidence → invariant → critical region → decision → implementation → verification → architectural trade-off**
