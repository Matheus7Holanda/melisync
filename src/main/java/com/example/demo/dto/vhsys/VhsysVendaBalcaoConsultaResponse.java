package com.example.demo.dto.vhsys;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Resposta de GET /vendas-balcao/{id_frente}: so o que a integracao precisa (o status). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VhsysVendaBalcaoConsultaResponse(
        @JsonProperty("data") Dados data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dados(
            @JsonProperty("id_frente") Long idFrente,
            @JsonProperty("status_pedido") String statusPedido
    ) {
    }
}
