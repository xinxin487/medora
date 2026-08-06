package com.medora.common.controller;

import com.medora.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "健康检查", description = "服务健康状态")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String phase;

    public HealthController(@Value("${medora.phase:phase-unknown}") String phase) {
        this.phase = phase;
    }

    @Operation(summary = "健康检查")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success(Map.of(
                "service", "medora-backend",
                "status", "UP",
                "phase", phase
        ));
    }
}
