package com.academia.banco.config;

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

    // Un Step de tipo Tasklet: recibe JobRepository y PlatformTransactionManager
    @Bean
    public Step saludoStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("saludoStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    System.out.println(">>> Hola desde el cierre del día");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // El Job: el contenedor de los steps. Por ahora tiene uno.
    @Bean
    public Job cierreDelDiaJob(JobRepository jobRepository, Step saludoStep) {
        return new JobBuilder("cierreDelDiaJob", jobRepository)
                .start(saludoStep)
                .build();
    }
}