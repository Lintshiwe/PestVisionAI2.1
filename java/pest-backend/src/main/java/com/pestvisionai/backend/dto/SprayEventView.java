package com.pestvisionai.backend.dto;

import java.time.Instant;

public record SprayEventView(
        Long id,
        Instant triggeredAt,
        String reason,
        double confidence,
        Long detectionId
) {
}
