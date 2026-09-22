package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient mercadoLivreWebClient(MercadoLivreProperties props) {
        return WebClient.builder()
                .baseUrl(props.apiBaseUrl())
                .build();
    }

    @Bean
    public WebClient vhsysWebClient(VhsysProperties props) {
        return WebClient.builder()
                .baseUrl(props.apiBaseUrl())
                .build();
    }
}
