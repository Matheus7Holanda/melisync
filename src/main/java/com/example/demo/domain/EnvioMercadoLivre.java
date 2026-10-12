package com.example.demo.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Um ENVIO (shipment) do Mercado Livre. Um carrinho com varios produtos gera
 * varios pedidos que compartilham o mesmo envio -- por isso o envio pode ter
 * mais de um orderId.
 *
 * Valores:
 *   valorTotalItens   = soma de (valor unitario x quantidade), preco de venda
 *   tarifaVenda       = comissao do Mercado Livre
 *   custoFreteVendedor= frete pago pelo vendedor
 *   valorRecebidoMl   = valorTotalItens - tarifaVenda - custoFreteVendedor
 *   descontoFlex      = R$ 13,00 UMA vez por envio FLEX (regra do negocio)
 *   valorLiquido      = valorRecebidoMl - descontoFlex  (o que vai para o VHSYS)
 *
 * Os valores unitarios dos itens continuam iguais aos do Mercado Livre.
 */
public class EnvioMercadoLivre {

    public static final BigDecimal DESCONTO_FLEX = new BigDecimal("13.00");

    private final Long shippingId;
    private final List<Long> orderIds;
    private final boolean flex;
    private final OffsetDateTime dataSaida;
    private final List<ItemVenda> itens;
    private final BigDecimal tarifaVenda;
    private final BigDecimal custoFreteVendedor;
    private final List<String> avisos;

    public EnvioMercadoLivre(Long shippingId, List<Long> orderIds, boolean flex,
                             OffsetDateTime dataSaida, List<ItemVenda> itens,
                             BigDecimal tarifaVenda, BigDecimal custoFreteVendedor,
                             List<String> avisos) {
        this.shippingId = shippingId;
        this.orderIds = orderIds;
        this.flex = flex;
        this.dataSaida = dataSaida;
        this.itens = itens;
        this.tarifaVenda = tarifaVenda;
        this.custoFreteVendedor = custoFreteVendedor;
        this.avisos = avisos;
    }

    /** Sem tarifa nem frete (usado em testes simples). */
    public EnvioMercadoLivre(Long shippingId, List<Long> orderIds, boolean flex,
                             OffsetDateTime dataSaida, List<ItemVenda> itens) {
        this(shippingId, orderIds, flex, dataSaida, itens,
                BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    public BigDecimal getValorTotalItens() {
        return itens.stream()
                .map(ItemVenda::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getValorRecebidoMl() {
        return getValorTotalItens().subtract(tarifaVenda).subtract(custoFreteVendedor);
    }

    public BigDecimal getDescontoFlex() {
        return flex ? DESCONTO_FLEX : BigDecimal.ZERO;
    }

    public BigDecimal getValorLiquido() {
        return getValorRecebidoMl().subtract(getDescontoFlex());
    }

    public Long getShippingId() {
        return shippingId;
    }

    public List<Long> getOrderIds() {
        return orderIds;
    }

    public boolean isFlex() {
        return flex;
    }

    public OffsetDateTime getDataSaida() {
        return dataSaida;
    }

    public List<ItemVenda> getItens() {
        return itens;
    }

    public BigDecimal getTarifaVenda() {
        return tarifaVenda;
    }

    public BigDecimal getCustoFreteVendedor() {
        return custoFreteVendedor;
    }

    public List<String> getAvisos() {
        return avisos;
    }
}
