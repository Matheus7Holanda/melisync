package com.example.demo.repository;

import com.example.demo.domain.MlOAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MlOAuthTokenRepository extends JpaRepository<MlOAuthToken, Long> {
}
