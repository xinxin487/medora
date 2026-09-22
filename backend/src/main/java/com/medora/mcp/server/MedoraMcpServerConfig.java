package com.medora.mcp.server;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.toolgateway.execution.ToolGateway;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Configuration;
import java.util.List; import java.util.Map;

@Configuration @ConditionalOnProperty(prefix="medora.mcp",name="enabled",havingValue="true")
public class MedoraMcpServerConfig {
 @Bean HttpServletStreamableServerTransportProvider mcpTransport(ObjectMapper mapper){return HttpServletStreamableServerTransportProvider.builder().mcpEndpoint("/mcp").jsonMapper(new JacksonMcpJsonMapper(mapper)).disallowDelete(true).build();}
 @Bean McpSyncServer medoraMcpServer(HttpServletStreamableServerTransportProvider transport, ObjectMapper mapper,
                                     ToolGateway gateway){
  SearchMedicalKnowledgeMcpTool knowledge = new SearchMedicalKnowledgeMcpTool(gateway);
  QueryScheduleSlotsMcpTool schedules = new QueryScheduleSlotsMcpTool(gateway);
  return McpServer.sync(transport).serverInfo("medora","1.0.0").instructions("Read-only Medora MCP server").strictToolNameValidation(true).validateToolInputs(true)
   .toolCall(knowledgeTool(),(exchange,request)->result(mapper,()->knowledge.execute(request.arguments())))
   .toolCall(scheduleTool(),(exchange,request)->result(mapper,()->schedules.execute(request.arguments())))
   .build();}
 @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(HttpServletStreamableServerTransportProvider transport){return new ServletRegistrationBean<>(transport,"/mcp");}
 private McpSchema.Tool knowledgeTool(){return new McpSchema.Tool("searchMedicalKnowledge",null,"Search published medical knowledge",schema(Map.of("query",Map.of("type","string","minLength",1)),List.of("query")),Map.of(),null,Map.of());}
 private McpSchema.Tool scheduleTool(){return new McpSchema.Tool("queryScheduleSlots",null,"Query open schedule slots",schema(Map.of("scheduleDate",Map.of("type","string","minLength",1)),List.of("scheduleDate")),Map.of(),null,Map.of());}
 private Map<String,Object> schema(Map<String,Object> props,List<String> required){return Map.of("type","object","properties",props,"required",required,"additionalProperties",false);}
 private McpSchema.CallToolResult result(ObjectMapper mapper,java.util.concurrent.Callable<Object> action){try{Object data=action.call();return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(mapper.writeValueAsString(data))),false,data,Map.of());}catch(Exception ignored){return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("tool unavailable")),true,null,Map.of());}}
}
