package com.example.demo.dto.vhsys;

import java.math.BigDecimal;
import java.util.List;

/**
 * TODO: este schema e um RASCUNHO. Precisa ser validado contra a doc real
 * da sua conta VHSYS antes de producao.
 */
public record VhsysPdvRequestDTO(
        Long idPdv,
        List<ItemPdvDTO> produtos,
        BigDecimal valorDesconto,
        String observacao
) {

    public record ItemPdvDTO(
            Long idProduto,
            Integer quantidade,
            BigDecimal valorUnitario
    ) {
    }
}
