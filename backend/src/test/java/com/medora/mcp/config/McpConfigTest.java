package com.medora.mcp.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpConfigTest {
 @Test void rejectsUnsafeRemoteUrlsBeforeAnyConnectionCanBeMade() {
  McpProperties properties=new McpProperties();
  properties.setEnabled(true); properties.setServerToken("token"); properties.setAllowedOrigins(List.of("https://medora.example"));
  McpProperties.RemoteServer remote=new McpProperties.RemoteServer(); remote.setName("directory"); remote.setBaseUrl("http://user@evil.example/mcp"); remote.setTokenEnvName("DIRECTORY_TOKEN"); remote.setAllowedTools(List.of("findDepartment")); properties.setRemoteServers(List.of(remote));
 assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("baseUrl");
 }

 @Test void exposesOnlyTheValidatedRemoteConfigurationFieldsToTheClientFactory() {
  McpProperties.RemoteServer remote=new McpProperties.RemoteServer();
  remote.setName("directory"); remote.setBaseUrl("https://directory.example/mcp"); remote.setTokenEnvName("DIRECTORY_TOKEN"); remote.setAllowedTools(List.of("findDepartment"));

  assertThat(remote.getName()).isEqualTo("directory");
  assertThat(remote.getBaseUrl()).isEqualTo("https://directory.example/mcp");
  assertThat(remote.getTokenEnvName()).isEqualTo("DIRECTORY_TOKEN");
  assertThat(remote.getAllowedTools()).containsExactly("findDepartment");
 }

 @Test void rejectsRemoteHostsOutsideTheExplicitAllowList() {
  McpProperties properties=new McpProperties();
  properties.setEnabled(true); properties.setServerToken("token"); properties.setAllowedOrigins(List.of("https://medora.example"));
  properties.setAllowedRemoteHosts(List.of("directory.example"));
  McpProperties.RemoteServer remote=new McpProperties.RemoteServer(); remote.setName("directory"); remote.setBaseUrl("https://other.example/mcp"); remote.setTokenEnvName("DIRECTORY_TOKEN"); remote.setAllowedTools(List.of("findDepartment")); properties.setRemoteServers(List.of(remote));
  assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("allow-list");
 }
}
