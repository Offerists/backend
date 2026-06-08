package ru.hack.aiprojectmanager.stt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class GroqSttClient implements SttProvider {

    private final RestClient restClient;
    private final String model;

    public GroqSttClient(
            RestClient.Builder builder,
            @Value("${groq.base-url}") String baseUrl,
            @Value("${groq.api-key}") String apiKey,
            @Value("${groq.whisper-model}") String model) {
        this.model = model;
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public String transcribe(byte[] audio, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", namedResource(audio, fileName));
        body.add("model", model);
        body.add("response_format", "json");

        TranscriptionResponse response = restClient.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(TranscriptionResponse.class);

        return response != null ? response.text() : "";
    }

    private ByteArrayResource namedResource(byte[] audio, String fileName) {
        return new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private record TranscriptionResponse(String text) {}
}
