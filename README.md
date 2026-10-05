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
