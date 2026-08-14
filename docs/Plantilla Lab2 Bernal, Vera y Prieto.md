# ARSW — Laboratorio #2
## Plantilla de entrega — Autonomous Warehouse

**Asignatura:** Arquitecturas de Software — ARSW  
**Periodo:** 2026-2  
**Laboratorio:** #2 — Autonomous Warehouse  
**Tema:** Race Conditions · Critical Sections · Thread Coordination  
**Tecnología:** Java 21 · Maven · JUnit 5  

---

## 0. Información del equipo

| Integrante | Código / ID | GitHub |
|---|---|---|
| Mabel | | |
| Vera | | |
| Nicolás | | |

**Repositorio:**  
`https://github.com/ARSW-LABORATORIOS/ARSW-LAB-2--Warehouse-Concurrency-Laboratory`

**Commit final:**  
`9a86263`

---

# 1. Evidencia de ejecución inicial

## 1.1 Verificación del entorno

Incluya la salida de:

```bash
java -version
mvn -version
```

**Evidencia:**

```text
openjdk version "21.0.11" 2025-04-15 LTS
OpenJDK Runtime Environment Microsoft-10891208 (build 21.0.11+10-LTS)
OpenJDK 64-Bit Server VM Microsoft-10891208 (build 21.0.11+10-LTS, mixed mode, sharing)

Apache Maven 3.9.16 (...)
Java version: 21.0.11, vendor: Microsoft
```

---

## 1.2 Ejecución inicial

