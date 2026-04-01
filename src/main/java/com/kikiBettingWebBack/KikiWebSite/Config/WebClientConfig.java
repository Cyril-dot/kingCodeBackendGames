package com.kikiBettingWebBack.KikiWebSite.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${football.api.key}")
    private String apiKey;

    @Value("${football.api.base-url}")
    private String baseUrl;

    @Bean("footballWebClient")
    public WebClient footballWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", apiKey)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Bean("genericWebClient")
    public WebClient genericWebClient() {
        return WebClient.builder()
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
}