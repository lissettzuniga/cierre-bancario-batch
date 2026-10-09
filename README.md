# Cierre bancario con Spring Batch

**Autor:** Lissett Zuñiga Reyes

## Cómo correrlo

```bash
docker compose up -d --wait
./correr.sh 2026-09-30 prueba
./ver-batch.sh
```

## Día 1 · Mi primer Job

### Boleto de salida

1. **¿Qué diferencia hay entre un proceso batch y la API REST de la Semana 3? Da dos.**
   * **Invocación:** La API REST se ejecuta por peticiones HTTP bajo demanda (un usuario o cliente da clic o llama un endpoint). El proceso batch es automático y se ejecuta de forma programada o masiva por lotes sin intervención humana.
   * **Volumen de procesamiento:** La API REST procesa solicitudes de forma individual y síncrona en tiempo real, mientras que el proceso batch procesa grandes volúmenes de registros en bloque optimizando el uso de recursos y transacciones.

2. **¿Qué es un Job, qué es un Step y qué es un Tasklet?**
   * **Job:** Es el contenedor o flujo completo del proceso por lotes que agrupa uno o varios pasos en un orden determinado.
   * **Step:** Es una fase o etapa independiente dentro de un Job que encapsula una unidad de trabajo específica.
   * **Tasklet:** Es un tipo de Step simplificado que ejecuta una sola tarea lógica o bloque de código una única vez dentro de una transacción y finaliza.

3. **Con tus tablas: ¿qué diferencia hay entre una JobInstance y una JobExecution?**
   * Una **JobInstance** es la definición del trabajo junto con sus parámetros únicos (por ejemplo, el cierre para la fecha `2026-09-28`). Representa "lo que se tiene que procesar". En cambio, una **JobExecution** es cada intento real de correr esa instancia. Si un trabajo falla al primer intento y lo vuelvo a ejecutar con los mismos parámetros, la `JobInstance` sigue siendo la misma, pero genera una segunda `JobExecution`.

4. **¿Por qué Spring Batch no deja correr dos veces el cierre del 28?**
   * Por seguridad y para evitar duplicar información. Como la `JobInstance` del `2026-09-28` ya terminó con estado `COMPLETED`, Spring Batch bloquea cualquier reejecución para no aplicar dos veces los mismos procesos (como cobrar comisiones o mover saldos repetidos). Si intenta correrse de nuevo con la misma fecha, lanza un `JobInstanceAlreadyCompleteException`.

5. **(MP-4, paso 6) Si mañana llega el archivo del 25 y corres otra vez el cierre del 25, ¿será otra instancia u otra ejecución de la misma? ¿Por qué lo crees?**
   * Será **otra ejecución de la misma instancia**. Como la corrida previa del `2026-09-25` quedó en estado `FAILED`, la `JobInstance` de esa fecha no se ha completado. Al ejecutar nuevamente la tarea con el mismo parámetro de fecha, Spring Batch reutiliza la `JobInstance` existente y registra un nuevo intento en la tabla `BATCH_JOB_EXECUTION`.

## Día 2 · El primer chunk

### Boleto de salida

1. **¿Qué diferencia hay entre un step de tipo Tasklet y uno de tipo chunk?**
   Un *Tasklet* ejecuta una tarea única e indivisible de principio a fin (como verificar la existencia de un archivo o enviar un correo), mientras que un *chunk* procesa datos en flujo iterativo (Lector → Procesador → Escritor) por bloques de registros dentro de transacciones individuales.

2. **¿Qué hace cada una de las tres piezas de un chunk? ¿Cuál es opcional?**
   * **Lector (ItemReader):** Lee un elemento a la vez de la fuente de datos.
   * **Procesador (ItemProcessor):** Transforma o valida el elemento. Es la única pieza **opcional**.
   * **Escritor (ItemWriter):** Escribe la lista completa de elementos recopilados en el chunk.

3. **Con 45 movimientos y chunks de 10, ¿cuántos commits habría? ¿Y con chunks de 50?**
   * Con **chunks de 10**: $5$ commits ($10 + 10 + 10 + 10 + 5$).
   * Con **chunks de 50**: $1$ commit ($45$ en una sola transacción).

4. **¿Por qué el Escritor recibe el chunk completo y no un movimiento a la vez?**
   Para optimizar el rendimiento y las operaciones de E/S. Al recibir la lista completa, puede ejecutar inserciones en lote (*batch SQL*) en la base de datos dentro de una sola transacción, reduciendo drásticamente la latencia y las llamadas a la red.

5. **Mi predicción de la MP-3, paso 1: ¿qué habría pasado sin el Procesador?**
   Los registros se habrían insertado en MySQL conservando inconsistencias de formato (espacios extra, minúsculas/mayúsculas). Al consultar o agrupar (`GROUP BY tipo`), habrían aparecido categorías dispersas e incorrectas como `" RETIRO"`, `"deposito"` o `"Retiro"`, afectando los saldos finales.


## Día 3 · Parámetros, fallas y reinicio

### Boleto de salida

1. **¿Qué diferencia hay entre una JobInstance y una JobExecution? Usa como ejemplo el cierre del 25.**  
   La `JobInstance` representa la definición lógica de una ejecución de trabajo asociada a parámetros identificadores únicos (en este caso, `fecha=2026-10-25`). La `JobExecution` representa cada intento técnico de ejecutar esa instancia. Para el cierre del 25 existió una única `JobInstance`, pero dos `JobExecution`: la primera falló (`FAILED`) y la segunda, al reintentarse con la misma fecha, terminó con éxito (`COMPLETED`).

