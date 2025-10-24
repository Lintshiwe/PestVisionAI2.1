package com.pestvisionai.backend.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    @GetMapping({"/", "/dashboard"})
    public ResponseEntity<Map<String, Object>> rootMessage() {
        return ResponseEntity.ok(Map.of(
                "message", "PestVisionAI backend is running",
                "documentation", "See README for available API routes",
                "liveFeed", "http://localhost:3000/"
        ));
    }
}
