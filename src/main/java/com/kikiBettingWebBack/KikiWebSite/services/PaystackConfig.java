package com.kikiBettingWebBack.KikiWebSite.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class PaystackConfig {

    @Value("${app.paystack.secret-key}")
    private String secretKey;

    @Value("${app.paystack.base-url}")
    private String baseUrl;

    @Bean
    public WebClient paystackWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}