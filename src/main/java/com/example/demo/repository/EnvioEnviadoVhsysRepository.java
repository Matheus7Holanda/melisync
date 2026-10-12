package com.example.demo.repository;

import com.example.demo.domain.EnvioEnviadoVhsys;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EnvioEnviadoVhsysRepository extends JpaRepository<EnvioEnviadoVhsys, Long> {
    List<EnvioEnviadoVhsys> findByShippingIdIn(Collection<Long> shippingIds);
}
