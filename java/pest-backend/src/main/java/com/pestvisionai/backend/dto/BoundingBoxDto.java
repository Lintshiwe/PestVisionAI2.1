package com.pestvisionai.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BoundingBoxDto(
        @Min(0) int x,
        @Min(0) int y,
        @Min(1) int width,
        @Min(1) int height,
        @Min(0) @Max(1) double confidence,
        @NotBlank String label,
        Integer trackId
) {
}
