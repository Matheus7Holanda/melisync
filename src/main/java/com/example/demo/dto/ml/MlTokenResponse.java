package com.example.demo.dto.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresInSeconds,
        @JsonProperty("scope") String scope,
        @JsonProperty("user_id") long userId,
        @JsonProperty("refresh_token") String refreshToken
) {
}
