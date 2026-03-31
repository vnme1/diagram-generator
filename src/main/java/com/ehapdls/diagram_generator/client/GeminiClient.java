package com.ehapdls.diagram_generator.client;

import com.ehapdls.diagram_generator.config.AppProperties;
import com.ehapdls.diagram_generator.exception.GeminiApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final RestClient geminiRestClient;
    private final AppProperties appProperties;

    /**
     * Sends a prompt to the Gemini API with a system instruction and returns the text response.
     */
    public String generateContent(String systemInstruction, String userPrompt) {
        AppProperties.Gemini config = appProperties.getGemini();

        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new GeminiApiException("Gemini API key is not configured");
        }

        Map<String, Object> requestBody = buildRequestBody(systemInstruction, userPrompt, config);

        try {
            GeminiResponse response = geminiRestClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/models/" + config.getModel() + ":generateContent")
                            .queryParam("key", config.getApiKey())
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiResponse.class);

            return extractText(response);

        } catch (RestClientResponseException ex) {
            log.error("Gemini API returned error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new GeminiApiException(
                    "Gemini API error (HTTP %d): %s".formatted(ex.getStatusCode().value(), parseApiErrorMessage(ex))
            );
        } catch (ResourceAccessException ex) {
            log.error("Gemini API timeout or connection error", ex);
            throw new GeminiApiException("Gemini API is not reachable. Please try again later.");
        }
    }

    private Map<String, Object> buildRequestBody(String systemInstruction, String userPrompt,
                                                   AppProperties.Gemini config) {
        return Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ),
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", userPrompt)))
                ),
                "generationConfig", Map.of(
                        "temperature", config.getTemperature(),
                        "maxOutputTokens", config.getMaxOutputTokens()
                )
        );
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new GeminiApiException("Empty response from Gemini API");
        }

        GeminiResponse.Candidate candidate = response.candidates().get(0);

        if (candidate.content() == null || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            throw new GeminiApiException("No content in Gemini API response");
        }

        // Gemini 2.5 thinking models return multiple parts: thought parts + text parts.
        // Skip thought parts and extract only the actual text output.
        return candidate.content().parts().stream()
                .filter(part -> part.text() != null && (part.thought() == null || !part.thought()))
                .map(GeminiResponse.Part::text)
                .findFirst()
                .orElseGet(() ->
                        // Fallback: if no non-thought part found, use the last part
                        candidate.content().parts().get(candidate.content().parts().size() - 1).text()
                );
    }

    private String parseApiErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body.contains("API_KEY_INVALID") || body.contains("API key not valid")) {
            return "Invalid API key. Please check your GEMINI_API_KEY.";
        }
        if (body.contains("QUOTA_EXCEEDED") || body.contains("quota")) {
            return "API quota exceeded. Please try again later.";
        }
        if (body.contains("is not found") || body.contains("NOT_FOUND")) {
            return "Model not found. Please check GEMINI_MODEL in your .env file.";
        }
        return "Please try again later.";
    }

    // --- Response DTOs (Gemini REST API structure) ---

    public record GeminiResponse(List<Candidate> candidates) {
        public record Candidate(Content content, String finishReason) {}
        public record Content(List<Part> parts, String role) {}
        public record Part(String text, Boolean thought) {}
    }
}
