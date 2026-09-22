package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mercadolivre")
public record MercadoLivreProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String sellerId,
        String apiBaseUrl
) {
}
