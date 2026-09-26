package com.example.demo.client;

import com.example.demo.config.VhsysProperties;
import com.example.demo.dto.vhsys.VhsysItemVendaBalcaoDTO;
import com.example.demo.dto.vhsys.VhsysListaProdutosResponse;
import com.example.demo.dto.vhsys.VhsysProdutoDTO;
import com.example.demo.dto.vhsys.VhsysVendaBalcaoRequestDTO;
import com.example.demo.dto.vhsys.VhsysVendaBalcaoResponseDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;

/**
 * Cliente HTTP para a API do VHSYS (base URL https://api.vhsys.com/v2,
 * confirmada na documentacao oficial).
 *
 * Autenticacao: access-token e secret-access-token vao como HEADERS (nao
 * query param), e User-Agent e obrigatorio em toda chamada.
 *
 * Criar uma venda balcao e um fluxo em DUAS chamadas encadeadas:
 *   1) criarVendaBalcao(...)          -> POST /vendas-balcao (cabecalho, com o
 *                                         desconto total do dia)
 *   2) adicionarProdutosVendaBalcao(...) -> POST /vendas-balcao/{id_frente}/produtos
 *                                         (itens, sem desconto por item)
 */
@Component
public class VhsysClient {

    private final WebClient vhsysWebClient;
    private final VhsysProperties properties;

    public VhsysClient(WebClient vhsysWebClient, VhsysProperties properties) {
        this.vhsysWebClient = vhsysWebClient;
        this.properties = properties;
    }

    /**
     * Busca um produto cadastrado no VHSYS pelo codigo/SKU (cod_produto).
     * Retorna Optional.empty() se nao encontrar nenhum produto com esse codigo.
     */
    public Optional<VhsysProdutoDTO> buscarProdutoPorSku(String sku) {
        VhsysListaProdutosResponse resposta = vhsysWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/produtos")
                        .queryParam("cod_produto", sku)
                        .build())
                .headers(this::adicionarHeadersDeAutenticacao)
                .retrieve()
                .bodyToMono(VhsysListaProdutosResponse.class)
                .block();

        if (resposta == null || resposta.data() == null || resposta.data().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(resposta.data().get(0));
    }

    /**
     * Passo 1 do lancamento da venda: cria o cabecalho da venda balcao
     * consolidada do dia (com o desconto total FLEX ja somado) e retorna o
     * id_frente gerado, necessario para o passo 2.
     */
    public Long criarVendaBalcao(VhsysVendaBalcaoRequestDTO requestDTO) {
        VhsysVendaBalcaoResponseDTO resposta = vhsysWebClient.post()
                .uri("/vendas-balcao")
                .headers(this::adicionarHeadersDeAutenticacao)
                .bodyValue(requestDTO)
                .retrieve()
                .bodyToMono(VhsysVendaBalcaoResponseDTO.class)
                .block();

        if (resposta == null || resposta.data() == null || resposta.data().idFrente() == null) {
            throw new IllegalStateException("VHSYS nao retornou id_frente ao criar a venda balcao.");
        }
        return resposta.data().idFrente();
    }

    /**
     * Passo 2 do lancamento da venda: anexa a lista de itens (um por SKU
     * vendido no dia) a venda balcao criada no passo 1.
     */
    public void adicionarProdutosVendaBalcao(Long idFrente, List<VhsysItemVendaBalcaoDTO> itens) {
        vhsysWebClient.post()
                .uri("/vendas-balcao/{id_frente}/produtos", idFrente)
                .headers(this::adicionarHeadersDeAutenticacao)
                .bodyValue(itens)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private void adicionarHeadersDeAutenticacao(HttpHeaders headers) {
        headers.add("access-token", properties.accessToken());
        headers.add("secret-access-token", properties.secretAccessToken());
        headers.add("User-Agent", properties.userAgent());
    }
}
