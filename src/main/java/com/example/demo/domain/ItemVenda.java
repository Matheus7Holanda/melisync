package com.example.demo.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um item vendido no Mercado Livre, ja traduzido para o modelo interno.
 * valorUnitarioRecebido e exatamente o valor do ML -- NUNCA e alterado por
 * descontos (o desconto FLEX vive no nivel do envio, ver EnvioMercadoLivre).
 *
 * produtoIdVhsys fica nulo aqui; e preenchido no proximo passo (mapeamento
 * de SKU para o id_produto do VHSYS).
 */
public class ItemVenda {

    private final String sku;
    private final String titulo;
    private final int quantidade;
    private final BigDecimal valorUnitarioRecebido;
    private Long produtoIdVhsys;
    private String codProdutoVhsys;
    private String descProdutoVhsys;
    /** Preenchido so quando o SKU do anuncio e um KIT (varios codigos no mesmo anuncio). */
    private List<ComponenteKit> componentesKit;

    public ItemVenda(String sku, String titulo, int quantidade, BigDecimal valorUnitarioRecebido) {
        this.sku = sku;
        this.titulo = titulo;
        this.quantidade = quantidade;
        this.valorUnitarioRecebido = valorUnitarioRecebido;
    }

    public BigDecimal getValorTotal() {
        return valorUnitarioRecebido.multiply(BigDecimal.valueOf(quantidade));
    }

    public String getSku() {
        return sku;
    }

    public String getTitulo() {
        return titulo;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public BigDecimal getValorUnitarioRecebido() {
        return valorUnitarioRecebido;
    }

    public Long getProdutoIdVhsys() {
        return produtoIdVhsys;
    }

    public void setProdutoIdVhsys(Long produtoIdVhsys) {
        this.produtoIdVhsys = produtoIdVhsys;
    }

    public String getCodProdutoVhsys() {
        return codProdutoVhsys;
    }

    public String getDescProdutoVhsys() {
        return descProdutoVhsys;
    }

    /** Dados do cadastro do VHSYS, para o item ficar vinculado ao produto cadastrado. */
    public void vincularProdutoVhsys(Long id, String codigo, String descricao) {
        this.produtoIdVhsys = id;
        this.codProdutoVhsys = codigo;
        this.descProdutoVhsys = descricao;
    }

    public boolean isKit() {
        return componentesKit != null;
    }

    public List<ComponenteKit> getComponentesKit() {
        return componentesKit;
    }

    public void definirComponentesKit(List<ComponenteKit> componentes) {
        this.componentesKit = List.copyOf(componentes);
    }
}
