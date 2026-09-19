package com.foodlog.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmRequest(@NotBlank String foodId) {
}
