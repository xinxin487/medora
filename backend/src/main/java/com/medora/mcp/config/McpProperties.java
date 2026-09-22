package com.medora.mcp.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI; import java.util.List;
@ConfigurationProperties("medora.mcp") public class McpProperties {
 private boolean enabled; private String serverToken; private List<String> allowedOrigins=List.of(); private List<String> allowedRemoteHosts=List.of(); private List<RemoteServer> remoteServers=List.of();
 public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} public String getServerToken(){return serverToken;} public void setServerToken(String v){serverToken=v;} public List<String> getAllowedOrigins(){return allowedOrigins;} public void setAllowedOrigins(List<String> v){allowedOrigins=v;} public List<String> getAllowedRemoteHosts(){return allowedRemoteHosts;} public void setAllowedRemoteHosts(List<String> v){allowedRemoteHosts=v;} public List<RemoteServer> getRemoteServers(){return remoteServers;} public void setRemoteServers(List<RemoteServer> v){remoteServers=v;}
 public void validate(){if(!enabled)return; if(serverToken==null||serverToken.isBlank()||allowedOrigins==null||allowedOrigins.isEmpty())throw new IllegalArgumentException("MCP token and allowed origins are required"); for(var r:remoteServers)r.validate(allowedRemoteHosts);}
 public static class RemoteServer {private String name,baseUrl,tokenEnvName; private List<String> allowedTools=List.of();
  public String getName(){return name;} public String getBaseUrl(){return baseUrl;} public String getTokenEnvName(){return tokenEnvName;} public List<String> getAllowedTools(){return List.copyOf(allowedTools);}
  public void setName(String v){name=v;} public void setBaseUrl(String v){baseUrl=v;} public void setTokenEnvName(String v){tokenEnvName=v;} public void setAllowedTools(List<String> v){allowedTools=v;}
  public void validate(List<String> allowedHosts){try{URI u=URI.create(baseUrl);if(!"https".equals(u.getScheme())||u.getUserInfo()!=null||u.getFragment()!=null||u.getHost()==null)throw new IllegalArgumentException("invalid baseUrl");if(allowedHosts==null||!allowedHosts.contains(u.getHost()))throw new IllegalArgumentException("remote host is not in allow-list");}catch(IllegalArgumentException e){throw e;}catch(RuntimeException e){throw new IllegalArgumentException("invalid baseUrl");} if(name==null||name.isBlank()||tokenEnvName==null||tokenEnvName.isBlank()||allowedTools.isEmpty())throw new IllegalArgumentException("invalid MCP remote server");}
 }
}
