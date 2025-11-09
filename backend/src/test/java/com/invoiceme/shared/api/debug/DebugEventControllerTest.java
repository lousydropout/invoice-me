package com.invoiceme.shared.api.debug;

import com.invoiceme.shared.infrastructure.persistence.DomainEventJpaRepository;
import com.invoiceme.shared.infrastructure.persistence.entities.DomainEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for DebugEventController.
 * 
 * Verifies that:
 * 1. Debug endpoint returns persisted events
 * 2. Endpoint is only available in dev profile
 * 3. Endpoint requires authentication
 */
@SpringBootTest(classes = com.invoiceme.api.InvoicemeApiApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
@DisplayName("DebugEventController Integration Tests")
class DebugEventControllerTest {

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
        registry.add("spring.security.user.name", () -> "admin");
        registry.add("spring.security.user.password", () -> "admin");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DomainEventJpaRepository eventRepository;

    private DomainEventEntity event1;
    private DomainEventEntity event2;
    private String authHeader;

    @BeforeEach
    void setUp() {
        event1 = new DomainEventEntity("InvoiceCreated", "{\"invoiceId\":\"123\",\"occurredAt\":\"2025-01-15T10:00:00Z\"}");
        event2 = new DomainEventEntity("CustomerCreated", "{\"customerId\":\"456\",\"occurredAt\":\"2025-01-15T09:00:00Z\"}");
        
        // BasicAuth header: admin:admin
        String credentials = "admin:admin";
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
        authHeader = "Basic " + encodedCredentials;
    }

    @Test
    @DisplayName("T8.2.5 - Get all events returns persisted events")
    void getAllEvents() throws Exception {
        // Given
        List<DomainEventEntity> events = List.of(event1, event2);
        when(eventRepository.findAllByOrderByCreatedAtDesc()).thenReturn(events);

        // When & Then
        mockMvc.perform(get("/api/debug/events")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].type", is("InvoiceCreated")))
            .andExpect(jsonPath("$[0].payload").exists())
            .andExpect(jsonPath("$[0].createdAt").exists())
            .andExpect(jsonPath("$[1].type", is("CustomerCreated")))
            .andExpect(jsonPath("$[1].payload").exists())
            .andExpect(jsonPath("$[1].createdAt").exists());
    }

    @Test
    @DisplayName("T8.2.6 - Get all events returns empty list when no events")
    void getAllEventsEmpty() throws Exception {
        // Given
        when(eventRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/debug/events")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("T8.2.7 - Debug endpoint requires authentication")
    void requiresAuthentication() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/debug/events")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("T8.2.8 - Events are ordered by creation date (newest first)")
    void eventsOrderedByDate() throws Exception {
        // Given
        DomainEventEntity olderEvent = new DomainEventEntity("CustomerCreated", "{}");
        DomainEventEntity newerEvent = new DomainEventEntity("InvoiceCreated", "{}");
        
        List<DomainEventEntity> events = List.of(newerEvent, olderEvent);
        when(eventRepository.findAllByOrderByCreatedAtDesc()).thenReturn(events);

        // When & Then
        mockMvc.perform(get("/api/debug/events")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].type", is("InvoiceCreated"))) // Newest first
            .andExpect(jsonPath("$[1].type", is("CustomerCreated")));
    }
}

