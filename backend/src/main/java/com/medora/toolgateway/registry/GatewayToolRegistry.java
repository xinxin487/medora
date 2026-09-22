package com.medora.toolgateway.registry;
import com.medora.toolgateway.descriptor.ToolDescriptor;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class GatewayToolRegistry {
    private final Map<String, ToolDescriptor> descriptors;
    public GatewayToolRegistry(List<ToolDescriptor> descriptors) {
        Map<String, ToolDescriptor> copy = new LinkedHashMap<>();
        for (ToolDescriptor descriptor : descriptors) { if (copy.putIfAbsent(descriptor.qualifiedName(), descriptor) != null) throw new IllegalStateException("duplicate gateway tool: " + descriptor.qualifiedName()); }
        this.descriptors = new java.util.concurrent.ConcurrentHashMap<>(copy);
    }
    public Optional<ToolDescriptor> find(String qualifiedName) { return Optional.ofNullable(descriptors.get(qualifiedName)); }
    public Collection<ToolDescriptor> all() { return List.copyOf(descriptors.values()); }
    /** Remote descriptors are namespaced and can never replace a local descriptor. */
    public void registerRemote(ToolDescriptor descriptor) {
        if (!descriptor.name().startsWith("mcp.")) throw new IllegalArgumentException("remote tools must use mcp namespace");
        if (descriptor.sourceType() != com.medora.toolgateway.descriptor.ToolSourceType.MCP) throw new IllegalArgumentException("remote tools must have MCP source");
        ToolDescriptor existing = descriptors.putIfAbsent(descriptor.qualifiedName(), descriptor);
        if (existing != null && existing.sourceType() != com.medora.toolgateway.descriptor.ToolSourceType.MCP) {
            throw new IllegalStateException("remote tool cannot overwrite local descriptor");
        }
    }
}
