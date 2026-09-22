package com.example.demo.client;

import com.example.demo.config.VhsysProperties;
import com.example.demo.dto.vhsys.VhsysPdvRequestDTO;
import com.example.demo.dto.vhsys.VhsysProdutoDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;

/**
 * TODO: confirmar com o suporte VHSYS o path real dos endpoints de produto
 * e de PDV/venda, e se a autenticacao vai em query param (como esta agora)
 * ou em header.
 */
@Component
public class VhsysClient {

    private final WebClient vhsysWebClient;
    private final VhsysProperties properties;

    public VhsysClient(WebClient vhsysWebClient, VhsysProperties properties) {
        this.vhsysWebClient = vhsysWebClient;
        this.properties = properties;
    }

    public Optional<VhsysProdutoDTO> buscarProdutoPorSku(String sku) {
        VhsysProdutoDTO[] resultado = vhsysWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/produtos")
                        .queryParam("access_token", properties.accessToken())
                        .queryParam("secret_access_token", properties.secretAccessToken())
                        .queryParam("codigo", sku)
                        .build())
                .retrieve()
                .bodyToMono(VhsysProdutoDTO[].class)
                .block();

        if (resultado == null || resultado.length == 0) {
            return Optional.empty();
        }
        return Optional.of(resultado[0]);
    }

    public void enviarVendaConsolidada(VhsysPdvRequestDTO requestDTO) {
        vhsysWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/pdv/vendas")
                        .queryParam("access_token", properties.accessToken())
                        .queryParam("secret_access_token", properties.secretAccessToken())
                        .build())
                .bodyValue(requestDTO)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public List<VhsysProdutoDTO> buscarProdutosPorSkus(List<String> skus) {
        throw new UnsupportedOperationException(
                "Implementar quando confirmar se a API do VHSYS suporta busca em lote por codigo."
        );
    }
}
