package com.example.demo.repository;

import com.example.demo.domain.VendaVhsysDia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface VendaVhsysDiaRepository extends JpaRepository<VendaVhsysDia, Long> {
    Optional<VendaVhsysDia> findByDia(LocalDate dia);
}
