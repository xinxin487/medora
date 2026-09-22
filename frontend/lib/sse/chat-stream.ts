import { getApiBaseUrl } from "@/lib/api/client";
import { getAccessToken } from "@/lib/auth/token";
import type { A2uiMessage } from "@/lib/a2ui/types";

export type ChatStreamEvent =
  | { event: "message_start"; data: { messageId: string; conversationId: string; traceId: string } }
  | { event: "message_delta"; data: { delta: string } }
  | { event: "message_end"; data: { messageId: string; status: string; finishReason: string } }
  | { event: "tool_status"; data: Record<string, unknown> }
  | { event: "citation"; data: Record<string, unknown> }
  | { event: "a2ui"; data: A2uiMessage }
  | { event: "error"; data: { code: string; message: string } };

type SendChatStreamOptions = {
  conversationId: string;
  message: string;
  clientMessageId: string;
  sceneType?: string;
  onEvent: (event: ChatStreamEvent) => void;
};

export async function sendChatMessageStream(options: SendChatStreamOptions) {
  const headers = new Headers();
  headers.set("Accept", "text/event-stream");
  headers.set("Content-Type", "application/json");

  const accessToken = getAccessToken();
  if (accessToken) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }

  const response = await fetch(
    `${getApiBaseUrl()}/chat/conversations/${options.conversationId}/stream`,
    {
      method: "POST",
      headers,
      body: JSON.stringify({
        message: options.message,
        clientMessageId: options.clientMessageId,
        context: {
          sceneType: options.sceneType ?? "GENERAL",
        },
      }),
    },
  );

  if (!response.ok || !response.body) {
    throw new Error("chat stream request failed");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split(/\r?\n\r?\n/);
    buffer = frames.pop() ?? "";

    for (const frame of frames) {
      const event = parseSseFrame(frame);
      if (event) {
        options.onEvent(event);
      }
    }
  }

  if (buffer.trim()) {
    const event = parseSseFrame(buffer);
    if (event) {
      options.onEvent(event);
    }
  }
}

function parseSseFrame(frame: string): ChatStreamEvent | null {
  let eventName = "message";
  const dataLines: string[] = [];

  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith("event:")) {
      eventName = line.slice("event:".length).trim();
    }
    if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trim());
    }
  }

  if (dataLines.length === 0) {
    return null;
  }

  return {
    event: eventName,
    data: JSON.parse(dataLines.join("\n")) as unknown,
  } as ChatStreamEvent;
}
