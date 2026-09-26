package com.example.demo.dto.vhsys;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa um produto retornado por GET /produtos na API do VHSYS.
 * Campos confirmados na documentacao oficial (Default_module.md).
 *
 * cod_produto e o campo que casamos contra o seller_sku do Mercado Livre;
 * id_produto e o identificador interno do VHSYS, usado depois para lancar
 * o item na venda balcao.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VhsysProdutoDTO(
        @JsonProperty("id_produto") Long idProduto,
        @JsonProperty("cod_produto") String codProduto,
        @JsonProperty("desc_produto") String descProduto,
        @JsonProperty("valor_produto") String valorProduto,
        @JsonProperty("status_produto") String statusProduto
) {
}
