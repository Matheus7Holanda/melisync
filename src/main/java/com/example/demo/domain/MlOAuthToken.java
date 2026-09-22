package com.example.demo.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class MlOAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accessToken;
    private String refreshToken;
    private Instant expiraEm;

    protected MlOAuthToken() {
    }

    public MlOAuthToken(String accessToken, String refreshToken, Instant expiraEm) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiraEm = expiraEm;
    }

    public boolean expirado() {
        return Instant.now().isAfter(expiraEm.minusSeconds(60));
    }

    public void atualizar(String accessToken, String refreshToken, Instant expiraEm) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiraEm = expiraEm;
    }

    public Long getId() {
        return id;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }
}