Comando utilizado:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain
```

o la configuración utilizada:

```bash
java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain <robots> <packages>
```

**Configuración utilizada:**

- Robots: 12
- Paquetes: 100

**Resultado observado:**

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

# 2. Estado mutable compartido

Identifique los objetos y variables compartidas entre múltiples threads.

| Objeto / Clase | Estado mutable compartido | Quién lee | Quién modifica | Riesgo identificado |
|---|---|---|---|---|
| `PackageQueue` | Lista `pending` de paquetes | Robots y snapshot | Robots usando `takeNext()` | Un paquete puede ser tomado por dos robots al mismo tiempo |
| `DeliveryRegistry` | Lista `deliveries` y `nextPosition` | Snapshot y verificación | Robots usando `register()` | Dos robots pueden recibir la misma posición de entrega |
| `WarehouseStatistics` | `processedParcels` y `totalProcessingMillis` | Snapshot y reporte | Robots usando `recordProcessed()` | Incrementos perdidos por read-modify-write no atómico |
| `SimulationControl` | Bandera `paused` | Todos los robots | `pause()` y `resume()` | Un robot pausado puede seguir consumiendo paquetes; busy-wait quema CPU |

---

# 3. Condiciones de carrera encontradas

## Race Condition #1

**Clase / método involucrado:**  
`PackageQueue.takeNext()`

**Estado compartido involucrado:**  
`List<Parcel> pending`

**Comportamiento observado:**  
`IndexOutOfBoundsException` en tiempo de ejecución; un robot intenta leer o remover un elemento que ya fue removido por otro robot.

**¿Por qué ocurre?**  
El check `isEmpty()`, el `get(0)` y el `remove(0)` son tres pasos separados. Entre el check y el remove, otro robot puede remover el último elemento, dejando la lista vacía antes de que el primer robot llegue al remove.

**Evidencia de ejecución:**

```text
[warehouse-robot-12] Queue anomaly: IndexOutOfBoundsException
```

---

## Race Condition #2

**Clase / método involucrado:**  
`WarehouseStatistics.recordProcessed()`

**Estado compartido involucrado:**  
`int processedParcels`

**Comportamiento observado:**  
El contador `processedParcels` es menor que el número de entregas en el registro.

**¿Por qué ocurre?**  
`processedParcels++` no es atómico: el JVM lo descompone en leer el valor, sumarle 1 y escribirlo. Si dos robots leen el mismo valor antes de que alguno escriba, uno de los incrementos se pierde.

**Evidencia de ejecución:**

```text
processedCounter=242, registry=245
```

---

## Race Condition #3

**Clase / método involucrado:**  
`DeliveryRegistry.register()`

**Estado compartido involucrado:**  
`int nextPosition`, `List<DeliveryRecord> deliveries`

**Comportamiento observado:**  
Dos entregas reciben la misma posición; las posiciones no forman una secuencia continua.

**¿Por qué ocurre?**  
La lectura de `nextPosition`, el incremento y el `add` son tres pasos separados. Dos robots pueden leer el mismo `nextPosition` antes de que alguno lo incremente, asignando la misma posición a dos entregas distintas.

**Evidencia de ejecución:**

```text
registry=245, uniquePositions=232, positionsContiguous=false
```

---

# 4. Interleaving

**Condición seleccionada:**  
`WarehouseStatistics.recordProcessed()` — lost update en `processedParcels`

| Paso | Thread A | Thread B | Estado compartido |
|---:|---|---|---|
| 1 | Lee `processedParcels = 10` | | `processedParcels = 10` |
| 2 | | Lee `processedParcels = 10` | `processedParcels = 10` |
| 3 | Calcula `10 + 1 = 11` | | `processedParcels = 10` |
| 4 | | Calcula `10 + 1 = 11` | `processedParcels = 10` |
| 5 | Escribe `processedParcels = 11` | | `processedParcels = 11` |
| 6 | | Escribe `processedParcels = 11` | `processedParcels = 11` |

### Explicación

¿Por qué este orden de ejecución produce un resultado incorrecto?

**Respuesta:**

Ambos robots procesaron un paquete, por lo que el contador debería quedar en 12. Sin embargo, como los dos leyeron el mismo valor (10) antes de que alguno escribiera, los dos calcularon 11 y el resultado final es 11 en lugar de 12. Un incremento se perdió. El resultado depende del scheduling porque si el scheduler hubiera dejado que Thread A terminara completamente antes de que Thread B leyera, el resultado sería correcto. No controlamos ese orden.

---

# 5. Invariantes del sistema

## I1

`Cada paquete debe ser procesado exactamente una vez (no puede ser tomado por dos robots al mismo tiempo ni desaparecer del sistema).`

## I2

`Ningún paquete puede desaparecer del sistema: la suma de paquetes pendientes y entregados debe ser siempre igual al total inicial.`

## I3

`Cada posición de llegada debe ser única: no pueden existir dos entregas con la misma posición.`

## I4 — opcional

`Las posiciones de llegada deben formar una secuencia continua de 1 a N, sin huecos. El contador de procesados debe coincidir con el número de registros de entrega.`

---

# 6. Regiones críticas

| Clase | Región crítica | Invariante protegida | Mecanismo usado | ¿Por qué ese tamaño? |
|---|---|---|---|---|
| `PackageQueue` | `isEmpty()` + `get(0)` + `remove(0)` dentro de `takeNext()`, y `size()` en `pendingCount()` | I1, I2 | `synchronized` en los dos métodos | Si solo se sincronizara el `remove` y no el check+get juntos, otro robot podría colarse entre esos pasos. Todo el check-then-act debe ser atómico. |
| `DeliveryRegistry` | Lectura de `nextPosition`, incremento y `deliveries.add()` en `register()`; iteración de `deliveries` en `snapshot()` | I3, I4 | `synchronized` en `register()` y `snapshot()` | Separar la lectura del incremento permitiría que dos robots lean el mismo `nextPosition`. `snapshot()` también se sincroniza para no leer una lista a medio escribir. |
| `WarehouseStatistics` | Actualización de `processedParcels` y `totalProcessingMillis` en `recordProcessed()`; lecturas en los getters | I4 (contador == registros) | `synchronized` en los tres métodos | Los dos campos deben actualizarse juntos; dos `AtomicInteger` separados no garantizan consistencia entre ellos. |
| `SimulationControl` | Lectura/escritura de `paused` en `pause()`, `resume()`, `awaitIfPaused()`, `isPaused()` | I1 indirectamente (robot pausado no consume paquetes) | `synchronized` + `wait()` / `notifyAll()` | Los cuatro métodos tocan la misma variable booleana y deben coordinarse en el mismo monitor para que `wait()`/`notifyAll()` funcionen correctamente. |

---

# 7. Decisiones de sincronización

## 7.1 Alternativas consideradas

Marque y explique cuáles evaluaron:

- [x] `synchronized`
- [ ] `AtomicInteger`
- [ ] Colecciones concurrentes
- [ ] `Lock`
- [x] `wait()` / `notifyAll()`
- [ ] Otra: `________________________`

### Alternativa 1

**Descripción:**  
`AtomicInteger` / `AtomicLong` para los contadores de `WarehouseStatistics`.

**Ventaja:**  
Son operaciones lock-free; para un solo contador son más eficientes que `synchronized`.

**Desventaja:**  
`WarehouseStatistics` tiene dos campos (`processedParcels` y `totalProcessingMillis`) que deben actualizarse juntos. Dos `Atomic*` separados no garantizan que ambos se actualicen como una sola operación atómica, por lo que el invariante "contador == registros" podría romperse entre las dos escrituras.

### Alternativa 2

**Descripción:**  
Un lock global compartido entre todas las clases (`PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics`, `SimulationControl`).

**Ventaja:**  
Implementación simple: un solo objeto de lock, imposible olvidar sincronizar algo.

**Desventaja:**  
Destruye el paralelismo innecesariamente. Un robot registrando una entrega bloquearía a otro que solo quiere tomar un paquete, aunque esas dos operaciones no comparten ningún dato.

### Decisión final

**Mecanismo seleccionado:**  
`synchronized` a nivel de método en cada clase, con su propio monitor (`this`). Para `SimulationControl`, además `wait()` / `notifyAll()`.

**Justificación:**  
`synchronized` por clase es el nivel correcto de granularidad: lo suficientemente pequeño para no bloquear operaciones no relacionadas, y lo suficientemente grande para cubrir el invariante completo de cada clase. `wait()`/`notifyAll()` en `SimulationControl` es la única forma de hacer que un robot espere sin quemar CPU, ya que `wait()` libera el monitor mientras duerme y `notifyAll()` despierta a todos los robots a la vez cuando se llama `resume()`.

---

# 8. Finalización de threads

Explique cómo garantizaron que el programa solamente genera el reporte final cuando todos los robots han terminado.

**Mecanismo utilizado:**  
`simulation.awaitCompletion()` que internamente llama `robot.join()` por cada robot.

**Explicación:**  
`WarehouseMain` llamaba `Thread.sleep(60)` antes de imprimir el reporte, lo que solo garantiza que pasaron 60 ms, no que los robots terminaron. Se reemplazó por `simulation.awaitCompletion()`, que hace `join()` sobre cada thread de robot. `join()` bloquea hasta que ese thread específico termina su ejecución, sin importar cuánto tarde. El reporte solo se imprime una vez que todos los `join()` retornan, es decir, cuando todos los robots han terminado.

### Pregunta

¿Por qué usar `Thread.sleep(...)` no sería una solución correcta para esperar la finalización de todos los workers?

**Respuesta:**  
`Thread.sleep(ms)` solo garantiza que pasó ese tiempo, no que los robots terminaron. Si hay más robots o paquetes de lo esperado, el sleep puede terminar antes de que los robots acaben, y el reporte se imprime con datos incompletos. Además, si los robots terminan antes del tiempo del sleep, se espera tiempo innecesario. `join()` es determinista: bloquea exactamente hasta que el thread termina, sin importar cuánto tarde.

---

# 9. PAUSE / RESUME

## 9.1 Problema inicial

Explique por qué el busy waiting de la implementación inicial no es adecuado.

**Respuesta:**  
El código original usaba `while (paused) { Thread.onSpinWait(); }`. Esto hace que cada robot pausado ejecute un loop vacío continuamente, consumiendo tiempo de CPU sin hacer ningún trabajo útil. Con 12 o más robots pausados, todos compiten por CPU sin necesidad. Además, `Thread.onSpinWait()` es una hint para el procesador pero no libera el scheduler, por lo que los robots pausados siguen siendo considerados "activos" y consumen recursos del sistema.

---

## 9.2 Solución implementada

Explique cómo implementaron:

- `pause()`
- espera de los workers
- `resume()`
- despertar coordinado de los workers

**Respuesta:**  
- `pause()`: método `synchronized` que setea `paused = true`. Al ser synchronized, solo un hilo puede modificar la variable a la vez.
- Espera de los workers: `awaitIfPaused()` es `synchronized` y usa `while (paused) { wait(); }`. El `while` (no `if`) protege contra spurious wakeups: el robot vuelve a verificar la condición antes de continuar. `wait()` libera el monitor y pone el thread a dormir, sin consumir CPU.
- `resume()`: método `synchronized` que setea `paused = false` y llama `notifyAll()`.
- Despertar coordinado: `notifyAll()` despierta a todos los robots que estaban en `wait()` a la vez. Cada uno vuelve a verificar `while (paused)` y, como ya es `false`, continúa su ejecución.

---

## 9.3 Snapshot consistente

Cuando la simulación está pausada, registre:

```text
Processed parcels: 47
Pending parcels:   53
Registry size:     47
Current leader:    Robot-03 / parcel 12 / position 1
```

Explique cómo garantizan que esos valores representan un estado consistente.

**Respuesta:**  
Cuando se llama `pause()`, un robot que ya está en medio de una iteración no se detiene inmediatamente. Termina de procesar el paquete actual, llama `register()` y `recordProcessed()`, y solo después llega a `awaitIfPaused()` y se duerme. Como `register()` y `recordProcessed()` son `synchronized`, quien lee el snapshot mientras la simulación está pausada solo puede ver el estado antes o después de que esas llamadas terminaron, nunca a mitad de una escritura. El snapshot es siempre una foto de robots que terminaron su paso actual, no de uno atrapado a mitad de una actualización.

---

# 10. Verificación con RaceConditionProbe

Ejecute:

```bash
java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500
```

## Resultados

| Robots | Paquetes | Runs | Anomalías antes | Anomalías después |
|---:|---:|---:|---:|---:|
| 8 | 100 | 100 | ver Evidencias 1-3 (starter roto) | 0 |
| 16 | 250 | 100 | ver Evidencias 1-3 (starter roto) | 0 |
| 32 | 500 | 100 | ver Evidencias 1-3 (starter roto) | 0 |

### Resultado final esperado

```text
Anomalous runs: 0/100
```

**Salida obtenida:**

```text
Running 100 simulations with 8 robots and 100 parcels...
Anomalous runs: 0/100  ✓

