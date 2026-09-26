package com.example.demo.dto.vhsys;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta de POST /vendas-balcao. O campo mais importante e id_frente --
 * e o identificador da venda balcao recem-criada, usado na chamada seguinte
 * para anexar os produtos (POST /vendas-balcao/{id_frente}/produtos).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VhsysVendaBalcaoResponseDTO(
        int code,
        String status,
        @JsonProperty("data") DadosVendaBalcao data
) {
    public record DadosVendaBalcao(
            @JsonProperty("id_frente") Long idFrente,
            @JsonProperty("id_pedido") Long idPedido
    ) {
    }
}
