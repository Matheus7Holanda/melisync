package com.example.demo.service;

import com.example.demo.dto.ml.MlShipmentHistoryDTO;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Define o que significa "o envio saiu em um dia".
 *
 * REGRA: a data de saida e a do PRIMEIRO registro do historico do envio com
 * status "shipped" (o "A caminho" do painel do Mercado Livre), convertida
 * para o horario do Brasil. Se o criterio do seu negocio for outro (ex:
 * data da coleta/postagem), e so alterar STATUS_SAIDA / dataDeSaida() aqui.
 */
public final class RegraSaidaEnvio {

    // Brasil nao tem horario de verao desde 2019, entao -03:00 fixo e seguro.
    public static final ZoneOffset FUSO_BRASIL = ZoneOffset.ofHours(-3);

    static final String STATUS_SAIDA = "shipped";

    // Status em que o envio pode ja ter saido. Os demais (pending, handling,
    // ready_to_ship, cancelled...) nao tem registro "shipped" -- evita
    // consultar o historico a toa.
    private static final Set<String> STATUS_QUE_PODEM_TER_SAIDO =
            Set.of("shipped", "delivered", "not_delivered");

    private RegraSaidaEnvio() {
    }

    public static boolean podeTerSaido(String statusDoEnvio) {
        return statusDoEnvio != null && STATUS_QUE_PODEM_TER_SAIDO.contains(statusDoEnvio);
    }

    /** Momento do primeiro status "shipped" do historico, se existir. */
    public static Optional<OffsetDateTime> dataDeSaida(List<MlShipmentHistoryDTO> historico) {
        if (historico == null) {
            return Optional.empty();
        }
        OffsetDateTime primeira = null;
        for (MlShipmentHistoryDTO registro : historico) {
            if (registro == null || !STATUS_SAIDA.equals(registro.status())) {
                continue;
            }
            OffsetDateTime data;
            try {
                data = OffsetDateTime.parse(registro.date());
            } catch (RuntimeException e) {
                // Falhar alto: ignorar em silencio faria o envio sumir do dia.
                throw new IllegalStateException(
                        "Data invalida no historico do envio: '" + registro.date() + "'", e);
            }
            if (primeira == null || data.isBefore(primeira)) {
                primeira = data;
            }
        }
        return Optional.ofNullable(primeira);
    }

    /** O instante cai em "dia" no horario do Brasil? (e nao em UTC). */
    public static boolean saiuNoDia(OffsetDateTime saida, LocalDate dia) {
        return saida.withOffsetSameInstant(FUSO_BRASIL).toLocalDate().equals(dia);
    }
}
