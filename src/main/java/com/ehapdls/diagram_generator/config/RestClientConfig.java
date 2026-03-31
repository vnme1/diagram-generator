package com.ehapdls.diagram_generator.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final AppProperties appProperties;

    @Bean
    public RestClient geminiRestClient() {
        int timeoutMs = appProperties.getGemini().getTimeoutSeconds() * 1000;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);

        return RestClient.builder()
                .baseUrl(appProperties.getGemini().getBaseUrl())
                .requestFactory(factory)
                .build();
    }
}
