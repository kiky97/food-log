package com.foodlog.client.stt;

import com.foodlog.client.gemini.GeminiApi;
import com.foodlog.exception.ApiException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Transcribes audio by sending it inline to Gemini (inline requests are limited to about 20 MB). */
@Component
@ConditionalOnProperty(name = "app.stt.provider", havingValue = "gemini")
public class GeminiSpeechToTextClient implements SpeechToTextClient {

    private static final String PROMPT = "Transcribe this audio verbatim in its original language. "
            + "Output only the transcript. If there is no speech, output nothing.";

    private final GeminiApi gemini;

    public GeminiSpeechToTextClient(GeminiApi gemini) {
        this.gemini = gemini;
    }

    @Override
    public String transcribe(byte[] audio, String mimeType) {
        if (!mimeType.startsWith("audio/")) {
            throw ApiException.badRequest("Gemini STT needs an audio file, got " + mimeType);
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(
                        Map.of("text", PROMPT),
                        Map.of("inline_data", Map.of(
                                "mime_type", mimeType,
                                "data", Base64.getEncoder().encodeToString(audio)))))),
                "generationConfig", Map.of("temperature", 0));
        return gemini.generateText(body).trim();
    }
}
