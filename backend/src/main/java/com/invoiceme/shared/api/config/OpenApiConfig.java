package com.invoiceme.shared.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI configuration for InvoiceMe API.
 * Configures Swagger UI and OpenAPI 3.0 specification generation.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Value("${OPENAPI_SERVER_URL:}")
    private String openApiServerUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        OpenAPI openAPI = new OpenAPI()
            .info(new Info()
                .title("InvoiceMe API")
                .version("1.0.0")
                .description("OpenAPI 3 specification for the InvoiceMe ERP system"));

        // Configure servers based on environment
        List<Server> servers = getConfiguredServers();
        openAPI.servers(servers);
        return openAPI;
    }

    @Bean
    public GroupedOpenApi publicApi() {
        // Use GroupedOpenApi to ensure our server configuration is used
        // This prevents Springdoc from auto-detecting servers from the request
        return GroupedOpenApi.builder()
            .group("invoice-me-api")
            .pathsToMatch("/api/**")
            .build();
    }

    private List<Server> getConfiguredServers() {
        List<Server> servers = new ArrayList<>();
        
        // If OPENAPI_SERVER_URL is explicitly set, use it
        if (openApiServerUrl != null && !openApiServerUrl.isEmpty()) {
            servers.add(new Server()
                .url(openApiServerUrl)
                .description("API Server"));
        } else if (activeProfile != null && activeProfile.contains("prod")) {
            // Production: use HTTPS
            // Use DOMAIN_NAME env var if set, otherwise default to invoice-me.vincentchan.cloud
            String domain = System.getenv("DOMAIN_NAME");
            if (domain == null || domain.isEmpty()) {
                domain = "invoice-me.vincentchan.cloud";
            }
            servers.add(new Server()
                .url("https://" + domain)
                .description("Production server (HTTPS)"));
        } else {
            // Development: use HTTP localhost
            servers.add(new Server()
                .url("http://localhost:" + serverPort)
                .description("Local development server"));
        }
        
        return servers;
    }
}

