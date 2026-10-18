package com.f1.AI.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

/** Reports whether the local Ollama server is up and whether the configured model has been pulled. */
@Service
public class OllamaStatusService {

    private final RestClient http;
    private final String baseUrl;
    private final String model;

    public OllamaStatusService(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
                               @Value("${spring.ai.ollama.chat.model:llama3.2}") String model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tags(List<Tag> models) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tag(String name) {
    }

    public record Status(boolean running, boolean modelAvailable, String model, String baseUrl,
                         List<String> installedModels, String hint) {
    }

    public Status status() {
        try {
            Tags tags = http.get().uri("/api/tags").retrieve().body(Tags.class);
            List<String> names = tags == null || tags.models() == null ? List.of()
                    : tags.models().stream().map(Tag::name).toList();
            boolean available = names.stream().anyMatch(n -> n.equals(model) || n.startsWith(model + ":"));
            return new Status(true, available, model, baseUrl, names,
                    available ? null : "Run: ollama pull " + model);
        } catch (Exception e) {
            return new Status(false, false, model, baseUrl, List.of(),
                    "Ollama is not running. Install it from ollama.com, then run: ollama pull " + model);
        }
    }
}
