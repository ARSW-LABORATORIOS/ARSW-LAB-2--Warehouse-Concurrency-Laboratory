# Laboratory 2 Report

## 1. Shared-state inventory

| Shared object         | Mutable state                                | Readers                 | Writers                           | Possible invariant                                   |
|-----------------------|----------------------------------------------|-------------------------|-----------------------------------|------------------------------------------------------|
| `PackageQueue`        | Lista `pending` de paquetes                  | Robots y snapshot       | Robots usando `takeNext()`        | Un paquete no debe ser tomado más de una vez         |
| `DeliveryRegistry`    | Lista de entregas y `nextPosition`           | Snapshot y verificación | Robots usando `register()`        | Las posiciones deben ser únicas y consecutivas       |
| `WarehouseStatistics` | `processedParcels` y `totalProcessingMillis` | Snapshot y reporte      | Robots usando `recordProcessed()` | El contador procesado debe coincidir con el registro |
| `SimulationControl`   | Bandera `paused`                             | Todos los robots        | `pause()` y `resume()`            | Un robot pausado no debe seguir avanzando            |

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
robots updated the shared counter concurrently.

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

| Step  | Thread A                       |  Thread B                      | Shared state            |
|-------|--------------------------------|--------------------------------|-------------------------|
| 1     | Reads `processedParcels = 10`  |                                | `processedParcels = 10` |
| 2     |                                | Reads `processedParcels = 10`  | `processedParcels = 10` |
| 3     | Writes `processedParcels = 11` |                                | `processedParcels = 11` |
| 4     |                                | Writes `processedParcels = 11` | `processedParcels = 11` |

Why is the final result dependent on scheduling?

The final result depends on scheduling because the threads can execute
their read and write operations in different orders. If two robots read
the same value before one of them updates it, one increment can be lost.
Also if we make another execution the threads can run in another order
and give a different result.


## 4. System invariants

| Candidate invariant                                                    | Classification   |
|------------------------------------------------------------------------|------------------|
| Every parcel is processed at most once                                 | Required         |
| No parcel disappears from the system                                   | Required         |
| Arrival positions are unique                                           | Required         |
| Arrival positions form a valid sequence from `1..N`                    | Required         |
| The processed counter matches the number of delivery records           | Required         |
| When the simulation is reported as complete, no parcels remain pending | Required         |

Final invariants:

I1: Every parcel must be processed at most once.

I2: No parcel can disappear from the system.

I3: Every arrival position must be unique.

I4: Arrival positions must form a continuous sequence from `1` to `N`.

I5: The processed counter must match the number of delivery records.

I6: When the simulation is complete, there must be no pending parcels.

## 5. Critical regions and synchronization decisions

| Class                 | Critical region                                                   | Protected invariant                                                                           | Synchronization mechanism               | Why this granularity?                                                                                                                                 |
|-----------------------|-------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `WarehouseStatistics` | Update and read of `processedParcels` and `totalProcessingMillis` | The processed counter must represent the processed operations and remain consistent when read | `synchronized` using the object monitor | Only the methods that access the shared mutable statistics are synchronized, this to not get a global lock or synchronization of unrelated operations |

**What would happen to throughput if the protected region were unnecessarily large?**

R/ If the protected region is unnecessarily large, 
more robots have to wait for the same lock even when 
they could execute independently. This reduces concurrency 
and can decrease the throughput of the simulation.

## 6. Thread completion and pause/resume coordination

## 7. Verification results

## 8. Quality-attribute analysis
