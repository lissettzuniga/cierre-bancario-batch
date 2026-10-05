package com.academia.banco;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class CierreBancarioBatchApplicationTests {

    @Test
    void contextLoads() {
    }

}