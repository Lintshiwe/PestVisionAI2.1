package com.pestvisionai.backend.controller;

import com.pestvisionai.backend.config.PestVisionProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final PestVisionProperties properties;

    public DashboardController(PestVisionProperties properties) {
        this.properties = properties;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("streamUrl", properties.getVision().getStreamUrl());
        return "dashboard";
    }
}
