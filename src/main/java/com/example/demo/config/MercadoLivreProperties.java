package com.example.demo.config;

import com.example.demo.domain.CriterioDataEnvio;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mercadolivre")
public record MercadoLivreProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String sellerId,
        String apiBaseUrl,
        Integer diasRetroativos,
        String criterioData
) {

    private static final int DIAS_RETROATIVOS_PADRAO = 10;

    /**
     * Quantos dias ANTES do dia consultado buscamos pedidos criados: um envio
     * pode sair dias depois do pedido, e a API nao permite buscar envios por
     * data de saida -- entao olhamos pedidos recentes e filtramos pelo
     * historico do envio.
     */
    public int diasRetroativosOuPadrao() {
        return diasRetroativos != null && diasRetroativos >= 0 ? diasRetroativos : DIAS_RETROATIVOS_PADRAO;
    }

    /** Criterio de "envios do dia" configurado (padrao: CRIACAO_PEDIDO). */
    public CriterioDataEnvio criterioDataOuPadrao() {
        return CriterioDataEnvio.deTexto(criterioData);
    }
}
