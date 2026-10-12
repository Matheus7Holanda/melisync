package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Referencia ao envio dentro do pedido -- so o id, usado depois em
 * GET /shipments/{id}. Pode vir nulo em pedidos sem envio.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlShippingRefDTO(
        Long id
) {
}
