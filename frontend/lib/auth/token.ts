export const ACCESS_TOKEN_KEY = "medora.access_token";
export const REFRESH_TOKEN_KEY = "medora.refresh_token";

export function getAccessToken() {
  if (typeof window === "undefined") {
    return null;
  }
  return window.localStorage.getItem(ACCESS_TOKEN_KEY);
}
