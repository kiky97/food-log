package com.foodlog.client.stt;

public interface SpeechToTextClient {

    /** Transcribes the audio. Returns an empty string if no speech was found. */
    String transcribe(byte[] audio, String mimeType);
}
