# ADR-001: Concurrency control for warehouse shared state

## Context

The warehouse simulation runs N `WarehouseRobot` threads concurrently against 4 shared mutable objects (`PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics`, `SimulationControl`). The starter implementation leaves every read-modify-write operation unsynchronized, producing check-then-act races (`PackageQueue.takeNext()`), lost-position races (`DeliveryRegistry.register()`), lost-update races (`WarehouseStatistics.recordProcessed()`), and CPU-wasting busy-waiting for pause/resume (`SimulationControl.awaitIfPaused()`).

## Decision

[COMPLETAR] — resumir en 3-4 líneas: se usó `synchronized` a nivel de método sobre cada clase compartida individualmente (no un candado global), con la región crítica limitada a la operación de lectura+escritura que causa la carrera en cada caso; y se reemplazó la espera activa de `SimulationControl` por un monitor con `wait()`/`notifyAll()`.

## Alternatives considered

- **One global lock for all 4 shared objects** — rejected: serializes unrelated operations (e.g. taking a parcel would block on delivery registration), unnecessarily hurting throughput.
- **Private lock object + `synchronized` block** (class material, "Solución 2: bloque sincronizado") — instead of `synchronized` on the public methods (which uses the object itself, `this`, as the monitor), declare `private final Object lock = new Object();` and wrap only the critical section in `synchronized (lock) { ... }`. This avoids the "exposing the lock" antipattern flagged in class: external code holding a reference to the object could otherwise `synchronized (thatObject)` and interfere with unrelated code. Not adopted here because `PackageQueue`, `DeliveryRegistry`, and `WarehouseStatistics` are internal domain objects only ever referenced by `WarehouseRobot`/`WarehouseSimulation` within the same trusted codebase — nothing external ever holds a reference to lock on. `synchronized` methods ("Solución 1") give the same mutual-exclusion guarantee with less code. If these classes were ever exposed as a public API, switching to a private lock would be the safer default.
- **`java.util.concurrent` primitives** (e.g. `ConcurrentLinkedQueue`, `AtomicInteger`, `ReentrantLock`/`Condition`) — [COMPLETAR: ¿lo consideraron y por qué se quedaron con `synchronized` + monitores intrínsecos en vez de esto? Mencionar que el "Desafío opcional" del enunciado explora justamente esta alternativa con `BlockingQueue`/`Lock`/`Condition`].
- **Busy-waiting kept as-is** — rejected: wastes CPU cycles while paused, explicitly disallowed by the assignment.

## Quality attributes affected

- **Correctness/reliability**: eliminates the race conditions verified by `RaceConditionProbe` (target: 0/100 anomalous runs).
- **Performance/throughput**: [COMPLETAR — cuantificar si es posible, o al menos razonar: sincronizar por clase en vez de globalmente mantiene el paralelismo entre operaciones independientes].
- **Maintainability**: [COMPLETAR — cada clase encapsula su propia sincronización, así que el comportamiento concurrente es local y fácil de razonar por separado].

## Evidence

See `docs/REPORT.md` sections 2 (Observed anomalies) and 7 (Verification results) for the before/after `RaceConditionProbe` runs across 3 configurations (8/100, 16/250, 32/500 robots/parcels).

## Consequences

[COMPLETAR] — ej.: todas las operaciones sobre un mismo objeto compartido quedan serializadas entre sí (aceptable, porque son operaciones cortas), pero el sistema sigue permitiendo paralelismo real entre robots que tocan objetos distintos en el mismo instante.

## Risks

- If a future change adds a new method that reads/writes the same fields without going through the synchronized methods, the invariant breaks silently (no compiler error).
- `wait()`/`notifyAll()` on `SimulationControl` requires callers to always hold the object's monitor; calling `awaitIfPaused()`/`pause()`/`resume()` from code that doesn't go through the synchronized methods would reintroduce the race.
- [COMPLETAR — algún otro riesgo que identifiquen específico de su implementación].
