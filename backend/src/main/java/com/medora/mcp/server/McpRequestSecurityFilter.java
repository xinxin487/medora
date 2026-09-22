package com.medora.mcp.server;
import com.medora.mcp.config.McpProperties;
import jakarta.servlet.FilterChain; import jakarta.servlet.ServletException; import jakarta.servlet.http.HttpServletRequest; import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException; import java.nio.charset.StandardCharsets; import java.security.MessageDigest;
@Component public class McpRequestSecurityFilter extends OncePerRequestFilter {
 private static final long MAX_BODY_BYTES=64*1024;
 private final McpProperties properties; public McpRequestSecurityFilter(McpProperties properties){this.properties=properties;}
 protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().equals("/mcp")||!properties.isEnabled();}
 protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c)throws ServletException,IOException{
  if("POST".equalsIgnoreCase(r.getMethod())){long contentLength=r.getContentLengthLong(); if(contentLength<0||contentLength>MAX_BODY_BYTES){s.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);return;}}
  String expected="Bearer "+properties.getServerToken(); String actual=r.getHeader("Authorization"); if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),(actual==null?"":actual).getBytes(StandardCharsets.UTF_8))){s.sendError(401);return;}
  String origin=r.getHeader("Origin"); if(origin==null||!properties.getAllowedOrigins().contains(origin)){s.sendError(403);return;} c.doFilter(r,s);
 }
}
