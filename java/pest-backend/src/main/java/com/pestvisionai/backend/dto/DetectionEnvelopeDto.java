package com.pestvisionai.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DetectionEnvelopeDto(
        @NotBlank String serviceName,
        @Valid @NotNull DetectionEventDto payload
) {
}
