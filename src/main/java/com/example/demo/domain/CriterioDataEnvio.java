package com.example.demo.domain;

/**
 * O que significa "os envios do dia".
 *
 * CRIACAO_PEDIDO: pedidos pagos CRIADOS no dia (agrupados por envio).
 *                 Mais simples e barato; bom para validar a integracao.
 * SAIDA_ENVIO:    envios que SAIRAM no dia (primeiro status "shipped" no
 *                 historico do envio). Alvo final do projeto.
 */
public enum CriterioDataEnvio {
    CRIACAO_PEDIDO,
    SAIDA_ENVIO;

    /** Converte o texto da configuracao; vazio/nulo usa CRIACAO_PEDIDO. */
    public static CriterioDataEnvio deTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return CRIACAO_PEDIDO;
        }
        try {
            return valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // Falhar alto: cair no padrao em silencio mostraria dados de
            // um criterio diferente do que a pessoa achou que escolheu.
            throw new IllegalArgumentException(
                    "Criterio de data invalido: '" + texto + "'. Use CRIACAO_PEDIDO ou SAIDA_ENVIO.", e);
        }
    }
}
