package com.example.coretracking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title and description shown at the top of Swagger UI. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI coreTrackingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Core Tracking API")
                .version("1.0.0")
                .description("Track returned engine cores from receiving to remanufacturing or scrapping."));
    }
}
