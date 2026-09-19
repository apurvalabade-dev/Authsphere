package com.authsphere.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("AuthSphere API")
                .description("Production-oriented Authentication & Authorization Service — JWT with refresh token rotation, Redis token blacklisting, RBAC, rate limiting, account locking, email verification, session management, and audit logging.")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Apurva Labade")
                    .email("apurvalabade1109@gmail.com")
                    .url("https://github.com/apurvalabade-dev/Authsphere")))
            .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
            .components(new Components()
                .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste your access token here. Get it from POST /api/v1/auth/login")));
    }
}
