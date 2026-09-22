package com.medora.mcp.server;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.mcp.config.McpProperties;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;

@Configuration @ConditionalOnProperty(prefix="medora.mcp",name="enabled",havingValue="true")
public class MedoraMcpServerConfig {
 @Bean HttpServletStreamableServerTransportProvider mcpTransport(ObjectMapper mapper){return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp").jsonMapper(new JacksonMcpJsonMapper(mapper)).disallowDelete(true).build();}
 @Bean McpSyncServer medoraMcpServer(HttpServletStreamableServerTransportProvider transport){return McpServer.sync(transport).serverInfo("medora","1.0.0").instructions("Read-only Medora MCP server").strictToolNameValidation(true).validateToolInputs(true).build();}
 @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(HttpServletStreamableServerTransportProvider transport){return new ServletRegistrationBean<>(transport,"/mcp");}
}
