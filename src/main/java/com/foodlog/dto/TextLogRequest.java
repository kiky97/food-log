package com.foodlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record TextLogRequest(
        @NotBlank @Size(max = 100) String userId,
        @NotBlank @Size(max = 2000) String text,
        Instant loggedAt) {
}
