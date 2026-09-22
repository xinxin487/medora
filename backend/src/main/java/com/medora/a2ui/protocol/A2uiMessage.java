package com.medora.a2ui.protocol;

import java.util.List;
import java.util.Map;

/** A constrained v0.9.1-compatible surface update sent through the chat stream. */
public record A2uiMessage(String event, String surfaceId, String rootId,
                          List<Map<String, Object>> components, Map<String, Object> dataModel) {
}
