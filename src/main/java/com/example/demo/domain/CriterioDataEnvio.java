package com.example.demo.domain;

/**
 * O que significa "os envios do dia".
 *
 * CRIACAO_PEDIDO: pedidos pagos CRIADOS no dia (agrupados por envio).
 *                 Mais simples e barato; bom para validar a integracao.
 * SAIDA_ENVIO:    envios que SAIRAM no dia (primeiro status "shipped" no
 *                 historico do envio).
 * ENVIOS_DE_HOJE: a lista "Envios de hoje" do painel do Mercado Livre: envios
 *                 prontos para despachar agora (ready_to_ship), fora os agendados
 *                 para os proximos dias. PADRAO.
 */
public enum CriterioDataEnvio {
    CRIACAO_PEDIDO,
    SAIDA_ENVIO,
    ENVIOS_DE_HOJE;

    /** Converte o texto da configuracao; vazio/nulo usa ENVIOS_DE_HOJE. */
    public static CriterioDataEnvio deTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return ENVIOS_DE_HOJE;
        }
        try {
            return valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Falhar alto: cair no padrao em silencio mostraria dados de
            // um criterio diferente do que a pessoa achou que escolheu.
            throw new IllegalArgumentException(
                    "Criterio de data invalido: '" + texto + "'. Use ENVIOS_DE_HOJE, CRIACAO_PEDIDO ou SAIDA_ENVIO.", e);
        }
    }
}
