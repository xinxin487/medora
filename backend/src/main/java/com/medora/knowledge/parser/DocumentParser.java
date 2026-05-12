package com.medora.knowledge.parser;

import com.medora.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class DocumentParser {

    public ParsedDocument parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("KB_DOCUMENT_EMPTY", HttpStatus.BAD_REQUEST, "knowledge document is empty");
        }

        String sourceType = resolveSourceType(file.getOriginalFilename());
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
            if (content.isBlank()) {
                throw new BusinessException("KB_DOCUMENT_EMPTY", HttpStatus.BAD_REQUEST,
                        "knowledge document content is empty");
            }
            return new ParsedDocument(sourceType, content);
        } catch (IOException ex) {
            throw new BusinessException("KB_DOCUMENT_PARSE_FAILED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "knowledge document parse failed");
        }
    }

    private String resolveSourceType(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase();
        if (normalized.endsWith(".md")) {
            return "MD";
        }
        if (normalized.endsWith(".txt")) {
            return "TEXT";
        }
        return "MANUAL";
    }

    public record ParsedDocument(String sourceType, String content) {
    }
}
