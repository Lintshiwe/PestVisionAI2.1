package com.pestvisionai.backend.service;

import com.pestvisionai.backend.dto.LiveEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class DetectionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DetectionEventPublisher.class);
    private final Sinks.Many<LiveEventDto> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(LiveEventDto event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("Failed to emit live event: {}", result);
        }
    }

    public Flux<LiveEventDto> stream() {
        return sink.asFlux();
    }
}
