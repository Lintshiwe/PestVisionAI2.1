package com.pestvisionai.backend.service;

import com.pestvisionai.backend.config.PestVisionProperties;
import com.pestvisionai.backend.dto.BoundingBoxDto;
import com.pestvisionai.backend.dto.DetectionEnvelopeDto;
import com.pestvisionai.backend.model.BoundingBox;
import com.pestvisionai.backend.model.Detection;
import com.pestvisionai.backend.model.SprayEvent;
import com.pestvisionai.backend.repository.DetectionRepository;
import com.pestvisionai.backend.repository.SprayEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetectionService {

    private static final Logger log = LoggerFactory.getLogger(DetectionService.class);

    private final DetectionRepository detectionRepository;
    private final SprayEventRepository sprayEventRepository;
    private final SprayControllerClient sprayControllerClient;
    private final DetectionEventPublisher eventPublisher;
    private final GeminiAnalysisService geminiAnalysisService;
    private final double sprayConfidenceThreshold;
    private final Duration sprayCooldown;
    private Instant lastSprayInstant = Instant.EPOCH;

    public DetectionService(
            DetectionRepository detectionRepository,
            SprayEventRepository sprayEventRepository,
            SprayControllerClient sprayControllerClient,
            DetectionEventPublisher eventPublisher,
            GeminiAnalysisService geminiAnalysisService,
            PestVisionProperties properties) {
        this.detectionRepository = detectionRepository;
        this.sprayEventRepository = sprayEventRepository;
        this.sprayControllerClient = sprayControllerClient;
        this.eventPublisher = eventPublisher;
        this.geminiAnalysisService = geminiAnalysisService;
        this.sprayConfidenceThreshold = properties.getSpray().getConfidenceThreshold();
        this.sprayCooldown = Duration.ofSeconds(properties.getSpray().getCooldownSeconds());
    }

    @Transactional
    public DetectionProcessingResult recordDetection(DetectionEnvelopeDto envelope) {
        Objects.requireNonNull(envelope, "Detection envelope must not be null");
        Detection detection = toEntity(envelope);
    geminiAnalysisService.generateSummary(detection)
        .map(summary -> summary.length() > 2000 ? summary.substring(0, 2000) : summary)
        .ifPresent(detection::setAnalysisSummary);
        Detection saved = detectionRepository.save(detection);
        SprayEvent sprayEvent = maybeTriggerSpray(saved);
        DetectionProcessingResult result = new DetectionProcessingResult(saved, sprayEvent);
        eventPublisher.publish(DetectionMapper.toLiveEvent(result));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Detection> fetchRecent(int limit) {
        List<Detection> detections = detectionRepository.findTop50ByOrderByDetectedAtDesc();
        if (detections.isEmpty()) {
            return detections;
        }
        return detections.subList(0, Math.min(limit, detections.size()));
    }

    @Transactional(readOnly = true)
    public List<com.pestvisionai.backend.dto.DetectionView> fetchRecentViews(int limit) {
        return fetchRecent(limit).stream().map(DetectionMapper::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<com.pestvisionai.backend.dto.SprayEventView> fetchRecentSprays(int limit) {
        List<SprayEvent> events = sprayEventRepository.findTop50ByOrderByTriggeredAtDesc();
        if (events.isEmpty()) {
            return List.of();
        }
        return events.subList(0, Math.min(limit, events.size())).stream()
                .map(DetectionMapper::toView)
                .toList();
    }

    private Detection toEntity(DetectionEnvelopeDto envelope) {
        var payload = envelope.payload();
        Detection detection = new Detection();
        detection.setDetectedAt(payload.detectedAt());
        detection.setStreamId(payload.streamId());
        detection.setServiceName(envelope.serviceName());
        detection.setPestType(payload.pestType());
        detection.setPestCount(payload.pestCount());
        detection.setMaxConfidence(payload.maxConfidence());
        detection.setSnapshotPath(payload.snapshotPath());
    List<BoundingBox> mappedBoxes = payload.boxes() == null
        ? Collections.emptyList()
        : payload.boxes().stream().map(this::toEntity).toList();
        detection.setBoxes(mappedBoxes);
        return detection;
    }

    private BoundingBox toEntity(BoundingBoxDto dto) {
        BoundingBox box = new BoundingBox();
        box.setX(dto.x());
        box.setY(dto.y());
        box.setWidth(dto.width());
        box.setHeight(dto.height());
        box.setConfidence(dto.confidence());
        box.setLabel(dto.label());
        box.setTrackId(dto.trackId());
        return box;
    }

    private synchronized SprayEvent maybeTriggerSpray(Detection detection) {
        if (detection.getMaxConfidence() < sprayConfidenceThreshold) {
            log.debug("Detection {} skipped spray: confidence {} below threshold {}",
                    detection.getId(), detection.getMaxConfidence(), sprayConfidenceThreshold);
            return null;
        }
        Instant now = Instant.now();
        if (Duration.between(lastSprayInstant, now).compareTo(sprayCooldown) < 0) {
            log.debug("Detection {} skipped spray: cooldown active", detection.getId());
            return null;
        }
        SprayEvent sprayEvent = new SprayEvent();
        sprayEvent.setTriggeredAt(now);
        sprayEvent.setReason("Confidence >= " + sprayConfidenceThreshold);
        sprayEvent.setConfidence(detection.getMaxConfidence());
        sprayEvent.setDetectionId(detection.getId());
        SprayEvent saved = sprayEventRepository.save(sprayEvent);
        sprayControllerClient.triggerSpray(detection);
        lastSprayInstant = now;
        return saved;
    }

    private static class DetectionMapper {

        private DetectionMapper() {
        }

        private static com.pestvisionai.backend.dto.DetectionView toView(Detection detection) {
            return new com.pestvisionai.backend.dto.DetectionView(
                    detection.getId(),
                    detection.getDetectedAt(),
                    detection.getStreamId(),
                    detection.getServiceName(),
                    detection.getPestType(),
                    detection.getPestCount(),
                    detection.getMaxConfidence(),
                    detection.getSnapshotPath(),
            detection.getAnalysisSummary(),
                    detection.getBoxes().stream()
                            .map(box -> new BoundingBoxDto(
                                    box.getX(),
                                    box.getY(),
                                    box.getWidth(),
                                    box.getHeight(),
                                    box.getConfidence(),
                                    box.getLabel(),
                                    box.getTrackId()))
                .toList());
        }

        private static com.pestvisionai.backend.dto.SprayEventView toView(SprayEvent sprayEvent) {
            if (sprayEvent == null) {
                return null;
            }
            return new com.pestvisionai.backend.dto.SprayEventView(
                    sprayEvent.getId(),
                    sprayEvent.getTriggeredAt(),
                    sprayEvent.getReason(),
                    sprayEvent.getConfidence(),
                    sprayEvent.getDetectionId());
        }

        private static com.pestvisionai.backend.dto.LiveEventDto toLiveEvent(DetectionProcessingResult result) {
            return new com.pestvisionai.backend.dto.LiveEventDto(
                    toView(result.detection()),
                    result.sprayEventOptional().map(DetectionMapper::toView).orElse(null));
        }
    }
}
