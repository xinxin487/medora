package com.medora.common.exception;

import com.medora.common.api.ErrorDetail;
import org.springframework.http.HttpStatus;

import java.util.List;

public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final List<ErrorDetail> details;

    public BusinessException(String code, HttpStatus httpStatus, String message) {
        this(code, httpStatus, message, List.of());
    }

    public BusinessException(String code, HttpStatus httpStatus, String message, List<ErrorDetail> details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}
