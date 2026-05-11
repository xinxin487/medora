package com.medora.common.controller;

import com.medora.common.api.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String phase;

    public HealthController(@Value("${medora.phase:phase-unknown}") String phase) {
        this.phase = phase;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "service", "medora-backend",
                "status", "UP",
                "phase", phase
        ));
    }
}
