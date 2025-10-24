package com.pestvisionai.backend.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DetectionEventDto(
        @Min(0) long frameId,
        @NotBlank String streamId,
        @NotNull Instant detectedAt,
        @NotBlank String pestType,
        @Min(1) int pestCount,
        @Valid List<BoundingBoxDto> boxes,
        @Min(0) @Max(1) double maxConfidence,
        String snapshotPath
) {
}
