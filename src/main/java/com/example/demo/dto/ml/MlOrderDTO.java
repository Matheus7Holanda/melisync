package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um pedido retornado por GET /orders/search.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlOrderDTO(
        Long id,
        String status,
        @JsonProperty("date_created") String dateCreated,
        @JsonProperty("total_amount") BigDecimal totalAmount,
        @JsonProperty("order_items") List<MlOrderItemDTO> orderItems,
        MlShippingRefDTO shipping,
        List<MlPaymentDTO> payments
) {
}
