package com.example.demo.service;

import com.example.demo.client.VhsysClient;
import com.example.demo.domain.EnvioMercadoLivre;
import com.example.demo.domain.ItemVenda;
import com.example.demo.dto.vhsys.VhsysProdutoDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Passo 5: liga cada SKU do Mercado Livre (seller_sku) ao produto do VHSYS
 * (cod_produto), preenchendo ItemVenda.produtoIdVhsys.
 *
 * SKU sem produto no VHSYS NAO derruba o processo: vira pendencia no
 * resultado, para o usuario cadastrar o produto. Erro de comunicacao com o
 * VHSYS, por outro lado, sobe como excecao (nunca vira "nao encontrado").
 */
@Service
public class MapeamentoSkuService {

    /** skusEncontrados: sku -> id_produto. skusPendentes: sem cadastro no VHSYS ou sem SKU no ML. */
    public record Resultado(Map<String, Long> skusEncontrados, List<String> skusPendentes) {
        public boolean completo() {
            return skusPendentes.isEmpty();
        }
    }

    private final VhsysClient vhsysClient;

    public MapeamentoSkuService(VhsysClient vhsysClient) {
        this.vhsysClient = vhsysClient;
    }

    public Resultado mapear(List<EnvioMercadoLivre> envios) {
        Map<String, Optional<VhsysProdutoDTO>> cache = new HashMap<>();
        Map<String, Long> encontrados = new java.util.LinkedHashMap<>();
        Set<String> pendentes = new LinkedHashSet<>();

        for (EnvioMercadoLivre envio : envios) {
            for (ItemVenda item : envio.getItens()) {
                String sku = item.getSku();
                if (sku == null || sku.isBlank()) {
                    pendentes.add("(sem SKU no ML: " + item.getTitulo() + ")");
                    continue;
                }
                Optional<VhsysProdutoDTO> produto = cache.computeIfAbsent(sku.trim(),
                        vhsysClient::buscarProdutoPorSku);
                if (produto.isPresent()) {
                    VhsysProdutoDTO p = produto.get();
                    item.vincularProdutoVhsys(p.idProduto(), p.codProduto(), p.descProduto());
                    encontrados.put(sku.trim(), p.idProduto());
                } else {
                    pendentes.add(sku.trim());
                }
            }
        }
        return new Resultado(encontrados, new ArrayList<>(pendentes));
    }

}
