import type { A2uiMessage, A2uiSurface } from "@/lib/a2ui/types";

export function reduceA2uiSurface(state: Record<string, A2uiSurface>, message: A2uiMessage): Record<string, A2uiSurface> {
  if (message.event === "deleteSurface") {
    const { [message.surfaceId]: _, ...rest } = state;
    return rest;
  }
  const prior = state[message.surfaceId];
  if (message.event === "updateComponents" && prior) {
    return { ...state, [message.surfaceId]: { ...prior, components: message.components } };
  }
  if (message.event === "updateDataModel" && prior) {
    return { ...state, [message.surfaceId]: { ...prior, dataModel: { ...prior.dataModel, ...message.dataModel } } };
  }
  return { ...state, [message.surfaceId]: { surfaceId: message.surfaceId, rootId: message.rootId, components: message.components, dataModel: message.dataModel } };
}
