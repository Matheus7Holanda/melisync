package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;
import java.time.LocalDate;

/** A venda balcao unica criada no VHSYS para um dia (uma linha por dia). */
@Entity
public class VendaVhsysDia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private LocalDate dia;

    @Column(nullable = false)
    private Long idFrente;

    private Instant criadaEm;

    protected VendaVhsysDia() {
    }

    public VendaVhsysDia(LocalDate dia, Long idFrente) {
        this.dia = dia;
        this.idFrente = idFrente;
        this.criadaEm = Instant.now();
    }

    public LocalDate getDia() {
        return dia;
    }

    public Long getIdFrente() {
        return idFrente;
    }
}
