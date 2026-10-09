package com.academia.banco.config;

import com.academia.banco.batch.MovimientoProcessor;
import com.academia.banco.model.Movimiento;
import com.academia.banco.model.SaldoCuenta;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.MongoItemWriter;
import org.springframework.batch.item.data.builder.MongoItemWriterBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CierreJobConfig {

    // Tasklet: revisa que exista el archivo de movimientos de la fecha que recibió el Job.
    @Bean
    public Step verificarArchivoStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("verificarArchivoStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Object fecha = chunkContext.getStepContext().getJobParameters().get("fecha");
                    Path archivo = Path.of("datos/movimientos-" + fecha + ".csv");
                    if (!Files.exists(archivo)) {
                        throw new IllegalStateException("No existe el archivo del día: " + archivo);
                    }
                    long movimientos = Files.readAllLines(archivo).size() - 1;   // menos el encabezado
                    System.out.println(">>> Archivo del día: " + archivo + " (" + movimientos + " movimientos)");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // El Lector: lee el archivo de la fecha del Job, un renglón a la vez, y lo convierte en un Movimiento.
    @Bean
    @StepScope
    public FlatFileItemReader<Movimiento> movimientoReader(@Value("#{jobParameters['fecha']}") String fecha) {
        return new FlatFileItemReaderBuilder<Movimiento>()
                .name("movimientoReader")
                .resource(new FileSystemResource("datos/movimientos-" + fecha + ".csv"))
                .linesToSkip(1)                          // el encabezado
                .delimited()                             // separado por comas
                .names("cuenta", "tipo", "monto")        // las columnas, en orden
                .targetType(Movimiento.class)            // cada renglón → un Movimiento
                .build();
    }

    // El Escritor: guarda en MySQL los movimientos que le llegan, todos juntos.
    @Bean
    public JdbcBatchItemWriter<Movimiento> movimientoWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Movimiento>()
                .dataSource(dataSource)
                .sql("INSERT INTO movimiento (cuenta, tipo, monto) VALUES (:cuenta, :tipo, :monto)")
                .beanMapped()                            // :cuenta → movimiento.cuenta(), etc.
                .build();
    }

    // Un Step de tipo Chunk: lee, procesa y escribe de 10 en 10, omitiendo los renglones que no se pueden leer.
    @Bean
    public Step cargarMovimientosStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                      FlatFileItemReader<Movimiento> movimientoReader,
                                      JdbcBatchItemWriter<Movimiento> movimientoWriter) {
        return new StepBuilder("cargarMovimientosStep", jobRepository)
                .<Movimiento, Movimiento>chunk(10, transactionManager)
                .reader(movimientoReader)
                .processor(new MovimientoProcessor())
                .writer(movimientoWriter)
                .faultTolerant()                             // tolerante a fallas:
                .skip(FlatFileParseException.class)          // si un renglón no se puede leer, se OMITE…
                .skipLimit(3)                                // …hasta 3 por ejecución; el 4.º hace fallar el step
                .build();
    }

    // ── Step 3: de MySQL a MongoDB ─────────────────────────────────────────────────────────────────────────────

    // El Lector: consulta MySQL agrupando saldos y mapea directamente al record SaldoCuenta.
    @Bean
    public JdbcCursorItemReader<SaldoCuenta> saldoReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<SaldoCuenta>()
                .name("saldoReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT cuenta,
                               SUM(CASE WHEN tipo = 'DEPOSITO' THEN monto ELSE -monto END) AS saldo,
                               COUNT(*) AS movimientos
                        FROM movimiento
                        GROUP BY cuenta
                        ORDER BY cuenta""")
                .rowMapper(new DataClassRowMapper<>(SaldoCuenta.class))
                .build();
    }

    // El Escritor: guarda cada SaldoCuenta en la colección «saldos».
    @Bean
    public MongoItemWriter<SaldoCuenta> saldoWriter(MongoTemplate mongoTemplate) {
        return new MongoItemWriterBuilder<SaldoCuenta>()
                .template(mongoTemplate)
                .collection("saldos")
                .build();
    }

    // Step de tipo Chunk: lee de MySQL y escribe en MongoDB, de 3 en 3.
    @Bean
    public Step publicarSaldosStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                   JdbcCursorItemReader<SaldoCuenta> saldoReader,
                                   MongoItemWriter<SaldoCuenta> saldoWriter) {
        return new StepBuilder("publicarSaldosStep", jobRepository)
                .<SaldoCuenta, SaldoCuenta>chunk(3, transactionManager)
                .reader(saldoReader)
                .writer(saldoWriter)
                .build();
    }

    // El Job principal.
    @Bean
    public Job cierreDelDiaJob(JobRepository jobRepository, Step verificarArchivoStep, Step cargarMovimientosStep,
                               Step publicarSaldosStep) {
        return new JobBuilder("cierreDelDiaJob", jobRepository)
                .start(verificarArchivoStep)
                .next(cargarMovimientosStep)
                .next(publicarSaldosStep)
                .build();
    }
}