package com.pestvisionai.backend.dto;

import java.time.Instant;
import java.util.List;

public record DetectionView(
        Long id,
        Instant detectedAt,
        String streamId,
        String serviceName,
        String pestType,
        int pestCount,
        double maxConfidence,
        String snapshotPath,
        String analysisSummary,
        List<BoundingBoxDto> boxes
) {
}
