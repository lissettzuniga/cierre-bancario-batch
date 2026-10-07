package com.academia.banco.batch;

import com.academia.banco.model.Movimiento;
import java.math.BigDecimal;
import org.springframework.batch.item.ItemProcessor;

public class MovimientoProcessor implements ItemProcessor<Movimiento, Movimiento> {

    private static final BigDecimal LIMITE_MAXIMO = new BigDecimal("10000.00");

    @Override
    public Movimiento process(Movimiento item) throws Exception {
        // Filtrar por tipo permitido
        if (!"DEPOSITO".equalsIgnoreCase(item.tipo()) && !"RETIRO".equalsIgnoreCase(item.tipo())) {
            return null;
        }

        // Filtrar montos mayores a 10,000.00 (van a revisión manual)
        if (item.monto() != null && item.monto().compareTo(LIMITE_MAXIMO) > 0) {
            return null;
        }

        return item;
    }
}