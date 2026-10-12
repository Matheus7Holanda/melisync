package com.example.demo.dto.vhsys;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corpo de POST /vendas-balcao -- cria o CABECALHO da venda consolidada do
 * dia. Campos nulos nao sao enviados. O valor de cada peca (ja com tarifa,
 * frete e desconto FLEX rateados) vai nos itens, entao o cabecalho nao leva
 * desconto.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VhsysVendaBalcaoRequestDTO(
        @JsonProperty("nome_cliente") String nomeCliente,
        @JsonProperty("status_pedido") String statusPedido,
        @JsonProperty("obs_pedido") String obsPedido
) {
}
