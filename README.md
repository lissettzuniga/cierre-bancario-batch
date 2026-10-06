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
