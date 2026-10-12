package com.example.demo.service;

import com.example.demo.dto.ml.MlOrderDTO;
import com.example.demo.dto.ml.MlOrderItemDTO;
import com.example.demo.dto.ml.MlPaymentDTO;
import com.example.demo.dto.ml.MlShipmentCostsDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Pecas do calculo do valor que o vendedor RECEBE no Mercado Livre:
 *
 *   recebido = (unit_price x quantity) - tarifa de venda - frete pago pelo vendedor
 *
 * (formula simplificada da propria documentacao do ML). O desconto FLEX de
 * R$ 13,00 NAO entra aqui: e uma regra do negocio, aplicada depois, em
 * EnvioMercadoLivre.
 */
public final class CalculoRecebimento {

    private static final String PAGAMENTO_APROVADO = "approved";

    private CalculoRecebimento() {
    }

    /** Tarifa de venda total dos pedidos de um mesmo envio. */
    public static BigDecimal tarifaDeVenda(List<MlOrderDTO> pedidos) {
        BigDecimal total = BigDecimal.ZERO;
        if (pedidos != null) {
            for (MlOrderDTO pedido : pedidos) {
                total = total.add(tarifaDoPedido(pedido));
            }
        }
        return arredondar(total);
    }

    /**
     * Tarifa do pedido: soma o marketplace_fee dos pagamentos aprovados. Se
     * nao houver, cai na tarifa por unidade (sale_fee) x quantidade.
     */
    static BigDecimal tarifaDoPedido(MlOrderDTO pedido) {
        if (pedido == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal dosPagamentos = BigDecimal.ZERO;
        boolean achou = false;
        if (pedido.payments() != null) {
            for (MlPaymentDTO pagamento : pedido.payments()) {
                if (pagamento != null
                        && PAGAMENTO_APROVADO.equals(pagamento.status())
                        && pagamento.marketplaceFee() != null) {
                    dosPagamentos = dosPagamentos.add(pagamento.marketplaceFee());
                    achou = true;
                }
            }
        }
        if (achou) {
            return arredondar(dosPagamentos);
        }

        BigDecimal porUnidade = BigDecimal.ZERO;
        if (pedido.orderItems() != null) {
            for (MlOrderItemDTO item : pedido.orderItems()) {
                if (item != null && item.saleFee() != null && item.quantity() != null) {
                    porUnidade = porUnidade.add(item.saleFee().multiply(BigDecimal.valueOf(item.quantity())));
                }
            }
        }
        return arredondar(porUnidade);
    }

    /** Custo de frete que ESTE vendedor paga no envio (vazio se nao constar). */
    public static Optional<BigDecimal> custoFreteDoVendedor(MlShipmentCostsDTO custos, Long sellerId) {
        if (custos == null || custos.senders() == null || sellerId == null) {
            return Optional.empty();
        }
        for (MlShipmentCostsDTO.Sender sender : custos.senders()) {
            if (sender != null && sellerId.equals(sender.userId()) && sender.cost() != null) {
                return Optional.of(arredondar(sender.cost()));
            }
        }
        return Optional.empty();
    }

    // A API devolve ruido de ponto flutuante (ex: 14.290000000000001).
    private static BigDecimal arredondar(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
