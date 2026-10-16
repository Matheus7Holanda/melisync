package com.example.demo.service;

import com.example.demo.domain.ComponenteKit;
import com.example.demo.domain.ItemVenda;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Troca cada linha de anuncio KIT pelas linhas das pecas que o compoem.
 *
 * Regra: o valor recebido de cada kit e repartido entre as pecas em proporcao
 * ao CUSTO de cada uma no VHSYS. E a conta "soma dos custos / valor recebido =
 * percentagem; custo da peca / percentagem = valor da peca": o resultado e o
 * mesmo que repartir o valor recebido na proporcao dos custos, e a soma das
 * pecas fecha EXATAMENTE no valor recebido do kit (centavo a centavo).
 *
 * Quantidades: a peca vem com a quantidade do SKU ("2 FJ875PC" = 2 unidades por
 * kit; sem numero = 1) multiplicada pela quantidade de kits vendidos.
 * Linhas de SKU comum (nao kit) passam sem alteracao.
 */
public final class ExpansorDeKits {

    private ExpansorDeKits() {
    }

    public static List<RateioValorRecebido.LinhaRateada> expandir(List<RateioValorRecebido.LinhaRateada> linhas) {
        List<RateioValorRecebido.LinhaRateada> resultado = new ArrayList<>();
        for (RateioValorRecebido.LinhaRateada linha : linhas) {
            if (linha.item().isKit()) {
                resultado.addAll(expandirKit(linha));
            } else {
                resultado.add(linha);
            }
        }
        return resultado;
    }

    private static List<RateioValorRecebido.LinhaRateada> expandirKit(RateioValorRecebido.LinhaRateada linha) {
        List<ComponenteKit> componentes = linha.item().getComponentesKit();
        long valorDoKitCentavos = linha.valorUnitario().movePointRight(2).longValueExact();

        // uma entrada por UNIDADE de peca dentro de UM kit
        List<Integer> componenteDaUnidade = new ArrayList<>();
        List<Long> pesos = new ArrayList<>();
        for (int c = 0; c < componentes.size(); c++) {
            ComponenteKit comp = componentes.get(c);
            long peso = componentes.size() == 1
                    ? 1L
                    : comp.custo().setScale(6, RoundingMode.HALF_UP).movePointRight(6).longValueExact();
            for (int u = 0; u < comp.quantidade(); u++) {
                componenteDaUnidade.add(c);
                pesos.add(peso);
            }
        }

        long[] partes = RateioValorRecebido.distribuirCentavos(
                valorDoKitCentavos, pesos.stream().mapToLong(Long::longValue).toArray());

        List<RateioValorRecebido.LinhaRateada> saida = new ArrayList<>();
        int k = 0;
        for (int c = 0; c < componentes.size(); c++) {
            ComponenteKit comp = componentes.get(c);
            List<Long> valores = new ArrayList<>();
            List<Integer> qtds = new ArrayList<>();
            for (int u = 0; u < comp.quantidade(); u++, k++) {
                int pos = valores.indexOf(partes[k]);
                if (pos < 0) {
                    valores.add(partes[k]);
                    qtds.add(1);
                } else {
                    qtds.set(pos, qtds.get(pos) + 1);
                }
            }
            for (int v = 0; v < valores.size(); v++) {
                int quantidadeTotal = qtds.get(v) * linha.quantidade(); // x kits vendidos
                ItemVenda peca = new ItemVenda(comp.codigo(), comp.descricao(), quantidadeTotal,
                        BigDecimal.valueOf(valores.get(v), 2));
                peca.vincularProdutoVhsys(comp.idProduto(), comp.codigo(), comp.descricao());
                saida.add(new RateioValorRecebido.LinhaRateada(peca, quantidadeTotal,
                        BigDecimal.valueOf(valores.get(v), 2)));
            }
        }
        return saida;
    }
}
