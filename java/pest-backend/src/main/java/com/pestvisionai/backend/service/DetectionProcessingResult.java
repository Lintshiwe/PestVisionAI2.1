package com.pestvisionai.backend.service;

import com.pestvisionai.backend.model.Detection;
import com.pestvisionai.backend.model.SprayEvent;
import java.util.Optional;

public record DetectionProcessingResult(Detection detection, SprayEvent sprayEvent) {

    public Optional<SprayEvent> sprayEventOptional() {
        return Optional.ofNullable(sprayEvent);
    }
}
