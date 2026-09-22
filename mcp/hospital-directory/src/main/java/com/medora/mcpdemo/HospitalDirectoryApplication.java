package com.medora.mcpdemo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class HospitalDirectoryApplication {
    public static void main(String[] args) { SpringApplication.run(HospitalDirectoryApplication.class, args); }

    @Bean HttpServletStreamableServerTransportProvider mcpTransport(ObjectMapper mapper) {
        ObjectMapper transportMapper = mapper.copy().addMixIn(McpError.class, McpErrorMixin.class);
        return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp")
                .jsonMapper(new JacksonMcpJsonMapper(transportMapper)).disallowDelete(true).build();
    }

    @Bean McpSyncServer hospitalDirectoryMcpServer(HttpServletStreamableServerTransportProvider transport, ObjectMapper mapper,
                                                    FindDepartmentTool directory) {
        Map<String, Object> input = Map.of("type", "object", "properties", Map.of("symptoms", Map.of("type", "string", "minLength", 1)),
                "required", List.of("symptoms"), "additionalProperties", false);
        McpSchema.Tool tool = new McpSchema.Tool("findDepartment", null, "Find a suitable department from symptoms", input,
                Map.of("type", "object"), null, Map.of());
        return McpServer.sync(transport).serverInfo("hospital-directory", "1.0.0").instructions("Read-only hospital directory")
                .strictToolNameValidation(true).validateToolInputs(true)
                .toolCall(tool, (exchange, request) -> {
                    try {
                        String symptoms = (String) request.arguments().get("symptoms");
                        FindDepartmentTool.Result result = directory.findDepartment(symptoms);
                        Map<String, Object> structured = mapper.convertValue(result, Map.class);
                        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(mapper.writeValueAsString(structured))), false, structured, Map.of());
                    } catch (Exception ignored) {
                        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("tool unavailable")), true, null, Map.of());
                    }
                }).build();
    }

    @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(HttpServletStreamableServerTransportProvider transport) {
        return new ServletRegistrationBean<>(transport, "/mcp");
    }

    @Bean FilterRegistrationBean<Filter> mcpSecurityFilter(@Value("${hospital-directory.token:}") String token,
                                                             @Value("${hospital-directory.allowed-origin:}") String allowedOrigin) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new McpSecurityFilter(token, allowedOrigin));
        registration.addUrlPatterns("/mcp");
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    private static final class McpSecurityFilter extends OncePerRequestFilter {
        private final String token; private final String allowedOrigin;
        private McpSecurityFilter(String token, String allowedOrigin) { this.token = token; this.allowedOrigin = allowedOrigin; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, jakarta.servlet.FilterChain chain)
                throws java.io.IOException, jakarta.servlet.ServletException {
            String authorization = request.getHeader("Authorization");
            String origin = request.getHeader("Origin");
            if (!MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8), String.valueOf(authorization).getBytes(StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED); return;
            }
            if (!allowedOrigin.equals(origin)) { response.sendError(HttpServletResponse.SC_FORBIDDEN); return; }
            chain.doFilter(request, response);
        }
    }

    @JsonIgnoreProperties({"cause", "stackTrace", "message", "suppressed", "localizedMessage"})
    private abstract static class McpErrorMixin {}
}
