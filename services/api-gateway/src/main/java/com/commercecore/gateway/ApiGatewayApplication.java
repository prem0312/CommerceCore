package com.commercecore.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * CommerceCore API Gateway
 * Acts as the single entry point for all client requests, providing intelligent routing,
 * centralized JWT authentication, header propagation (X-User-Id, X-User-Role), and rate limiting.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
