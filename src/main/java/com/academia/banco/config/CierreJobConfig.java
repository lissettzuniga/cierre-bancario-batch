package com.academia.banco.config;

import com.academia.banco.batch.MovimientoProcessor;
import com.academia.banco.model.Movimiento;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CierreJobConfig {

    @Bean
    public Step verificarArchivoStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("verificarArchivoStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    Object fecha = chunkContext.getStepContext().getJobParameters().get("fecha");
                    Path archivo = Path.of("datos/movimientos-" + fecha + ".csv");
                    if (!Files.exists(archivo)) {
                        throw new IllegalStateException("No existe el archivo del día: " + archivo);
                    }
                    long movimientos = Files.readAllLines(archivo).size() - 1;
                    System.out.println(">>> Archivo del día: " + archivo + " (" + movimientos + " movimientos)");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Movimiento> movimientoReader(@Value("#{jobParameters['fecha']}") String fecha) {
        return new FlatFileItemReaderBuilder<Movimiento>()
                .name("movimientoReader")
                .resource(new FileSystemResource("datos/movimientos-" + fecha + ".csv"))
                .linesToSkip(1)
                .delimited()
                .names("cuenta", "tipo", "monto")
                .targetType(Movimiento.class)
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<Movimiento> movimientoWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Movimiento>()
                .dataSource(dataSource)
                .sql("INSERT INTO movimiento (cuenta, tipo, monto) VALUES (:cuenta, :tipo, :monto)")
                .beanMapped()
                .build();
    }

    @Bean
    public Step cargarMovimientosStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                      FlatFileItemReader<Movimiento> movimientoReader,
                                      JdbcBatchItemWriter<Movimiento> movimientoWriter) {
        return new StepBuilder("cargarMovimientosStep", jobRepository)
                .<Movimiento, Movimiento>chunk(10, transactionManager)
                .reader(movimientoReader)
                .processor(new MovimientoProcessor())
                .writer(movimientoWriter)
                .build();
    }

    @Bean
    public Job cierreDelDiaJob(JobRepository jobRepository, Step verificarArchivoStep, Step cargarMovimientosStep) {
        return new JobBuilder("cierreDelDiaJob", jobRepository)
                .start(verificarArchivoStep)
                .next(cargarMovimientosStep)
                .build();
    }
}
