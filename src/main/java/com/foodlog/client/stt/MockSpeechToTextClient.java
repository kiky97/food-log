package com.foodlog.client.stt;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development stand-in: treats the uploaded bytes as UTF-8 text, so a .txt file with a sentence
 * can simulate a voice recording. Real audio needs {@code STT_PROVIDER=gemini}.
 */
@Component
@ConditionalOnProperty(name = "app.stt.provider", havingValue = "mock", matchIfMissing = true)
public class MockSpeechToTextClient implements SpeechToTextClient {

    @Override
    public String transcribe(byte[] audio, String mimeType) {
        if (!AudioMimeTypes.TEXT_PLAIN.equals(mimeType)) {
            throw com.foodlog.exception.ApiException.unavailable(
                    "Mock STT cannot decode audio. Set STT_PROVIDER=gemini, or upload a .txt file to simulate speech.");
        }
        return new String(audio, StandardCharsets.UTF_8).trim();
    }
}
