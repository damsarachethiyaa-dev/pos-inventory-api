package com.chethiya.posapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI posInventoryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("POS & Inventory Enterprise REST API")
                        .version("1.0.0")
                        .description("Transactional Point-of-Sale and Inventory Management backend with JWT RBAC, "
                                + "pessimistic-locked stock deduction, Flyway migrations, and RFC 7807 error handling.")
                        .contact(new Contact()
                                .name("Chethiya Rathnasekara")
                                .url("https://github.com/damsarachethiyaa-dev")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
