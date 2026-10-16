package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta de GET /shipments/{id}. "self_service" = Mercado Envios Flex.
 *
 * A API de envios do ML tem dois formatos de resposta: o legado traz
 * logistic_type no topo; o novo (header x-format-new: true) traz
 * logistic.type. Aceitamos os dois para nao depender de qual vier.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlShipmentDTO(
        Long id,
        String status,
        String substatus,
        @JsonProperty("logistic_type") String logisticType,
        Logistic logistic
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Logistic(String type) {
    }

    public String tipoLogistica() {
        if (logisticType != null && !logisticType.isBlank()) {
            return logisticType;
        }
        return logistic != null ? logistic.type() : null;
    }

    public boolean isFlex() {
        return "self_service".equals(tipoLogistica());
    }
}
