package com.pestvisionai.backend.service;

import com.pestvisionai.backend.model.Detection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SprayControllerClient {

    private static final Logger log = LoggerFactory.getLogger(SprayControllerClient.class);

    public void triggerSpray(Detection detection) {
        log.info("[Spray] Triggered for detection {} with confidence {}", detection.getId(), detection.getMaxConfidence());
    }
}
