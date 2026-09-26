package com.example.demo.dto.vhsys;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Corpo de POST /vendas-balcao -- cria o CABECALHO da venda consolidada do
 * dia. E aqui que entra o desconto total do dia (soma dos R$13 de cada
 * envio FLEX), no campo desconto_pedido -- sem mexer no valor unitario de
 * nenhum item. Confirmado na documentacao oficial da API VHSYS.
 */
public record VhsysVendaBalcaoRequestDTO(
        @JsonProperty("desconto_pedido") BigDecimal descontoPedido,
        @JsonProperty("valor_recebido") BigDecimal valorRecebido,
        @JsonProperty("forma_pagamento") String formaPagamento,
        @JsonProperty("status_pedido") String statusPedido,
        @JsonProperty("obs_pedido") String obsPedido
) {
}
