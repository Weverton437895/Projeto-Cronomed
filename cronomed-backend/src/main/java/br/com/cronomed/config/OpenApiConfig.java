package br.com.cronomed.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentacao OpenAPI (Swagger UI em /swagger-ui.html, desabilitada no perfil prod). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cronomedOpenApi() {
        return new OpenAPI()
                .info(new Info().title("CronoMed API").version("0.1.0")
                        .description("API REST do Sistema de Gerenciamento de Medicacao CronoMed"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