Running 100 simulations with 16 robots and 250 parcels...
Anomalous runs: 0/100  ✓

Running 100 simulations with 32 robots and 500 parcels...
Anomalous runs: 0/100  ✓
```

---

# 11. Evidencia de correctitud

Explique brevemente cómo demuestran que su solución es correcta.

**Conclusión:**

Las invariantes I1–I4 se verifican automáticamente por `InvariantChecker` en cada run del `RaceConditionProbe`. En 300 ejecuciones totales (100 por cada configuración de carga), ninguna produjo anomalías: no hubo paquetes duplicados, no hubo posiciones repetidas, el contador de procesados coincidió con el tamaño del registro, y no quedaron paquetes pendientes al finalizar. La consistencia durante la pausa se garantiza porque los robots terminan su operación actual antes de dormir, y `register()`/`recordProcessed()` son atómicos. La finalización correcta se garantiza con `join()` en lugar de `Thread.sleep()`. Los 2/2 tests de `InvariantCheckerTest` pasan con `mvn clean test`.

---

# 12. Impacto en atributos de calidad

| Atributo | Impacto de la solución | Evidencia / métrica |
|---|---|---|
| Correctitud / Reliability | Mejora: las invariantes de posiciones únicas y contadores consistentes están garantizadas | 0/100 anomalías en RaceConditionProbe; 2/2 tests pasan |
| Performance / Throughput | Leve reducción: los robots esperan turno para entrar a regiones críticas, pero el bloqueo es mínimo porque cada clase tiene su propio monitor | Las secciones críticas son de pocas líneas; robots en clases distintas no se bloquean entre sí |
| Maintainability | Mejora: cada clase maneja su propia sincronización; la región protegida es explícita y justificada | Sin locks ocultos ni sincronización innecesaria |
| Scalability | Aceptable dentro de una JVM: más robots solo aumentan la contención en las regiones críticas, que son pequeñas. No escala a múltiples JVMs | Ver sección 14 para el análisis multi-instancia |

---

# 13. Trade-off principal

¿Qué ganaron y qué sacrificaron al introducir sincronización?

**Respuesta:**

Ganamos correctitud determinista: las invariantes del sistema se cumplen en todas las ejecuciones, sin importar el orden en que el scheduler ejecute los threads. Los paquetes no se duplican, las posiciones son únicas y el reporte final es siempre correcto.

Sacrificamos algo de paralelismo: cuando dos robots quieren acceder al mismo objeto al mismo tiempo, uno debe esperar. Sin embargo, al usar un monitor por clase en lugar de un lock global, minimizamos la contención: un robot registrando una entrega no bloquea a otro que solo quiere tomar un paquete nuevo.

---

# 14. Análisis arquitectónico

Suponga ahora que existen tres instancias de la aplicación:

```text
                 Load Balancer
                       |
            +----------+----------+
            |          |          |
          App A      App B      App C
            \          |          /
                    Database
