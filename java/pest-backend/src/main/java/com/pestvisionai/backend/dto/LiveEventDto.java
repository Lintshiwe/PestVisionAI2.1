package com.pestvisionai.backend.dto;

public record LiveEventDto(
        DetectionView detection,
        SprayEventView sprayEvent
) {
}
