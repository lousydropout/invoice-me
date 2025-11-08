package com.invoiceme.shared.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for InvoiceMe API.
 * Configures Swagger UI and OpenAPI 3.0 specification generation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("InvoiceMe API")
                .version("1.0.0")
                .description("OpenAPI 3 specification for the InvoiceMe ERP system"));
    }
}

