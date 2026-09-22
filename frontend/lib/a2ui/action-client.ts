import { apiFetch } from "@/lib/api/client";

export type A2uiActionResult = { actionId: string; status: string; appointmentId: string };

export async function submitA2uiAction(input: {
  surfaceId: string; actionId: string; confirmationToken: string; idempotencyKey: string;
}): Promise<A2uiActionResult> {
  return apiFetch<A2uiActionResult>("/a2ui/actions", {
    method: "POST",
    headers: { "Idempotency-Key": input.idempotencyKey },
    body: JSON.stringify({ surfaceId: input.surfaceId, actionId: input.actionId, confirmationToken: input.confirmationToken }),
  });
}
