package com.medora.knowledge.controller;

import com.medora.common.api.ApiResponse;
import com.medora.common.api.PageResponse;
import com.medora.knowledge.api.query.KnowledgeDocumentListQuery;
import com.medora.knowledge.api.request.KnowledgePublishRequest;
import com.medora.knowledge.api.request.KnowledgeRebuildRequest;
import com.medora.knowledge.api.request.KnowledgeSearchTestRequest;
import com.medora.knowledge.api.response.KnowledgeDocumentItem;
import com.medora.knowledge.api.response.KnowledgeDocumentResponse;
import com.medora.knowledge.api.response.KnowledgeSearchResponse;
import com.medora.knowledge.api.response.KnowledgeUploadResponse;
import com.medora.knowledge.service.KnowledgeDocumentService;
import com.medora.knowledge.service.RetrievalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "管理后台 - 知识库", description = "文档上传、检索重建、发布管理")
@RestController
@RequestMapping("/api/v1/admin/knowledge")
public class AdminKnowledgeController {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final RetrievalService retrievalService;

    public AdminKnowledgeController(KnowledgeDocumentService knowledgeDocumentService,
                                    RetrievalService retrievalService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.retrievalService = retrievalService;
    }

    @Operation(summary = "上传知识库文档")
    @PostMapping("/documents/upload")
    public ApiResponse<KnowledgeUploadResponse> upload(
            @Parameter(description = "文件，支持 PDF/Word/TXT") @RequestPart("file") MultipartFile file,
            @Parameter(description = "文档标题") @RequestParam(required = false) String title,
            @Parameter(description = "业务范围") @RequestParam(required = false) String bizScope,
            @Parameter(description = "科室标签") @RequestParam(required = false) String departmentTags,
            @Parameter(description = "医生标签") @RequestParam(required = false) String doctorTags) {
        return ApiResponse.success(
                knowledgeDocumentService.upload(file, title, bizScope, departmentTags, doctorTags)
        );
    }

    @Operation(summary = "查询知识库文档列表")
    @GetMapping("/documents")
    public ApiResponse<PageResponse<KnowledgeDocumentItem>> list(
            @Parameter(description = "标题搜索") @RequestParam(required = false) String title,
            @Parameter(description = "业务范围") @RequestParam(required = false) String bizScope,
            @Parameter(description = "发布状态") @RequestParam(required = false) String publishStatus,
            @Parameter(description = "索引进度") @RequestParam(required = false) String ingestStatus,
            @Parameter(description = "页码，从 1 开始") @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数") @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                knowledgeDocumentService.list(
                        new KnowledgeDocumentListQuery(title, bizScope, publishStatus, ingestStatus, pageNo, pageSize)
                )
        );
    }

    @Operation(summary = "查询文档详情")
    @GetMapping("/documents/{documentId}")
    public ApiResponse<KnowledgeDocumentResponse> get(
            @Parameter(description = "文档 ID") @PathVariable String documentId) {
        return ApiResponse.success(knowledgeDocumentService.get(documentId));
    }

    @Operation(summary = "重建文档向量索引")
    @PostMapping("/documents/{documentId}/rebuild")
    public ApiResponse<KnowledgeDocumentResponse> rebuild(
            @Parameter(description = "文档 ID") @PathVariable String documentId,
            @RequestBody(required = false) KnowledgeRebuildRequest request) {
        return ApiResponse.success(knowledgeDocumentService.rebuild(documentId));
    }

    @Operation(summary = "发布/下架文档")
    @PostMapping("/documents/{documentId}/publish")
    public ApiResponse<KnowledgeDocumentResponse> publish(
            @Parameter(description = "文档 ID") @PathVariable String documentId,
            @Valid @RequestBody KnowledgePublishRequest request) {
        return ApiResponse.success(knowledgeDocumentService.publish(documentId, request));
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> delete(
            @Parameter(description = "文档 ID") @PathVariable String documentId) {
        knowledgeDocumentService.delete(documentId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "检索测试，验证检索质量")
    @PostMapping("/search/test")
    public ApiResponse<KnowledgeSearchResponse> search(@Valid @RequestBody KnowledgeSearchTestRequest request) {
        return ApiResponse.success(
                retrievalService.searchForAdmin(request.query(), request.bizScope(), request.topK())
        );
    }
}
