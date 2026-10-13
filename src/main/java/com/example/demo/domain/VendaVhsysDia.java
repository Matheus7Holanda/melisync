package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Venda balcao criada pela integracao no VHSYS. A mais recente (criadaEm) e a
 * candidata a receber novos envios, enquanto estiver "Em Aberto" no VHSYS.
 */
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

    /** Aponta esta linha para uma venda nova (o dia ja tinha uma venda, ja concluida). */
    public void trocarVenda(Long novoIdFrente) {
        this.idFrente = novoIdFrente;
        this.criadaEm = Instant.now();
    }

    public LocalDate getDia() {
        return dia;
    }

    public Long getIdFrente() {
        return idFrente;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }
}
