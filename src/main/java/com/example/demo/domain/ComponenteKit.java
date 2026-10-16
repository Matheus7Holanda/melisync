package com.example.demo.domain;

import java.math.BigDecimal;

/**
 * Uma peca que faz parte de um anuncio KIT: o produto do VHSYS, quantas
 * unidades dela vem em CADA kit e o custo cadastrado no VHSYS (por unidade).
 */
public record ComponenteKit(String codigo, int quantidade, Long idProduto, String descricao, BigDecimal custo) {
}
