package com.academia.banco.processor;

import com.academia.banco.model.SaldoCuenta;
import java.math.BigDecimal;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class FiltroSaldoPositivoProcessor implements ItemProcessor<SaldoCuenta, SaldoCuenta> {

    @Override
    public SaldoCuenta process(SaldoCuenta item) throws Exception {
        if (item.saldo() != null && item.saldo().compareTo(BigDecimal.ZERO) < 0) {
            return null; // Filtra la cuenta sobregirada
        }
        return item;
    }
}