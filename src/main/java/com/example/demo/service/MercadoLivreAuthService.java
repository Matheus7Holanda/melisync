package com.example.demo.service;

import com.example.demo.config.MercadoLivreProperties;
import com.example.demo.domain.MlOAuthToken;
import com.example.demo.dto.ml.MlTokenResponse;
import com.example.demo.repository.MlOAuthTokenRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Optional;

@Service
public class MercadoLivreAuthService {

    private final WebClient mercadoLivreWebClient;
    private final MercadoLivreProperties properties;
    private final MlOAuthTokenRepository tokenRepository;

    public MercadoLivreAuthService(
            WebClient mercadoLivreWebClient,
            MercadoLivreProperties properties,
            MlOAuthTokenRepository tokenRepository
    ) {
        this.mercadoLivreWebClient = mercadoLivreWebClient;
        this.properties = properties;
        this.tokenRepository = tokenRepository;
    }

    public void trocarCodePorToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());

        MlTokenResponse response = mercadoLivreWebClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(MlTokenResponse.class)
                .block();

        salvarOuAtualizarToken(response);
    }

    public String obterAccessTokenValido() {
        MlOAuthToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhum token do Mercado Livre encontrado. "
                        + "Acesse /auth/mercadolivre/iniciar e complete o fluxo OAuth2 primeiro."
                ));

        if (token.expirado()) {
            token = renovarToken(token);
        }

        return token.getAccessToken();
    }

    private MlOAuthToken renovarToken(MlOAuthToken tokenAtual) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("refresh_token", tokenAtual.getRefreshToken());

        MlTokenResponse response = mercadoLivreWebClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(MlTokenResponse.class)
                .block();

        return salvarOuAtualizarToken(response);
    }

    private MlOAuthToken salvarOuAtualizarToken(MlTokenResponse response) {
        Instant expiraEm = Instant.now().plusSeconds(response.expiresInSeconds());

        Optional<MlOAuthToken> existente = tokenRepository.findAll().stream().findFirst();

        MlOAuthToken token = existente
                .map(t -> {
                    t.atualizar(response.accessToken(), response.refreshToken(), expiraEm);
                    return t;
                })
                .orElseGet(() -> new MlOAuthToken(response.accessToken(), response.refreshToken(), expiraEm));

        return tokenRepository.save(token);
    }

    public String montarUrlDeAutorizacao() {
        return "https://auth.mercadolivre.com.br/authorization"
                + "?response_type=code"
                + "&client_id=" + properties.clientId()
                + "&redirect_uri=" + properties.redirectUri();
    }
}
