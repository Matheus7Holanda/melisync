package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Um registro de GET /shipments/{id}/history. "date" vem em UTC
 * (ex: 2016-12-30T12:32:35.000Z).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlShipmentHistoryDTO(
        String status,
        String substatus,
        String date
) {
}