```

## 14.1 Pregunta

¿Los bloques `synchronized` utilizados dentro de una JVM garantizan consistencia entre `App A`, `App B` y `App C`?

- [ ] Sí
- [x] No

**Justificación:**

`synchronized` solo protege memoria dentro de una JVM. Cada instancia tiene su propia copia de `nextPosition`, `paused`, `pending`, etc. en su heap. Un lock en App A no tiene ningún efecto sobre lo que App B o App C están haciendo simultáneamente. Dos robots corriendo en instancias distintas podrían leer el mismo `nextPosition` de sus respectivas copias locales y asignar la misma posición de entrega, exactamente el mismo problema que teníamos entre threads, pero ahora entre procesos.

---

## 14.2 Evolución arquitectónica

¿Qué alternativa consideraría para garantizar consistencia entre múltiples instancias?

- [x] Transacción en base de datos
- [x] Restricción / constraint en base de datos
- [ ] Optimistic locking / versionado
- [ ] Lock distribuido
- [ ] Otra: `________________________`

**Decisión propuesta:**

Mover el estado compartido (`nextPosition`, contadores, cola de paquetes) a una base de datos compartida y usar transacciones con constraints de unicidad.

**Justificación:**

Una base de datos con una constraint `UNIQUE` en la columna de posición garantiza que dos instancias no puedan insertar la misma posición, sin importar en qué JVM corran. Las transacciones garantizan que el read-increment-write de `nextPosition` sea atómico a nivel de base de datos. Esto traslada la garantía de consistencia a una capa que todas las instancias comparten, que es exactamente lo que `synchronized` no puede hacer entre JVMs.

---

# 15. Mini ADR

## ADR-001 — Concurrency control for warehouse shared state

### Context

En el simulador de almacén, varios robots (threads Java) comparten cuatro objetos: `PackageQueue`, `DeliveryRegistry`, `WarehouseStatistics` y `SimulationControl`. El código inicial no tiene ninguna protección sobre estos objetos, lo que genera condiciones de carrera: dos robots pueden tomar el mismo paquete, recibir la misma posición de entrega, perder incrementos de contador, o quemar CPU en active waiting. La tarea es corregir esos problemas con la mínima sincronización necesaria, sin eliminar la concurrencia ni usar un lock global.

### Decision

Usamos `synchronized` a nivel de método en cada clase por separado. En `PackageQueue`, `takeNext()` y `pendingCount()` se sincronizaron para que el check, la lectura y el remove sean una operación atómica. En `DeliveryRegistry`, `register()` y `snapshot()` se sincronizaron para que la lectura de `nextPosition`, el incremento y el `add` no puedan ser interrumpidos por otro robot. En `WarehouseStatistics`, los tres métodos se sincronizaron para que los dos campos se actualicen juntos. En `SimulationControl`, se reemplazó el busy-wait con `wait()`/`notifyAll()` para que los robots duerman sin consumir CPU. Cada clase usa su propio monitor (`this`), independiente de las demás.

### Alternatives considered

1. Lock global compartido entre todas las clases: descartado porque bloquearía robots que operan sobre datos no relacionados, reduciendo el throughput innecesariamente.
2. `AtomicInteger`/`AtomicLong` para los contadores: válido para un solo contador, pero `WarehouseStatistics` tiene dos campos que deben actualizarse juntos, y `DeliveryRegistry` necesita atomicidad entre tres pasos. Un `Atomic*` por variable no cubre esos casos.

### Quality attributes affected

Correctitud mejora: las invariantes de posiciones únicas y contadores consistentes están garantizadas. Performance tiene una leve reducción por la contención en regiones críticas, minimizada al usar un monitor por clase. Mantenibilidad mejora: cada clase encapsula su propia sincronización con regiones críticas explícitas y justificadas.

### Evidence

`RaceConditionProbe` retorna 0/100 anomalías en las tres configuraciones requeridas (8/100, 16/250, 32/500). `mvn clean test` pasa con BUILD SUCCESS, 2/2 tests. No se observan `IndexOutOfBoundsException`, posiciones duplicadas ni contadores desincronizados después de los fixes.

### Consequences

Cada clase protege sus propios invariantes de forma independiente. No hay lock global, por lo que robots que operan sobre clases distintas no se bloquean entre sí. El comportamiento público de cada clase no cambió: mismas firmas, misma semántica. La correctitud ya no depende del scheduler del sistema operativo.

### Risks

Si en el futuro se agrega lógica que requiera atomicidad entre dos clases distintas, los monitores separados no serán suficientes y el diseño deberá revisarse. Si alguien agrega un método nuevo que toque los mismos campos sin pasar por los métodos sincronizados, la protección se rompe sin advertencia del compilador. En un escenario con múltiples instancias JVM detrás de un load balancer, `synchronized` no protege nada entre procesos separados.

---

# 16. Cambios realizados

| Archivo / Clase | Cambio realizado | Razón |
|---|---|---|
| `PackageQueue.java` | `takeNext()` y `pendingCount()` marcados como `synchronized` | Eliminar el check-then-act race condition: el isEmpty+get+remove deben ser atómicos |
| `DeliveryRegistry.java` | `register()` y `snapshot()` marcados como `synchronized`; incremento de `nextPosition` explícito | Eliminar el lost-update en `nextPosition` y garantizar posiciones únicas y consecutivas |
| `WarehouseStatistics.java` | `recordProcessed()`, `processedParcels()` y `totalProcessingMillis()` marcados como `synchronized` | Garantizar que los dos campos se actualicen juntos y que las lecturas sean consistentes |
| `SimulationControl.java` | Reemplazado busy-wait con `synchronized` + `wait()`/`notifyAll()` en los 4 métodos | Eliminar active waiting; los robots duermen sin consumir CPU y se despiertan coordinadamente |
| `WarehouseMain.java` | Reemplazado `Thread.sleep(60)` por `simulation.awaitCompletion()` (que hace `join()` por cada robot) | Garantizar que el reporte final solo se imprime cuando todos los robots han terminado |

---

# 17. Pruebas ejecutadas

| Prueba | Comando | Resultado |
|---|---|---|
| Compilación y tests | `mvn clean test` | BUILD SUCCESS — 2/2 tests passed |
| Simulación estándar | `java -cp target/classes edu.eci.arsw.warehouse.app.WarehouseMain 12 100` | Reporte final correcto, 0 paquetes pendientes, contador == registry size |
| RaceConditionProbe 8/100 | `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 8 100` | 0/100 anomalous runs |
| RaceConditionProbe 16/250 | `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 16 250` | 0/100 anomalous runs |
| RaceConditionProbe 32/500 | `java -cp target/classes edu.eci.arsw.warehouse.verification.RaceConditionProbe 100 32 500` | 0/100 anomalous runs |
| Pause / Resume | `java -cp target/classes edu.eci.arsw.warehouse.app.PauseResumeDemo` | Pausa y reanuda correctamente; snapshot consistente durante la pausa |

---

# 18. Conclusiones

1. `Las condiciones de carrera no son visibles en el código fuente: el starter se veía razonable pero producía IndexOutOfBoundsException, posiciones duplicadas y contadores desincronizados porque operaciones de múltiples pasos no eran atómicas.`
2. `synchronized a nivel de método por clase es el nivel de granularidad correcto para este problema: protege cada invariante sin bloquear operaciones no relacionadas entre clases distintas.`
3. `wait()/notifyAll() es la única forma correcta de implementar pause/resume sin active waiting: el thread libera el monitor mientras duerme y se despierta exactamente cuando resume() lo indica, sin consumir CPU.`
4. `join() es la única forma correcta de esperar la finalización de workers: Thread.sleep() solo garantiza que pasó tiempo, no que los threads terminaron, lo que produce reportes prematuros con datos incompletos.`
5. `synchronized solo protege dentro de una JVM: en un escenario distribuido con múltiples instancias, la consistencia debe moverse a una capa compartida como una base de datos con transacciones y constraints, porque ningún mecanismo de memoria compartida puede cruzar procesos.`

---

# 19. Checklist de entrega

- [x] El proyecto compila con `mvn clean test`.
- [x] El código utiliza Java 21.
- [x] No se eliminó la concurrencia.
- [x] No existe busy waiting en la solución final.
- [x] El programa espera correctamente la finalización de todos los robots.
- [x] Las regiones críticas están justificadas.
- [x] Se preservan las invariantes definidas.
- [x] El `RaceConditionProbe` final no presenta anomalías.
- [x] Se documentó el análisis arquitectónico.
- [x] Se incluyó el ADR.
- [x] El repositorio contiene commits claros.
- [x] Se incluyó la URL del repositorio y el commit final.

---

## Nota

No se evalúa la cantidad de texto. Se evalúa la capacidad de demostrar:

> **problema → evidencia → invariante → región crítica → decisión → implementación → verificación → trade-off arquitectónico**
