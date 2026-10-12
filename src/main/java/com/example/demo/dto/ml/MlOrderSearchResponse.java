package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Envelope de resposta de GET /orders/search, com paginacao.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlOrderSearchResponse(
        List<MlOrderDTO> results,
        MlPagingDTO paging
) {
}
