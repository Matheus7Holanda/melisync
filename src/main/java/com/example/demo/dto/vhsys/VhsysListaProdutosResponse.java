package com.example.demo.dto.vhsys;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope de resposta de GET /produtos -- a API do VHSYS embrulha a lista
 * de produtos dentro de um objeto com code/status/paging/data, confirmado
 * na documentacao oficial.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VhsysListaProdutosResponse(
        int code,
        String status,
        @JsonProperty("data") List<VhsysProdutoDTO> data
) {
}
