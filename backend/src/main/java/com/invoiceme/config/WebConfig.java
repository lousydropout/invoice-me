package com.invoiceme.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration for InvoiceMe API.
 * 
 * This configuration enables Cross-Origin Resource Sharing (CORS) to allow
 * the frontend application to make requests to the backend API.
 * 
 * TO SWITCH BETWEEN RESTRICTED AND OPEN MODE:
 * 
 * Restricted mode (default - recommended for production):
 *   - Comment out the .allowedOrigins("*") line
 *   - Use the specific allowedOriginPatterns() configuration
 * 
 * Open mode (for local testing only):
 *   - Uncomment the .allowedOrigins("*") line
 *   - Comment out the allowedOriginPatterns() configuration
 *   - Note: allowCredentials(true) cannot be used with allowedOrigins("*")
 *     so you'll need to set allowCredentials(false) in open mode
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            // RESTRICTED MODE: Allow specific origins
            // Supports:
            // - http://localhost with any port (e.g., http://localhost:3000, http://localhost:5173)
            // - https://*.vincentchan.cloud (any subdomain)
            .allowedOriginPatterns(
                "http://localhost:*",
                "https://*.vincentchan.cloud"
            )
            
            // OPEN MODE (for local testing only - uncomment to enable):
            // .allowedOrigins("*")
            
            // Allow all standard HTTP methods
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            
            // Allow all headers
            .allowedHeaders("*")
            
            // Expose the Authorization header to the frontend
            .exposedHeaders("Authorization")
            
            // Support credentials (cookies, authorization headers, etc.)
            // Note: When using allowedOrigins("*"), you must set this to false
            .allowCredentials(true)
            
            // Cache preflight requests for 1 hour
            .maxAge(3600);
    }
}

