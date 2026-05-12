import { getAccessToken } from "@/lib/auth/token";
import type {
  ApiErrorDetail,
  ApiErrorResponse,
  ApiSuccessResponse,
} from "@/lib/types";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export function getApiBaseUrl() {
  return API_BASE_URL;
}

export class ApiClientError extends Error {
  code: string;
  details: ApiErrorDetail[];

  constructor(code: string, message: string, details: ApiErrorDetail[] = []) {
    super(message);
    this.code = code;
    this.details = details;
  }
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  options: { withAuth?: boolean } = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");

  const isFormData = typeof FormData !== "undefined" && init.body instanceof FormData;
  if (init.body && !isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (options.withAuth !== false) {
    const accessToken = getAccessToken();
    if (accessToken) {
      headers.set("Authorization", `Bearer ${accessToken}`);
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
  });

  const payload = (await response.json()) as
    | ApiSuccessResponse<T>
    | ApiErrorResponse;

  if (!response.ok || payload.code !== "SUCCESS") {
    const errorPayload = payload as ApiErrorResponse;
    throw new ApiClientError(
      errorPayload.code ?? "HTTP_ERROR",
      errorPayload.message ?? "request failed",
      errorPayload.details ?? [],
    );
  }

  return (payload as ApiSuccessResponse<T>).data;
}
