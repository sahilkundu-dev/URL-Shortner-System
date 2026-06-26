package com.sahil.url_shortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("URL Shortener API")
                        .description("""
                                A production-grade URL Shortening Service built with Java 21 and Spring Boot 3.5.
                                
                                **Features:**
                                - URL shortening with Base62 encoding (6-char padded codes)
                                - Redis Cache-Aside pattern with TTL alignment
                                - Per-link expiry with HTTP 410 Gone
                                - Real-time click analytics (IP, user-agent, timestamp)
                                - RFC 3986 URL validation with XSS scheme blocklist
                                - Token bucket rate limiting (10 req/min per IP)
                                - Spring Actuator health monitoring
                                
                                **Health & Monitoring:**
                                - `GET /actuator/health` — System health (MySQL, Redis, app)
                                - `GET /actuator/info` — App metadata
                                - `GET /actuator/metrics` — JVM and HTTP metrics
                                
                                **Rate Limiting:** POST /api/shorten is limited to 10 requests per minute per IP.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Sahil Kundu")
                                .url("https://github.com/sahilkundu-dev"))
                        .license(new License()
                                .name("MIT License")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server")
                ));
    }
}