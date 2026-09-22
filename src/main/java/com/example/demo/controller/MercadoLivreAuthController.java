package com.example.demo.controller;

import com.example.demo.service.MercadoLivreAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MercadoLivreAuthController {

    private final MercadoLivreAuthService authService;

    public MercadoLivreAuthController(MercadoLivreAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/auth/mercadolivre/iniciar")
    public String iniciarAutorizacao() {
        return authService.montarUrlDeAutorizacao();
    }

    @GetMapping("/auth/mercadolivre/callback")
    public String callback(@RequestParam String code) {
        authService.trocarCodePorToken(code);
        return "Autorizacao concluida com sucesso. Token salvo.";
    }
}
