package com.example.demo.dto.vhsys;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * TODO: confirmar os nomes reais dos campos retornados pela API do VHSYS.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VhsysProdutoDTO(
        Long id,
        String codigo,
        String nome,
        String situacao
) {
}
