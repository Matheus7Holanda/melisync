package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Um pagamento do pedido (campo "payments" de GET /orders/search).
 * marketplace_fee e a tarifa de venda cobrada do vendedor (totalizada no pedido).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlPaymentDTO(
        Long id,
        String status,
        @JsonProperty("transaction_amount") BigDecimal transactionAmount,
        @JsonProperty("total_paid_amount") BigDecimal totalPaidAmount,
        @JsonProperty("marketplace_fee") BigDecimal marketplaceFee
) {
}
