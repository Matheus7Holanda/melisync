package com.example.demo.service;

import com.example.demo.client.VhsysClient;
import com.example.demo.domain.ComponenteKit;
import com.example.demo.domain.EnvioMercadoLivre;
import com.example.demo.domain.ItemVenda;
import com.example.demo.dto.vhsys.VhsysProdutoDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Passo 5: liga cada SKU do Mercado Livre (seller_sku) ao produto do VHSYS
 * (cod_produto), preenchendo ItemVenda.produtoIdVhsys.
 *
 * SKU com varios codigos (kit, ex. "2 FJ875PC e FJ1150PC"): cada codigo e
 * procurado no VHSYS e o item guarda as pecas (ComponenteKit) com o custo de
 * cada uma, usado depois para repartir o valor recebido.
 *
 * SKU sem produto no VHSYS (ou, em kit, sem custo cadastrado) NAO derruba o
 * processo: vira pendencia no resultado, para o usuario corrigir. Erro de
 * comunicacao com o VHSYS, por outro lado, sobe como excecao.
 */
@Service
public class MapeamentoSkuService {

    /** skusEncontrados: sku -> id_produto. skusPendentes: o que impede o envio. */
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
        Map<String, Long> encontrados = new LinkedHashMap<>();
        Set<String> pendentes = new LinkedHashSet<>();

        for (EnvioMercadoLivre envio : envios) {
            for (ItemVenda item : envio.getItens()) {
                mapearItem(item, cache, encontrados, pendentes);
            }
        }
        return new Resultado(encontrados, new ArrayList<>(pendentes));
    }

    private void mapearItem(ItemVenda item, Map<String, Optional<VhsysProdutoDTO>> cache,
                            Map<String, Long> encontrados, Set<String> pendentes) {
        String sku = item.getSku();
        if (sku == null || sku.isBlank()) {
            pendentes.add("(sem SKU no ML: " + item.getTitulo() + ")");
            return;
        }

        List<InterpretadorSku.ParteSku> partes;
        try {
            partes = InterpretadorSku.interpretar(sku);
        } catch (IllegalArgumentException e) {
            pendentes.add("SKU ilegivel: '" + sku + "'");
            return;
        }

        if (InterpretadorSku.ehSimples(partes)) {
            String codigo = partes.get(0).codigo();
            Optional<VhsysProdutoDTO> produto = cache.computeIfAbsent(codigo, vhsysClient::buscarProdutoPorSku);
            if (produto.isPresent()) {
                VhsysProdutoDTO p = produto.get();
                item.vincularProdutoVhsys(p.idProduto(), p.codProduto(), p.descProduto());
                encontrados.put(codigo, p.idProduto());
            } else {
                pendentes.add(codigo);
            }
            return;
        }

        // KIT: varias pecas (ou uma peca com quantidade maior que 1)
        List<ComponenteKit> componentes = new ArrayList<>();
        boolean ok = true;
        for (InterpretadorSku.ParteSku parte : partes) {
            Optional<VhsysProdutoDTO> produto = cache.computeIfAbsent(parte.codigo(), vhsysClient::buscarProdutoPorSku);
            if (produto.isEmpty()) {
                pendentes.add(parte.codigo());
                ok = false;
                continue;
            }
            VhsysProdutoDTO p = produto.get();
            BigDecimal custo = lerCusto(p.valorCustoProduto());
            if (partes.size() > 1 && custo.signum() <= 0) {
                pendentes.add(parte.codigo() + " (sem custo cadastrado no VHSYS; necessario para dividir o kit)");
                ok = false;
                continue;
            }
            encontrados.put(parte.codigo(), p.idProduto());
            componentes.add(new ComponenteKit(p.codProduto(), parte.quantidade(),
                    p.idProduto(), p.descProduto(), custo));
        }
        if (ok) {
            item.definirComponentesKit(componentes);
        }
    }

    private static BigDecimal lerCusto(String texto) {
        if (texto == null || texto.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
