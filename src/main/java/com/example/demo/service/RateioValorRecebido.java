package com.example.demo.service;

import com.example.demo.domain.EnvioMercadoLivre;
import com.example.demo.domain.ItemVenda;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Distribui o valor liquido de um envio (valor recebido do ML menos o
 * desconto FLEX, quando houver) entre as pecas, proporcionalmente ao preco
 * de venda de cada peca. Ou seja: tarifa, frete e desconto FLEX sao
 * divididos entre as pecas na proporcao do preco.
 *
 * O calculo e feito em centavos, UNIDADE POR UNIDADE, e as sobras de
 * centavo vao para as unidades com maior resto. Assim a soma das linhas
 * fecha EXATAMENTE no valor liquido do envio. Unidades do mesmo item que
 * ficam com valores diferentes (diferenca de 1 centavo) viram linhas
 * separadas, porque o VHSYS recebe um unico valor unitario por linha.
 */
public final class RateioValorRecebido {

    private RateioValorRecebido() {
    }

    /** Uma linha pronta para o VHSYS: o item, a quantidade e o valor de cada unidade. */
    public record LinhaRateada(ItemVenda item, int quantidade, BigDecimal valorUnitario) {
        public BigDecimal valorTotal() {
            return valorUnitario.multiply(BigDecimal.valueOf(quantidade));
        }
    }

    public static List<LinhaRateada> ratear(EnvioMercadoLivre envio) {
        return ratear(envio.getItens(), envio.getValorLiquido());
    }

    public static List<LinhaRateada> ratear(List<ItemVenda> itens, BigDecimal valorAlvo) {
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Envio sem itens: nada para ratear.");
        }
        if (valorAlvo.signum() < 0) {
            throw new IllegalArgumentException("Valor liquido negativo (" + valorAlvo
                    + "): tarifa, frete e desconto superam o valor dos itens. Revisar o envio.");
        }

        // uma entrada por unidade vendida
        List<Integer> itemDaUnidade = new ArrayList<>();
        List<Long> pesoCentavos = new ArrayList<>();
        BigInteger pesoTotal = BigInteger.ZERO;
        for (int i = 0; i < itens.size(); i++) {
            ItemVenda item = itens.get(i);
            long peso = item.getValorUnitarioRecebido().movePointRight(2).longValueExact();
            for (int u = 0; u < item.getQuantidade(); u++) {
                itemDaUnidade.add(i);
                pesoCentavos.add(peso);
                pesoTotal = pesoTotal.add(BigInteger.valueOf(peso));
            }
        }
        if (pesoTotal.signum() == 0) {
            throw new IllegalArgumentException("Itens com valor zero: impossivel ratear.");
        }

        long alvo = valorAlvo.movePointRight(2).longValueExact();
        long[] pesos = pesoCentavos.stream().mapToLong(Long::longValue).toArray();
        long[] parte = distribuirCentavos(alvo, pesos);

        // agrupa unidades iguais do mesmo item em linhas
        List<LinhaRateada> linhas = new ArrayList<>();
        int k = 0;
        for (int i = 0; i < itens.size(); i++) {
            ItemVenda item = itens.get(i);
            List<Long> valores = new ArrayList<>();
            List<Integer> qtds = new ArrayList<>();
            for (int u = 0; u < item.getQuantidade(); u++, k++) {
                int pos = valores.indexOf(parte[k]);
                if (pos < 0) {
                    valores.add(parte[k]);
                    qtds.add(1);
                } else {
                    qtds.set(pos, qtds.get(pos) + 1);
                }
            }
            for (int v = 0; v < valores.size(); v++) {
                linhas.add(new LinhaRateada(item, qtds.get(v),
                        BigDecimal.valueOf(valores.get(v), 2)));
            }
        }
        return linhas;
    }

    /**
     * Reparte "alvo" centavos entre os pesos, proporcionalmente, sem sobrar nem
     * faltar centavo: as sobras vao para quem tem maior resto (empate: ordem original).
     */
    public static long[] distribuirCentavos(long alvo, long[] pesos) {
        BigInteger pesoTotal = BigInteger.ZERO;
        for (long p : pesos) {
            pesoTotal = pesoTotal.add(BigInteger.valueOf(p));
        }
        if (pesoTotal.signum() == 0) {
            throw new IllegalArgumentException("Pesos zerados: impossivel ratear.");
        }
        int n = pesos.length;
        long[] parte = new long[n];
        BigInteger[] resto = new BigInteger[n];
        long somaPartes = 0;
        for (int k = 0; k < n; k++) {
            BigInteger[] qr = BigInteger.valueOf(alvo)
                    .multiply(BigInteger.valueOf(pesos[k]))
                    .divideAndRemainder(pesoTotal);
            parte[k] = qr[0].longValueExact();
            resto[k] = qr[1];
            somaPartes += parte[k];
        }
        long sobra = alvo - somaPartes;
        List<Integer> ordem = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            ordem.add(k);
        }
        ordem.sort((a, b) -> {
            int c = resto[b].compareTo(resto[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        for (int s = 0; s < sobra; s++) {
            parte[ordem.get(s)]++;
        }
        return parte;
    }
}
