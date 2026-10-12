package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta de GET /shipments/{id}/costs. "senders" lista o custo de frete
 * de cada vendedor (um envio pode ter produtos de vendedores diferentes);
 * "cost" e o custo FINAL que aquele vendedor paga, ja com descontos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlShipmentCostsDTO(
        @JsonProperty("gross_amount") BigDecimal grossAmount,
        List<Sender> senders
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sender(
            @JsonProperty("user_id") Long userId,
            BigDecimal cost
    ) {
    }
}
