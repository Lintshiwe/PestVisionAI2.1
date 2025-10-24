package com.pestvisionai.backend.controller;

import com.pestvisionai.backend.dto.DetectionEnvelopeDto;
import com.pestvisionai.backend.dto.DetectionView;
import com.pestvisionai.backend.dto.LiveEventDto;
import com.pestvisionai.backend.dto.SprayEventView;
import com.pestvisionai.backend.service.DetectionEventPublisher;
import com.pestvisionai.backend.service.DetectionProcessingResult;
import com.pestvisionai.backend.service.DetectionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/detections")
public class DetectionController {

    private final DetectionService detectionService;
    private final DetectionEventPublisher eventPublisher;

    public DetectionController(DetectionService detectionService, DetectionEventPublisher eventPublisher) {
        this.detectionService = detectionService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public ResponseEntity<Void> ingestDetection(@Valid @RequestBody DetectionEnvelopeDto envelope) {
        DetectionProcessingResult result = detectionService.recordDetection(envelope);
        return ResponseEntity.created(URI.create("/api/detections/" + result.detection().getId())).build();
    }

    @GetMapping("/recent")
    public ResponseEntity<List<DetectionView>> recentDetections() {
        List<DetectionView> detections = detectionService.fetchRecentViews(20);
        return ResponseEntity.ok(detections);
    }

    @GetMapping("/sprays/recent")
    public ResponseEntity<List<SprayEventView>> recentSprays() {
        List<SprayEventView> sprays = detectionService.fetchRecentSprays(20);
        return ResponseEntity.ok(sprays);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<LiveEventDto> liveStream() {
        return eventPublisher.stream();
    }
}
