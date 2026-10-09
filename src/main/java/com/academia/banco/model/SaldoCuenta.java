package com.academia.banco.model;

import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("saldos")
public record SaldoCuenta(@Id String cuenta, BigDecimal saldo, long movimientos) {
}