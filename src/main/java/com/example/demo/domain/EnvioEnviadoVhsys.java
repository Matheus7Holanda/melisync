package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Registro de um envio do ML ja lancado no VHSYS -- evita lancar duas vezes. */
@Entity
public class EnvioEnviadoVhsys {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private Long shippingId;

    private LocalDate dia;
    private Long idFrente;
    private BigDecimal valorLiquido;
    private Instant enviadoEm;

    protected EnvioEnviadoVhsys() {
    }

    public EnvioEnviadoVhsys(Long shippingId, LocalDate dia, Long idFrente, BigDecimal valorLiquido) {
        this.shippingId = shippingId;
        this.dia = dia;
        this.idFrente = idFrente;
        this.valorLiquido = valorLiquido;
        this.enviadoEm = Instant.now();
    }

    public Long getShippingId() {
        return shippingId;
    }
}
