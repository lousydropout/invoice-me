package com.invoiceme.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for BasicAuth login verification.
 * 
 * This endpoint verifies that the provided BasicAuth credentials are valid.
 * If authentication succeeds, returns the authenticated username.
 * If authentication fails, Spring Security automatically returns HTTP 401.
 */
@RestController
public class LoginController {

    /**
     * Verifies BasicAuth credentials and returns the authenticated username.
     * 
     * This endpoint requires authentication. Spring Security will automatically
     * return HTTP 401 for invalid credentials.
     * 
     * @param authentication The authenticated user's authentication object
     * @return HTTP 200 with JSON body containing the username
     */
    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(Map.of("user", username));
    }
}

