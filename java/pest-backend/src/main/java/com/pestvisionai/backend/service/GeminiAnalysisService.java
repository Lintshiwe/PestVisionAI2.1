package com.pestvisionai.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pestvisionai.backend.config.PestVisionProperties;
import com.pestvisionai.backend.model.Detection;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class GeminiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAnalysisService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String model;
    private final String apiKey;

    public GeminiAnalysisService(WebClient.Builder builder, PestVisionProperties properties, ObjectMapper objectMapper) {
        var gemini = properties.getAi().getGemini();
        this.apiKey = StringUtils.hasText(gemini.getApiKey()) ? gemini.getApiKey().trim() : null;
        this.enabled = StringUtils.hasText(this.apiKey);
        this.model = gemini.getModel();
        this.objectMapper = objectMapper;
        if (enabled) {
            this.webClient = builder
                    .baseUrl("https://generativelanguage.googleapis.com")
                    .build();
        } else {
            this.webClient = null;
        }
    }

    public Optional<String> generateSummary(Detection detection) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            String prompt = buildPrompt(detection);
            JsonNode request = objectMapper.createObjectNode()
                    .set("contents", objectMapper.createArrayNode()
                            .add(objectMapper.createObjectNode()
                                    .set("parts", objectMapper.createArrayNode()
                                            .add(objectMapper.createObjectNode()
                                                    .put("text", prompt))
                                    )
                            ));

            JsonNode response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/" + model + ":generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(request))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                log.warn("Empty response from Gemini API");
                return Optional.empty();
            }

            JsonNode candidates = response.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                log.warn("Gemini response missing candidates node: {}", response);
                return Optional.empty();
            }

            JsonNode content = candidates.get(0).path("content");
            JsonNode parts = content.path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                JsonNode textNode = parts.get(0).get("text");
                if (textNode != null && textNode.isTextual()) {
                    return Optional.of(textNode.asText());
                }
            }

            log.warn("Unable to parse Gemini summary from response: {}", response);
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Gemini analysis failed: {}", ex.getMessage());
            log.debug("Gemini analysis error", ex);
            return Optional.empty();
        }
    }

    private String buildPrompt(Detection detection) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are an agronomy expert. Analyse the following pest detection event and provide a two sentence actionable summary for field technicians.\\n");
        if (detection.getDetectedAt() != null) {
            builder.append("Detection timestamp: ")
                    .append(TIMESTAMP_FORMATTER.format(detection.getDetectedAt()))
                    .append("\n");
        }
        builder.append("Pest type: ").append(detection.getPestType()).append("\\n");
        builder.append("Detected count: ").append(detection.getPestCount()).append(" with max confidence ")
                .append(String.format("%.2f", detection.getMaxConfidence())).append(".\\n");
        if (detection.getBoxes() != null && !detection.getBoxes().isEmpty()) {
            builder.append("Bounding boxes: ");
            detection.getBoxes().forEach(box -> builder
                    .append("[label=")
                    .append(box.getLabel())
                    .append(", confidence=")
                    .append(String.format("%.2f", box.getConfidence()))
                    .append("] "));
            builder.append("\\n");
        }
        builder.append("If the event suggests human presence, highlight that pesticide actions should be paused. Focus on concise operational guidance.");
        return builder.toString();
    }
}
