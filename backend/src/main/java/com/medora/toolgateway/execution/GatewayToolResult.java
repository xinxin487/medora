package com.medora.toolgateway.execution;
public record GatewayToolResult(Status status, Object data, GatewayErrorCode errorCode, String message, boolean retryable) {
    public enum Status { SUCCESS, FAILED, DENIED, TIMEOUT, UNAVAILABLE, RATE_LIMITED, CONFIRMATION_REQUIRED }
    public static GatewayToolResult success(Object data) { return new GatewayToolResult(Status.SUCCESS, data, null, null, false); }
    public static GatewayToolResult failure(Status status, GatewayErrorCode code, String message, boolean retryable) { return new GatewayToolResult(status, null, code, message, retryable); }
}
