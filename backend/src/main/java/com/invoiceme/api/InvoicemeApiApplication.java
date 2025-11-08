package com.invoiceme.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.invoiceme")
@EnableJpaRepositories(basePackages = "com.invoiceme")
@EntityScan(basePackages = "com.invoiceme")
public class InvoicemeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoicemeApiApplication.class, args);
    }
}

