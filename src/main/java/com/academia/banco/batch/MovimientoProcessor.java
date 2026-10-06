package com.academia.banco.batch;

import com.academia.banco.model.Movimiento;
import org.springframework.batch.item.ItemProcessor;

public class MovimientoProcessor implements ItemProcessor<Movimiento, Movimiento> {

    @Override
    public Movimiento process(Movimiento movimiento) {
        String tipo = movimiento.tipo().trim().toUpperCase();
        return new Movimiento(movimiento.cuenta().trim(), tipo, movimiento.monto());
    }
}
