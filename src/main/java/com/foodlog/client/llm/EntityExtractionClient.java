package com.foodlog.client.llm;

import java.util.List;

public interface EntityExtractionClient {

    /** Extracts every food or drink the user says they consumed. Returns an empty list if there is none. */
    List<ExtractedFood> extract(String text);
}
