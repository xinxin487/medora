package com.medora.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.common.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiErrorResponse.of(code, message, List.of()));
    }
}
