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
        this.descriptors = Map.copyOf(copy);
    }
    public Optional<ToolDescriptor> find(String qualifiedName) { return Optional.ofNullable(descriptors.get(qualifiedName)); }
    public Collection<ToolDescriptor> all() { return descriptors.values(); }
}
