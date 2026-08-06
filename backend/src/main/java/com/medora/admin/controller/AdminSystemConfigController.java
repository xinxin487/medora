package com.medora.admin.controller;

import com.medora.admin.api.request.UpdateSystemConfigRequest;
import com.medora.admin.api.response.SystemConfigResponse;
import com.medora.ai.config.AiProperties;
import com.medora.common.api.ApiResponse;
import com.medora.knowledge.config.RagProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理后台 - 系统配置", description = "查询与更新系统级配置参数")
@RestController
@RequestMapping("/api/v1/admin/system-config")
public class AdminSystemConfigController {

    private final AiProperties aiProperties;
    private final RagProperties ragProperties;

    public AdminSystemConfigController(AiProperties aiProperties, RagProperties ragProperties) {
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
    }

    @Operation(summary = "获取当前系统配置")
    @GetMapping
    public ApiResponse<SystemConfigResponse> get() {
        return ApiResponse.success(new SystemConfigResponse(
                new SystemConfigResponse.RagConfig(
                        ragProperties.getDefaultTopK(),
                        ragProperties.getMaxTopK(),
                        ragProperties.getMinScoreThreshold(),
                        ragProperties.getKeywordWeight(),
                        ragProperties.getVectorWeight()
                ),
                new SystemConfigResponse.AiConfig(
                        aiProperties.getProvider(),
                        aiProperties.getChatModel(),
                        aiProperties.getEmbeddingModel(),
                        aiProperties.getEmbeddingDimensions(),
                        aiProperties.getContextCacheTtlSeconds()
                )
        ));
    }

    @Operation(summary = "更新系统配置（RAG 参数即时生效）")
    @PutMapping
    public ApiResponse<SystemConfigResponse> update(@Valid @RequestBody UpdateSystemConfigRequest request) {
        UpdateSystemConfigRequest.RagConfig rag = request.rag();
        ragProperties.setDefaultTopK(rag.defaultTopK());
        ragProperties.setMaxTopK(rag.maxTopK());
        ragProperties.setMinScoreThreshold(rag.minScoreThreshold());
        ragProperties.setKeywordWeight(rag.keywordWeight());
        ragProperties.setVectorWeight(rag.vectorWeight());
        return ApiResponse.success(new SystemConfigResponse(
                new SystemConfigResponse.RagConfig(
                        ragProperties.getDefaultTopK(),
                        ragProperties.getMaxTopK(),
                        ragProperties.getMinScoreThreshold(),
                        ragProperties.getKeywordWeight(),
                        ragProperties.getVectorWeight()
                ),
                new SystemConfigResponse.AiConfig(
                        aiProperties.getProvider(),
                        aiProperties.getChatModel(),
                        aiProperties.getEmbeddingModel(),
                        aiProperties.getEmbeddingDimensions(),
                        aiProperties.getContextCacheTtlSeconds()
                )
        ));
    }
}
