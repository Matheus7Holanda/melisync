package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Um item de um pedido do Mercado Livre: o anuncio vendido, a quantidade
 * e o valor unitario cobrado do comprador. sale_fee e a tarifa de venda
 * POR UNIDADE.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlOrderItemDTO(
        MlItemDTO item,
        Integer quantity,
        @JsonProperty("unit_price") BigDecimal unitPrice,
        @JsonProperty("sale_fee") BigDecimal saleFee
) {
}
