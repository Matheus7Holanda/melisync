package com.example.demo.client;

import com.example.demo.config.MercadoLivreProperties;
import com.example.demo.dto.ml.MlOrderSearchResponse;
import com.example.demo.dto.ml.MlShipmentCostsDTO;
import com.example.demo.dto.ml.MlShipmentHistoryDTO;
import com.example.demo.dto.ml.MlShipmentDTO;
import com.example.demo.service.MercadoLivreAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cliente HTTP para os endpoints de pedidos e envios do Mercado Livre.
 * Toda chamada pega um access_token valido (renovado automaticamente pelo
 * MercadoLivreAuthService quando necessario) e envia como Bearer.
 */
@Component
public class MercadoLivreOrderClient {

    // Formato aceito pelo ML: 2026-10-10T00:00:00.000-03:00
    private static final DateTimeFormatter FORMATO_DATA_ML =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final WebClient mercadoLivreWebClient;
    private final MercadoLivreAuthService authService;
    private final MercadoLivreProperties properties;

    public MercadoLivreOrderClient(
            WebClient mercadoLivreWebClient,
            MercadoLivreAuthService authService,
            MercadoLivreProperties properties
    ) {
        this.mercadoLivreWebClient = mercadoLivreWebClient;
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * GET /orders/search -- uma pagina de pedidos do vendedor criados no
     * intervalo informado.
     */
    public MlOrderSearchResponse buscarPedidos(OffsetDateTime de, OffsetDateTime ate, int offset, int limit) {
        String token = authService.obterAccessTokenValido();

        return mercadoLivreWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/orders/search")
                        .queryParam("seller", properties.sellerId())
                        .queryParam("order.date_created.from", FORMATO_DATA_ML.format(de))
                        .queryParam("order.date_created.to", FORMATO_DATA_ML.format(ate))
                        .queryParam("sort", "date_asc")
                        .queryParam("offset", offset)
                        .queryParam("limit", limit)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(MlOrderSearchResponse.class)
                .block();
    }

    /**
     * GET /shipments/{id} -- dados do envio, usados para descobrir se a
     * logistica e FLEX.
     */
    public MlShipmentDTO buscarEnvio(Long shippingId) {
        String token = authService.obterAccessTokenValido();

        return mercadoLivreWebClient.get()
                .uri("/shipments/{id}", shippingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("x-format-new", "true")
                .retrieve()
                .bodyToMono(MlShipmentDTO.class)
                .block();
    }

    /**
     * GET /shipments/{id}/history -- historico de status do envio, de onde
     * tiramos a data em que ele saiu (ver RegraSaidaEnvio).
     */
    public List<MlShipmentHistoryDTO> buscarHistoricoEnvio(Long shippingId) {
        String token = authService.obterAccessTokenValido();

        MlShipmentHistoryDTO[] historico = mercadoLivreWebClient.get()
                .uri("/shipments/{id}/history", shippingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(MlShipmentHistoryDTO[].class)
                .block();

        return historico == null ? List.of() : List.of(historico);
    }

    /**
     * GET /shipments/{id}/costs -- custos do envio por vendedor; usado para
     * saber quanto de frete o vendedor paga.
     */
    public MlShipmentCostsDTO buscarCustosEnvio(Long shippingId) {
        String token = authService.obterAccessTokenValido();

        return mercadoLivreWebClient.get()
                .uri("/shipments/{id}/costs", shippingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("x-format-new", "true")
                .retrieve()
                .bodyToMono(MlShipmentCostsDTO.class)
                .block();
    }
}
