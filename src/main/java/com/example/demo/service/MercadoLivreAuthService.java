package com.example.demo.service;

import com.example.demo.config.MercadoLivreProperties;
import com.example.demo.domain.MlOAuthToken;
import com.example.demo.dto.ml.MlTokenResponse;
import com.example.demo.repository.MlOAuthTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Optional;

@Service
public class MercadoLivreAuthService {

    private static final Logger log = LoggerFactory.getLogger(MercadoLivreAuthService.class);

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

        MlTokenResponse response = solicitarToken(form, "trocar o code por token");
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
        String refreshToken = tokenAtual.getRefreshToken();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException(
                    "O token salvo nao tem refresh_token, entao nao da para renova-lo. "
                    + "Isso acontece quando o app e autorizado sem o escopo 'offline_access'. "
                    + "Marque 'offline_access' nas permissoes do app no Mercado Livre e refaca a "
                    + "autorizacao em /auth/mercadolivre/iniciar.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("refresh_token", refreshToken);

        MlTokenResponse response = solicitarToken(form, "renovar o token");
        return salvarOuAtualizarToken(response);
    }

    /**
     * Chama POST /oauth/token. Se o Mercado Livre recusar, o erro traz o
     * motivo que ele devolveu (o corpo da resposta), que a excecao padrao do
     * WebClient esconde -- sem isso um "400 Bad Request" nao diz o que
     * corrigir. O corpo de erro do ML nao contem tokens nem o client_secret.
     */
    private MlTokenResponse solicitarToken(MultiValueMap<String, String> form, String operacao) {
        try {
            MlTokenResponse response = mercadoLivreWebClient.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(MlTokenResponse.class)
                    .block();

            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException(
                        "O Mercado Livre respondeu sem access_token ao " + operacao + ".");
            }
            return response;
        } catch (WebClientResponseException e) {
            throw new IllegalStateException(
                    "O Mercado Livre recusou ao " + operacao
                    + " (HTTP " + e.getStatusCode().value() + "): " + e.getResponseBodyAsString()
                    + " | Se o erro for 'invalid_grant': o refresh_token/code expirou ou ja foi usado -- "
                    + "refaca a autorizacao em /auth/mercadolivre/iniciar (com o ngrok ligado). "
                    + "Se for 'invalid_client': confira ML_CLIENT_ID e ML_CLIENT_SECRET na Run Configuration.",
                    e);
        }
    }

    private MlOAuthToken salvarOuAtualizarToken(MlTokenResponse response) {
        Instant expiraEm = Instant.now().plusSeconds(response.expiresInSeconds());

        if (response.refreshToken() == null || response.refreshToken().isBlank()) {
            log.warn("O Mercado Livre nao devolveu refresh_token. Verifique se o escopo "
                    + "'offline_access' esta marcado nas permissoes do app; sem ele o token "
                    + "expira em poucas horas e nao pode ser renovado.");
        }

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