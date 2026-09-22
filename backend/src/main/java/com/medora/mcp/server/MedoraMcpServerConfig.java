package com.medora.mcp.server;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medora.mcp.config.McpProperties;
import com.medora.knowledge.service.RetrievalService;
import com.medora.masterdata.api.query.ScheduleSlotListQuery;
import com.medora.masterdata.service.ScheduleSlotQueryService;
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
                                     RetrievalService retrieval, ScheduleSlotQueryService schedules){
  return McpServer.sync(transport).serverInfo("medora","1.0.0").instructions("Read-only Medora MCP server").strictToolNameValidation(true).validateToolInputs(true)
   .toolCall(knowledgeTool(),(exchange,request)->result(mapper,()->retrieval.searchForAdmin(text(request,"query"),text(request,"bizScope"),number(request,"topK",5))))
   .toolCall(scheduleTool(),(exchange,request)->result(mapper,()->schedules.list(new ScheduleSlotListQuery(text(request,"departmentId"),text(request,"doctorId"),text(request,"scheduleDate"),text(request,"periodCode"),"OPEN",1,Math.min(number(request,"pageSize",20),100)))))
   .build();}
 @Bean ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(HttpServletStreamableServerTransportProvider transport){return new ServletRegistrationBean<>(transport,"/mcp");}
 private McpSchema.Tool knowledgeTool(){return new McpSchema.Tool("searchMedicalKnowledge",null,"Search published medical knowledge",schema(Map.of("query",Map.of("type","string"))),Map.of(),null,Map.of());}
 private McpSchema.Tool scheduleTool(){return new McpSchema.Tool("queryScheduleSlots",null,"Query open schedule slots",schema(Map.of("scheduleDate",Map.of("type","string"))),Map.of(),null,Map.of());}
 private Map<String,Object> schema(Map<String,Object> props){return Map.of("type","object","properties",props,"additionalProperties",false);}
 private String text(McpSchema.CallToolRequest r,String key){Object value=r.arguments().get(key);return value instanceof String s?s:null;}
 private int number(McpSchema.CallToolRequest r,String key,int fallback){Object value=r.arguments().get(key);return value instanceof Number n?n.intValue():fallback;}
 private McpSchema.CallToolResult result(ObjectMapper mapper,java.util.concurrent.Callable<Object> action){try{return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(mapper.writeValueAsString(action.call()))),false,null,Map.of());}catch(Exception ignored){return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("tool unavailable")),true,null,Map.of());}}
}
