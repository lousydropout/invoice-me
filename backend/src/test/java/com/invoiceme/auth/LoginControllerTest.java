package com.invoiceme.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for LoginController.
 * 
 * Verifies that:
 * 1. Login endpoint returns authenticated username with valid credentials
 * 2. Login endpoint returns 401 with invalid credentials
 * 3. Login endpoint returns 401 without credentials
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DisplayName("LoginController Integration Tests")
class LoginControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("invoiceme_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.security.user.name", () -> "testuser");
        registry.add("spring.security.user.password", () -> "testpass");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /login with valid credentials returns 200 and username")
    void login_WithValidCredentials_ReturnsUsername() throws Exception {
        mockMvc.perform(get("/login")
                .with(httpBasic("testuser", "testpass"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.user", is("testuser")));
    }

    @Test
    @DisplayName("GET /login with invalid credentials returns 401")
    void login_WithInvalidCredentials_Returns401() throws Exception {
        mockMvc.perform(get("/login")
                .with(httpBasic("testuser", "wrongpassword"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /login without credentials returns 401")
    void login_WithoutCredentials_Returns401() throws Exception {
        mockMvc.perform(get("/login")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }
}

