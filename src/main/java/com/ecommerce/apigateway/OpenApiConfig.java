package com.ecommerce.apigateway;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiGatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("E-Commerce API Gateway")
                        .version("1.0.0")
                        .description("Spring Cloud Gateway. All /api/v1 routes proxy to the backend services."));
    }
}