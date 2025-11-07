package com.invoiceme.api;

import com.invoiceme.api.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class InvoicemeApiApplicationTests {

    @Autowired
    private HealthController healthController;

    @Test
    void contextLoads() {
        assertThat(healthController).isNotNull();
    }

    @Test
    void healthEndpointReturnsOk() {
        var response = healthController.health();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("ok");
    }
}

