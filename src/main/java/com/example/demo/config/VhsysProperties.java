package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mapeia o bloco "vhsys:" do application.properties.
 *
 * Confirmado na documentacao oficial da API VHSYS (base URL
 * https://api.vhsys.com/v2): access-token e secret-access-token sao
 * enviados como HEADERS em toda requisicao (nao query param), e User-Agent
 * e obrigatorio. Nao existe conceito de "id_pdv" na API -- uma venda balcao
 * e criada em duas chamadas encadeadas: POST /vendas-balcao (cabecalho)
 * e depois POST /vendas-balcao/{id_frente}/produtos (itens).
 */
@ConfigurationProperties(prefix = "vhsys")
public record VhsysProperties(
        String accessToken,
        String secretAccessToken,
        String apiBaseUrl,
        String userAgent
) {
}
