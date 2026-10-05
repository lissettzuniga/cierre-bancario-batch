package com.academia.banco.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CierreJobConfig {

    @Bean
    public Step saludoStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("saludoStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    System.out.println(">>> Hola desde el cierre del día");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

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
    public Step contarArchivosStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("contarArchivosStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    try (Stream<Path> archivos = Files.list(Path.of("datos"))) {
                        long total = archivos.count();
                        System.out.println(">>> Total de archivos en datos/: " + total);
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Job cierreDelDiaJob(
            JobRepository jobRepository,
            Step saludoStep,
            Step verificarArchivoStep,
            Step contarArchivosStep) {
        return new JobBuilder("cierreDelDiaJob", jobRepository)
                .start(saludoStep)
                .next(verificarArchivoStep)
                .next(contarArchivosStep)
                .build();
    }
}