package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TODO: confirmar com o suporte VHSYS se access-token/secret-access-token
 * vao em query param (como esta implementado hoje em VhsysClient) ou em
 * header, e o path exato do endpoint de PDV/venda.
 */
@ConfigurationProperties(prefix = "vhsys")
public record VhsysProperties(
        String accessToken,
        String secretAccessToken,
        String apiBaseUrl,
        Long idPdv
) {
}
