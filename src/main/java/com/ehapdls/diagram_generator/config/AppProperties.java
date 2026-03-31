package com.ehapdls.diagram_generator.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Gemini gemini = new Gemini();
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Gemini {
        private String apiKey;
        private String model = "gemini-2.5-flash";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private int timeoutSeconds = 30;
        private int maxOutputTokens = 4096;
        private double temperature = 0.7;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int capacity = 20;
        private int refillTokens = 20;
        private int refillDurationSeconds = 60;
    }
}
