package com.invoiceme.shared.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * BasicAuth security configuration.
 * 
 * Authentication is NOT a domain concern - it's a cross-cutting infrastructure concern.
 * This configuration protects all /api/** endpoints with BasicAuth.
 * 
 * Credentials are sourced from environment variables:
 * - SPRING_SECURITY_USER_NAME (default: admin)
 * - SPRING_SECURITY_USER_PASSWORD (default: admin)
 */
@Configuration
@EnableWebSecurity
public class BasicAuthConfig {

    @Value("${spring.security.user.name:admin}")
    private String username;

    @Value("${spring.security.user.password:admin}")
    private String password;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for stateless API
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Health endpoints remain public for monitoring
                .requestMatchers("/api/health", "/actuator/health", "/actuator/info").permitAll()
                // All other /api/** endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                // Allow all other requests (e.g., Swagger UI if added later)
                .anyRequest().permitAll())
            .httpBasic(httpBasic -> {});

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
            .username(username)
            .password(passwordEncoder().encode(password))
            .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

