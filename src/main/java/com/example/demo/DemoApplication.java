package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada unico da aplicacao.
 *
 * CORRIGIDO: antes existiam duas classes @SpringBootApplication em pacotes
 * sem relacao entre si (com.example.demo.DemoApplication e
 * integracao.MlVhsysIntegrationApplication), e todos os outros pacotes
 * (config, client, controller, domain, service...) estavam soltos na raiz,
 * fora do escopo de component scan de qualquer uma delas. Agora existe
 * apenas esta classe, e todo o resto do projeto vive dentro de
 * com.example.demo.*, entao o component scan padrao do Spring Boot
 * (a partir do pacote desta classe, para baixo) alcanca tudo.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}