2. **¿En qué caso Spring Batch se niega a correr un cierre, y en qué caso lo reinicia?**  
   Spring Batch se niega a correr un cierre cuando la `JobInstance` asociada a esa fecha ya tiene una `JobExecution` previa con estado `COMPLETED` (garantizando idempotencia). Por el contrario, si la última `JobExecution` de esa instancia terminó en estado `FAILED` (o interrumpió de forma no definitiva), Spring Batch permite reiniciar el Job reutilizando la misma instancia.

3. **En el reinicio del día 5, ¿por qué el step de carga leyó 10 movimientos y no 20?**  
   Porque en la ejecución fallida previo a la corrección, el primer chunk (movimientos 1 al 10) ya se había procesado, escrito y confirmado (`COMMIT_COUNT = 1`). Spring Batch registró ese punto de control (checkpoint) en el `BATCH_STEP_EXECUTION_CONTEXT`. Al reiniciar, el lector reanudó la lectura exactamente a partir del registro 11, leyendo únicamente los 10 movimientos restantes para completar los 20 sin duplicar datos.

4. **¿Qué diferencia hay entre un movimiento filtrado y uno omitido?**  
   Un movimiento **filtrado** se lee y parsea correctamente a nivel de estructura, pero el `ItemProcessor` decide ignorarlo de forma explícita según reglas de negocio (retornando `null`, por ejemplo para tipos no permitidos como `PAGO`), incrementando `FILTER_COUNT`. Un movimiento **omitido** (skip) sufre un error técnico de parseo o formato (como `FlatFileParseException` al encontrar texto donde debía ir un monto numérico) y el step, al ser `faultTolerant()`, lo salta e incrementa `SKIP_COUNT`.

5. **¿Por qué importa el código de salida, si el estado ya queda en las tablas?**  
   Porque los orquestadores y planificadores de tareas de producción (como Control-M) no consultan la base de datos de metadatos de Spring Batch; únicamente evalúan el código de retorno que la JVM entrega al sistema operativo al finalizar la ejecución del proceso. Un código de salida `0` indica éxito, mientras que un código distinto (como `5` o `1`) notifica al orquestador que el Job falló, permitiéndole lanzar alertas o detener flujos dependientes.

## Día 4 · De MySQL a MongoDB

### Boleto de salida

1. **¿Qué hace cada uno de los tres steps de tu Job, y de qué tipo es cada uno?**
   - `verificarArchivoStep` (**Tasklet**): Comprueba la existencia y disponibilidad del archivo CSV diario.
   - `cargarMovimientosStep` (**Chunk**): Lee el CSV diario, valida/transforma los movimientos y los inserta en la tabla MySQL `movimiento`.
   - `publicarSaldosStep` (**Chunk**): Lee los movimientos consolidados desde MySQL, calcula los saldos agregados por cuenta y los persiste en la colección `saldos` de MongoDB.

2. **¿Por qué el cierre del 9 no duplicó los saldos, y el del 10 (sin `@Id`) sí?**
   - El cierre del 9 utilizó la anotación `@Id` en la propiedad `cuenta` del record `SaldoCuenta`. Esto hace que Spring Data MongoDB utilice operaciones de tipo *upsert* (`save`), sobreescribiendo el documento si la clave primaria ya existe.
   - Al retirar `@Id` en el cierre del 10, MongoDB no reconoce ningún atributo como identificador único y asigna automáticamente un `ObjectId` dinámico predeterminado a cada inserción, generando documentos duplicados.

3. **Al reiniciar el cierre del 11, ¿por qué no se cargó otra vez el archivo?**
   - Porque Spring Batch consulta los metadatos guardados en la base de datos (`BATCH_STEP_EXECUTION`). Al detectar que `verificarArchivoStep` y `cargarMovimientosStep` ya tenían el estado `COMPLETED`, los omitió automáticamente y reanudó la ejecución a partir del primer paso fallido (`publicarSaldosStep`).

4. **¿Qué diferencia hay entre `spring-boot-starter-data-mongodb` y «Spring Batch MongoDB» (`batch-data-mongodb`)?**
   - `spring-boot-starter-data-mongodb`: Proporciona la infraestructura base de Spring Data (como `MongoTemplate` y los repositorios) para interactuar, mapear y persistir entidades en MongoDB.
   - `batch-data-mongodb`: Ofrece componentes especializados para flujos por lotes (`MongoItemReader` y `MongoItemWriter`), integrando operaciones en chunks y paginación directamente con el ciclo de vida de Spring Batch.

## Lo que aprendí esta semana

Un proceso batch es una ejecución automatizada por lotes que procesa grandes volúmenes de datos de forma eficiente y sin intervención humana directa. En Spring Batch, un Job se compone de uno o varios Steps, los cuales pueden implementarse como Tasklets (para tareas puntuales o administrativas) o Chunks (orientados al procesamiento iterativo con Reader, Processor y Writer). Lo más potente de Spring Batch es su gestión de resiliencia y trazabilidad mediante tablas de metadatos: si ocurre una falla a mitad de un proceso, el framework registra exactamente qué pasos se completaron y en qué punto ocurrió el error. Esto permite reiniciar el Job corrigiendo únicamente la causa raíz, garantizando la idempotencia y evitando reprocesar o duplicar la información ya procesada.