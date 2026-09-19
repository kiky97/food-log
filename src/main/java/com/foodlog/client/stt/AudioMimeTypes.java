package com.foodlog.client.stt;

import com.foodlog.exception.ApiException;
import java.util.Locale;
import java.util.Map;

/** Resolves the MIME type of an uploaded audio file from its declared type or file extension. */
public final class AudioMimeTypes {

    public static final String TEXT_PLAIN = "text/plain";

    private static final Map<String, String> BY_EXTENSION = Map.of(
            "wav", "audio/wav",
            "mp3", "audio/mp3",
            "ogg", "audio/ogg",
            "flac", "audio/flac",
            "aac", "audio/aac",
            "m4a", "audio/mp4",
            "webm", "audio/webm",
            "txt", TEXT_PLAIN);

    private AudioMimeTypes() {
    }

    /**
     * text/plain is accepted so the mock STT can be exercised without real audio; the Gemini
     * client rejects it.
     */
    public static String resolve(String filename, String declaredContentType) {
        if (declaredContentType != null) {
            String ct = declaredContentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
            if (ct.startsWith("audio/") || ct.equals(TEXT_PLAIN)) {
                return ct;
            }
        }
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot >= 0) {
                String mime = BY_EXTENSION.get(filename.substring(dot + 1).toLowerCase(Locale.ROOT));
                if (mime != null) {
                    return mime;
                }
            }
        }
        throw ApiException.badRequest("Unsupported audio type. Upload wav, mp3, ogg, flac, aac, m4a or webm.");
    }
}
