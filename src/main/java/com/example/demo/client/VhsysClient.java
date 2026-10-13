package com.example.demo.client;

import com.example.demo.config.VhsysProperties;
import com.example.demo.dto.vhsys.VhsysItemVendaBalcaoDTO;
import com.example.demo.dto.vhsys.VhsysListaProdutosResponse;
import com.example.demo.dto.vhsys.VhsysProdutoDTO;
import com.example.demo.dto.vhsys.VhsysVendaBalcaoConsultaResponse;
import com.example.demo.dto.vhsys.VhsysVendaBalcaoRequestDTO;
import com.example.demo.dto.vhsys.VhsysVendaBalcaoResponseDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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

        if (resposta == null || resposta.data() == null) {
            return Optional.empty();
        }
        // O VHSYS pode ter VARIOS cadastros com o mesmo codigo (duplicados antigos na lixeira).
        // So vale produto com codigo IGUAL ao SKU, Ativo e fora da lixeira ("lixeira":"Nao").
        List<VhsysProdutoDTO> validos = resposta.data().stream()
                .filter(p -> p.codProduto() != null && p.codProduto().trim().equalsIgnoreCase(sku.trim()))
                .filter(p -> "Ativo".equalsIgnoreCase(p.statusProduto()))
                .filter(p -> !"Sim".equalsIgnoreCase(p.lixeira()))
                .toList();
        if (validos.size() > 1) {
            throw new IllegalStateException("SKU " + sku + " tem " + validos.size()
                    + " produtos ativos com o mesmo codigo no VHSYS (id_produto "
                    + validos.stream().map(p -> String.valueOf(p.idProduto())).toList()
                    + "). Deixe apenas um ativo para a integracao saber qual baixar.");
        }
        return validos.stream().findFirst();
    }

    /**
     * Passo 1 do lancamento da venda: cria o cabecalho da venda balcao
     * consolidada do dia (com o desconto total FLEX ja somado) e retorna o
     * id_frente gerado, necessario para o passo 2.
     */
    public Long criarVendaBalcao(VhsysVendaBalcaoRequestDTO requestDTO) {
        VhsysVendaBalcaoResponseDTO resposta;
        try {
            resposta = vhsysWebClient.post()
                    .uri("/vendas-balcao")
                    .headers(this::adicionarHeadersDeAutenticacao)
                    .bodyValue(requestDTO)
                    .retrieve()
                    .bodyToMono(VhsysVendaBalcaoResponseDTO.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw erroVhsys("criar a venda balcao", e);
        }

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
        try {
            vhsysWebClient.post()
                    .uri("/vendas-balcao/{id_frente}/produtos", idFrente)
                    .headers(this::adicionarHeadersDeAutenticacao)
                    .bodyValue(itens)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            throw erroVhsys("adicionar produtos a venda id_frente=" + idFrente, e);
        }
    }

    /**
     * true se a venda balcao existe e ainda esta "Em Aberto" (pode receber mais produtos).
     * Venda concluida, cancelada ou excluida no VHSYS => false.
     */
    public boolean vendaBalcaoEstaAberta(Long idFrente) {
        try {
            VhsysVendaBalcaoConsultaResponse resposta = vhsysWebClient.get()
                    .uri("/vendas-balcao/{id_frente}", idFrente)
                    .headers(this::adicionarHeadersDeAutenticacao)
                    .retrieve()
                    .bodyToMono(VhsysVendaBalcaoConsultaResponse.class)
                    .block();
            return resposta != null && resposta.data() != null
                    && "Em Aberto".equalsIgnoreCase(resposta.data().statusPedido());
        } catch (WebClientResponseException e) {
            String corpo = e.getResponseBodyAsString();
            if (e.getStatusCode().value() == 404
                    || (e.getStatusCode().value() == 403 && corpo != null && corpo.contains("encontrada"))) {
                return false; // venda nao existe mais (excluida)
            }
            throw erroVhsys("consultar a venda id_frente=" + idFrente, e);
        }
    }

    /**
     * Lanca (baixa) o estoque de TODOS os produtos da venda balcao de uma vez.
     * O VHSYS recusa (422) se a venda nao tem produtos ou se o estoque ja foi lancado.
     */
    public void lancarEstoqueVendaBalcao(Long idFrente) {
        try {
            vhsysWebClient.post()
                    .uri("/vendas-balcao/{id_frente}/estoque-lancado", idFrente)
                    .headers(this::adicionarHeadersDeAutenticacao)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException e) {
            throw erroVhsys("lancar o estoque da venda id_frente=" + idFrente, e);
        }
    }

    /**
     * DIAGNOSTICO (somente leitura): devolve o JSON bruto de um GET do VHSYS.
     * Aceita apenas caminhos de /produtos e /vendas-balcao.
     */
    public String consultaBruta(String caminho) {
        if (caminho == null || !caminho.matches("^/(produtos|vendas-balcao)[A-Za-z0-9/_?=&.%-]*$")
                || caminho.contains("..")) {
            throw new IllegalArgumentException(
                    "Caminho nao permitido (use /produtos... ou /vendas-balcao...): " + caminho);
        }
        try {
            return vhsysWebClient.get()
                    .uri(caminho)
                    .headers(this::adicionarHeadersDeAutenticacao)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientResponseException e) {
            throw erroVhsys("consultar " + caminho, e);
        }
    }

    private IllegalStateException erroVhsys(String acao, WebClientResponseException e) {
        return new IllegalStateException("VHSYS recusou ao " + acao + ": HTTP "
                + e.getStatusCode().value() + " - " + e.getResponseBodyAsString(), e);
    }

    private void adicionarHeadersDeAutenticacao(HttpHeaders headers) {
        headers.add("access-token", properties.accessToken());
        headers.add("secret-access-token", properties.secretAccessToken());
        headers.add("User-Agent", properties.userAgent());
    }
}
