# Cierre bancario con Spring Batch

**Autor:** Lissett Zuñiga Reyes

## Cómo correrlo

```bash
docker compose up -d --wait
./correr.sh 2026-09-30 prueba
./ver-batch.sh

## Día 1 · Mi primer Job

### Boleto de salida

1. ¿Qué diferencia hay entre un proceso batch y la API REST de la Semana 3? Da dos.
    Invocación: La API REST se ejecuta por peticiones HTTP bajo demanda (un usuario o cliente da clic o llama un endpoint). El proceso batch es automático y se ejecuta de forma programada o masiva por lotes sin intervención humana.
    Volumen de procesamiento: La API REST procesa solicitudes de forma individual y síncrona en tiempo real, mientras que el proceso batch procesa grandes volúmenes de registros en bloque optimizando el uso de recursos y transacciones.
2. ¿Qué es un Job, qué es un Step y qué es un Tasklet?
    Job: Es el contenedor o flujo completo del proceso por lotes que agrupa uno o varios pasos en un orden determinado.
    Step: Es una fase o etapa independiente dentro de un Job que encapsula una unidad de trabajo específica.
    Tasklet: Es un tipo de Step simplificado que ejecuta una sola tarea lógica o bloque de código una única vez dentro de una transacción y finaliza.
3. Con tus tablas: ¿qué diferencia hay entre una **JobInstance** y una **JobExecution**?
    JobInstance: Representa la definición lógica del Job ligada a un conjunto de parámetros identificadores únicos.
    JobExecution: Representa un intento técnico individual de ejecutar esa JobInstance. Una JobInstance puede tener múltiples JobExecution si los primeros intentos fallaron y se reintentaron.
4. ¿Por qué Spring Batch no deja correr dos veces el cierre del 28?
    Por el principio de idempotencia y protección de datos: la JobInstance del día 2026-09-28 ya alcanzó el estado COMPLETED. Para evitar duplicar operaciones críticas (como cobrar dos veces las comisiones del mismo día), Spring Batch bloquea la reejecución de instancias ya completadas lanzando JobInstanceAlreadyCompleteException.
5. (MP-4, paso 6) Si mañana llega el archivo del 25 y corres otra vez el cierre del 25, ¿será otra instancia u otra ejecución de la misma? ¿Por qué lo crees?
    Será otra ejecución de la misma instancia (JobInstance). Como el intento anterior para fecha=2026-12-25 terminó en estado FAILED, la instancia no se ha completado. Al volver a correr con el mismo parámetro, Spring Batch reutiliza la JobInstance existente y crea un nuevo registro en BATCH_JOB_EXECUTION para intentar completarla.