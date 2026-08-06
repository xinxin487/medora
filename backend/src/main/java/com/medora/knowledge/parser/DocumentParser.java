package com.medora.knowledge.parser;

import com.medora.common.exception.BusinessException;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import org.apache.tika.exception.TikaException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class DocumentParser {

    private final Tika tika = new Tika();

    public ParsedDocument parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("KB_DOCUMENT_EMPTY", HttpStatus.BAD_REQUEST, "knowledge document is empty");
        }

        String sourceType = resolveSourceType(file.getOriginalFilename());
        try {
            String content = extractText(file.getInputStream(), file.getOriginalFilename(), sourceType);
            if (content.isBlank()) {
                throw new BusinessException("KB_DOCUMENT_EMPTY", HttpStatus.BAD_REQUEST,
                        "knowledge document content is empty");
            }
            return new ParsedDocument(sourceType, content);
        } catch (IOException | TikaException ex) {
            throw new BusinessException("KB_DOCUMENT_PARSE_FAILED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "knowledge document parse failed");
        }
    }

    public ParsedDocument parseBytes(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException("KB_DOCUMENT_EMPTY", HttpStatus.BAD_REQUEST, "knowledge document is empty");
        }

        String sourceType = resolveSourceType(filename);
        try {
            String content = extractText(bytes, filename, sourceType);
            if (content.isBlank()) {
                throw new BusinessException("KB_DOCUMENT_EMPTY", HttpStatus.BAD_REQUEST,
                        "knowledge document content is empty");
            }
            return new ParsedDocument(sourceType, content);
        } catch (IOException | TikaException ex) {
            throw new BusinessException("KB_DOCUMENT_PARSE_FAILED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "knowledge document parse failed");
        }
    }

    private String extractText(InputStream inputStream, String filename, String sourceType) throws IOException, TikaException {
        if ("MD".equals(sourceType) || "TEXT".equals(sourceType)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        return tika.parseToString(inputStream).trim();
    }

    private String extractText(byte[] bytes, String filename, String sourceType) throws IOException, TikaException {
        if ("MD".equals(sourceType) || "TEXT".equals(sourceType)) {
            return new String(bytes, StandardCharsets.UTF_8).trim();
        }
        return tika.parseToString(new java.io.ByteArrayInputStream(bytes)).trim();
    }

    String resolveSourceType(String filename) {
        String normalized = filename == null ? "" : filename.toLowerCase();
        if (normalized.endsWith(".md")) {
            return "MD";
        }
        if (normalized.endsWith(".txt")) {
            return "TEXT";
        }
        if (normalized.endsWith(".pdf")) {
            return "PDF";
        }
        if (normalized.endsWith(".docx") || normalized.endsWith(".doc")) {
            return "DOCX";
        }
        if (normalized.endsWith(".xlsx") || normalized.endsWith(".xls")) {
            return "XLSX";
        }
        return "MANUAL";
    }

    public record ParsedDocument(String sourceType, String content) {
    }
}
