package com.example.demo.service;

import com.example.demo.domain.ItemVenda;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Junta as linhas do MESMO produto do VHSYS numa so, somando as quantidades.
 * Ex.: 1 x 22,19 + 1 x 22,18 do mesmo codigo viram 2 x 22,185 (total 44,37).
 *
 * O VHSYS aceita ate 6 casas no valor unitario. Se mesmo assim valor unitario x
 * quantidade nao reproduzir o total ao centavo, o produto NAO e agrupado
 * (as linhas ficam separadas) para o total da venda nunca sair errado.
 */
public final class AgrupadorDeLinhas {

    private AgrupadorDeLinhas() {
    }

    public record LinhaAgrupada(Long idProduto, String codigo, String descricao,
                                int quantidade, BigDecimal valorUnitario) {
        public BigDecimal valorTotal() {
            return valorUnitario.multiply(BigDecimal.valueOf(quantidade));
        }
    }

    public static List<LinhaAgrupada> agrupar(List<RateioValorRecebido.LinhaRateada> linhas) {
        Map<Long, List<RateioValorRecebido.LinhaRateada>> porProduto = new LinkedHashMap<>();
        for (RateioValorRecebido.LinhaRateada l : linhas) {
            porProduto.computeIfAbsent(l.item().getProdutoIdVhsys(), k -> new ArrayList<>()).add(l);
        }

        List<LinhaAgrupada> resultado = new ArrayList<>();
        for (Map.Entry<Long, List<RateioValorRecebido.LinhaRateada>> e : porProduto.entrySet()) {
            List<RateioValorRecebido.LinhaRateada> grupo = e.getValue();
            ItemVenda primeiro = grupo.get(0).item();

            int qtd = 0;
            BigDecimal total = BigDecimal.ZERO;
            for (RateioValorRecebido.LinhaRateada l : grupo) {
                qtd += l.quantidade();
                total = total.add(l.valorTotal());
            }
            BigDecimal unitario = total.divide(BigDecimal.valueOf(qtd), 6, RoundingMode.HALF_UP);
            boolean fecha = unitario.multiply(BigDecimal.valueOf(qtd))
                    .setScale(2, RoundingMode.HALF_UP).compareTo(total) == 0;

            if (fecha) {
                resultado.add(new LinhaAgrupada(e.getKey(), primeiro.getCodProdutoVhsys(),
                        descricao(primeiro), qtd, unitario));
            } else {
                for (RateioValorRecebido.LinhaRateada l : grupo) {
                    resultado.add(new LinhaAgrupada(e.getKey(), l.item().getCodProdutoVhsys(),
                            descricao(l.item()), l.quantidade(), l.valorUnitario()));
                }
            }
        }
        return resultado;
    }

    /** Nome do produto CADASTRADO no VHSYS; so cai no titulo do ML se o cadastro nao tiver nome. */
    private static String descricao(ItemVenda item) {
        String d = item.getDescProdutoVhsys();
        return d != null && !d.isBlank() ? d : item.getTitulo();
    }
}
