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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/documents/upload")
    public ApiResponse<KnowledgeUploadResponse> upload(@RequestPart("file") MultipartFile file,
                                                       @RequestParam(required = false) String title,
                                                       @RequestParam(required = false) String bizScope,
                                                       @RequestParam(required = false) String departmentTags,
                                                       @RequestParam(required = false) String doctorTags) {
        return ApiResponse.success(
                knowledgeDocumentService.upload(file, title, bizScope, departmentTags, doctorTags)
        );
    }

    @GetMapping("/documents")
    public ApiResponse<PageResponse<KnowledgeDocumentItem>> list(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String bizScope,
            @RequestParam(required = false) String publishStatus,
            @RequestParam(required = false) String ingestStatus,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize
    ) {
        return ApiResponse.success(
                knowledgeDocumentService.list(
                        new KnowledgeDocumentListQuery(title, bizScope, publishStatus, ingestStatus, pageNo, pageSize)
                )
        );
    }

    @GetMapping("/documents/{documentId}")
    public ApiResponse<KnowledgeDocumentResponse> get(@PathVariable String documentId) {
        return ApiResponse.success(knowledgeDocumentService.get(documentId));
    }

    @PostMapping("/documents/{documentId}/rebuild")
    public ApiResponse<KnowledgeDocumentResponse> rebuild(@PathVariable String documentId,
                                                          @RequestBody(required = false)
                                                          KnowledgeRebuildRequest request) {
        return ApiResponse.success(knowledgeDocumentService.rebuild(documentId));
    }

    @PostMapping("/documents/{documentId}/publish")
    public ApiResponse<KnowledgeDocumentResponse> publish(@PathVariable String documentId,
                                                          @Valid @RequestBody KnowledgePublishRequest request) {
        return ApiResponse.success(knowledgeDocumentService.publish(documentId, request));
    }

    @PostMapping("/search/test")
    public ApiResponse<KnowledgeSearchResponse> search(@Valid @RequestBody KnowledgeSearchTestRequest request) {
        return ApiResponse.success(
                retrievalService.searchForAdmin(request.query(), request.bizScope(), request.topK())
        );
    }
}
