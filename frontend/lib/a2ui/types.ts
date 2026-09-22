export type A2uiComponentType = "Text" | "Card" | "Column" | "Row" | "Button" | "Badge" | "Divider";

export type A2uiComponent = { id: string; type: A2uiComponentType; props: Record<string, string> };

export type A2uiMessage = {
  event: "createSurface" | "updateComponents" | "updateDataModel" | "deleteSurface";
  surfaceId: string;
  rootId: string;
  components: A2uiComponent[];
  dataModel: Record<string, string>;
};

export type A2uiSurface = Omit<A2uiMessage, "event">;
