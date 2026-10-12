package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * O "item" dentro de um order_item do Mercado Livre -- representa o anuncio
 * vendido. O SKU e o campo que casamos contra o cod_produto do VHSYS.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlItemDTO(
        String id,
        String title,
        @JsonProperty("seller_sku") String sellerSku,
        @JsonProperty("seller_custom_field") String sellerCustomField
) {
    /**
     * O ML permite informar o SKU em dois campos, dependendo de como o
     * anuncio foi cadastrado: seller_sku (mais novo) ou seller_custom_field
     * (legado). Usa o que estiver preenchido, preferindo seller_sku.
     */
    public String skuEfetivo() {
        if (sellerSku != null && !sellerSku.isBlank()) {
            return sellerSku;
        }
        return sellerCustomField;
    }
}
