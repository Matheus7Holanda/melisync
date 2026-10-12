package com.example.demo.dto.vhsys;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Um item da lista enviada em POST /vendas-balcao/{id_frente}/produtos.
 * valor_unit_produto deve ser o mesmo valor unitario recebido no Mercado
 * Livre para aquele SKU (rateado, ver RateioValorRecebido) -- sem descontos aplicados aqui (o desconto FLEX
 * fica so no cabecalho da venda, ver VhsysVendaBalcaoRequestDTO).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VhsysItemVendaBalcaoDTO(
        @JsonProperty("qtde_produto") Integer qtdeProduto,
        @JsonProperty("id_produto") Long idProduto,
        @JsonProperty("cod_produto") String codProduto,
        @JsonProperty("valor_unit_produto") BigDecimal valorUnitProduto,
        @JsonProperty("desc_produto") String descProduto
) {
}
