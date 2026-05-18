package com.medora.knowledge.service;

import com.medora.auth.domain.UserAccountEntity;
import com.medora.auth.repository.UserAccountRepository;
import com.medora.auth.security.AuthenticatedUser;
import com.medora.auth.security.CurrentAuthenticatedUserProvider;
import com.medora.common.api.PageResponse;
import com.medora.common.audit.AuditLogService;
import com.medora.common.exception.BusinessException;
import com.medora.knowledge.api.query.KnowledgeDocumentListQuery;
import com.medora.knowledge.api.request.KnowledgePublishRequest;
import com.medora.knowledge.api.response.KnowledgeDocumentItem;
import com.medora.knowledge.api.response.KnowledgeDocumentResponse;
import com.medora.knowledge.api.response.KnowledgeUploadResponse;
import com.medora.knowledge.domain.KnowledgeDocumentEntity;
import com.medora.knowledge.parser.DocumentParser;
import com.medora.knowledge.repository.KnowledgeChunkRepository;
import com.medora.knowledge.repository.KnowledgeDocumentRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeDocumentService {

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final UserAccountRepository userAccountRepository;
    private final CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider;
    private final DocumentParser documentParser;
    private final DocumentIngestService documentIngestService;
    private final KnowledgeJsonService knowledgeJsonService;
    private final AuditLogService auditLogService;

    public KnowledgeDocumentService(KnowledgeDocumentRepository knowledgeDocumentRepository,
                                    KnowledgeChunkRepository knowledgeChunkRepository,
                                    UserAccountRepository userAccountRepository,
                                    CurrentAuthenticatedUserProvider currentAuthenticatedUserProvider,
                                    DocumentParser documentParser,
                                    DocumentIngestService documentIngestService,
                                    KnowledgeJsonService knowledgeJsonService,
                                    AuditLogService auditLogService) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.userAccountRepository = userAccountRepository;
        this.currentAuthenticatedUserProvider = currentAuthenticatedUserProvider;
        this.documentParser = documentParser;
        this.documentIngestService = documentIngestService;
        this.knowledgeJsonService = knowledgeJsonService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public KnowledgeUploadResponse upload(MultipartFile file,
                                          String title,
                                          String bizScope,
                                          String departmentTags,
                                          String doctorTags) {
        DocumentParser.ParsedDocument parsed = documentParser.parse(file);
        String normalizedTitle = normalizeTitle(title, file.getOriginalFilename());
        String normalizedBizScope = normalizeBizScope(bizScope);
        UserAccountEntity uploader = getCurrentUserAccount();

        KnowledgeDocumentEntity document = knowledgeDocumentRepository.save(KnowledgeDocumentEntity.uploaded(
                normalizedTitle,
                parsed.sourceType(),
                file.getOriginalFilename(),
                normalizedBizScope,
                parseTags(departmentTags),
                parseTags(doctorTags),
                sha256(parsed.content()),
                knowledgeJsonService.toJson(Map.of(
                        "originalFilename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
                        "size", file.getSize()
                )),
                uploader
        ));

        int chunkCount = documentIngestService.ingest(document, parsed.content());
        auditLogService.record(
                uploader,
                "ADMIN",
                "KB_DOCUMENT_UPLOAD",
                "KNOWLEDGE_DOCUMENT",
                document.getPublicId().toString(),
                Map.of(
                        "title", document.getTitle(),
                        "bizScope", document.getBizScope(),
                        "chunkCount", chunkCount,
                        "publishStatus", document.getPublishStatus()
                )
        );
        return new KnowledgeUploadResponse(
                document.getPublicId().toString(),
                document.getTitle(),
                document.getBizScope(),
                document.getPublishStatus(),
                document.getIngestStatus(),
                chunkCount
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<KnowledgeDocumentItem> list(KnowledgeDocumentListQuery query) {
        PageRequest pageRequest = PageRequest.of(
                Math.max((query.pageNo() == null ? 1 : query.pageNo()) - 1, 0),
                Math.min(Math.max(query.pageSize() == null ? 20 : query.pageSize(), 1), 100),
                Sort.by(Sort.Direction.DESC, "uploadedAt")
        );
        Page<KnowledgeDocumentItem> page = knowledgeDocumentRepository
                .findAll(toSpec(query), pageRequest)
                .map(document -> KnowledgeDocumentMapper.toItem(document, knowledgeChunkRepository));
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public KnowledgeDocumentResponse get(String documentId) {
        KnowledgeDocumentEntity document = findByPublicId(documentId);
        return KnowledgeDocumentMapper.toResponse(document, knowledgeChunkRepository);
    }

    @Transactional
    public KnowledgeDocumentResponse rebuild(String documentId) {
        KnowledgeDocumentEntity document = findByPublicId(documentId);
        documentIngestService.rebuildEmbeddings(document);
        auditLogService.record(
                getCurrentUserAccount(),
                "ADMIN",
                "KB_DOCUMENT_REBUILD",
                "KNOWLEDGE_DOCUMENT",
                document.getPublicId().toString(),
                Map.of(
                        "title", document.getTitle(),
                        "bizScope", document.getBizScope(),
                        "ingestStatus", document.getIngestStatus()
                )
        );
        return KnowledgeDocumentMapper.toResponse(document, knowledgeChunkRepository);
    }

    @Transactional
    public KnowledgeDocumentResponse publish(String documentId, KnowledgePublishRequest request) {
        KnowledgeDocumentEntity document = findByPublicId(documentId);
        String action = request.publishAction().trim().toUpperCase();
        if ("PUBLISH".equals(action)) {
            if (!"INDEXED".equalsIgnoreCase(document.getIngestStatus())) {
                throw new BusinessException("KB_DOCUMENT_NOT_INDEXED", HttpStatus.UNPROCESSABLE_ENTITY,
                        "document must be indexed before publish");
            }
            document.publish();
        } else if ("OFFLINE".equals(action)) {
            document.offline();
        } else {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST,
                    "publishAction must be PUBLISH or OFFLINE");
        }
        auditLogService.record(
                getCurrentUserAccount(),
                "ADMIN",
                "KB_DOCUMENT_PUBLISH",
                "KNOWLEDGE_DOCUMENT",
                document.getPublicId().toString(),
                Map.of(
                        "title", document.getTitle(),
                        "action", action,
                        "publishStatus", document.getPublishStatus()
                )
        );
        return KnowledgeDocumentMapper.toResponse(document, knowledgeChunkRepository);
    }

    private Specification<KnowledgeDocumentEntity> toSpec(KnowledgeDocumentListQuery query) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(query.title())) {
                predicates.add(builder.like(builder.lower(root.get("title")),
                        "%" + query.title().trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(query.bizScope())) {
                predicates.add(builder.equal(root.get("bizScope"), query.bizScope().trim().toUpperCase()));
            }
            if (StringUtils.hasText(query.publishStatus())) {
                predicates.add(builder.equal(root.get("publishStatus"), query.publishStatus().trim().toUpperCase()));
            }
            if (StringUtils.hasText(query.ingestStatus())) {
                predicates.add(builder.equal(root.get("ingestStatus"), query.ingestStatus().trim().toUpperCase()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private KnowledgeDocumentEntity findByPublicId(String documentId) {
        try {
            return knowledgeDocumentRepository.findByPublicId(UUID.fromString(documentId))
                    .orElseThrow(() -> new BusinessException("KB_DOCUMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                            "knowledge document not found"));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST,
                    "documentId must be a valid uuid");
        }
    }

    private UserAccountEntity getCurrentUserAccount() {
        AuthenticatedUser authenticatedUser = currentAuthenticatedUserProvider.getRequiredUser();
        return userAccountRepository.findByPublicId(authenticatedUser.userPublicId())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "current user not found"));
    }

    private String normalizeTitle(String title, String filename) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        if (StringUtils.hasText(filename)) {
            return filename.trim();
        }
        return "未命名知识文档";
    }

    private String normalizeBizScope(String bizScope) {
        String normalized = StringUtils.hasText(bizScope) ? bizScope.trim().toUpperCase() : "FAQ";
        List<String> allowed = List.of("GUIDE", "APPOINTMENT_RULE", "DEPARTMENT_INFO", "DOCTOR_INFO", "FAQ");
        if (!allowed.contains(normalized)) {
            throw new BusinessException("COMMON_INVALID_PARAM", HttpStatus.BAD_REQUEST, "unsupported bizScope");
        }
        return normalized;
    }

    private List<String> parseTags(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[,，]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String sha256(String content) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not supported", ex);
        }
    }
}
