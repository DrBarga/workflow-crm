// Next.js inlines NEXT_PUBLIC_* vars into the browser bundle only when
// referenced with this exact static `process.env.NEXT_PUBLIC_X` syntax —
// a computed lookup like `process.env[name]` would stay undefined client-side.
const NEXT_PUBLIC_API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL;

// Имя cookie сессии задаёт backend (JWT_COOKIE_NAME). Middleware по ней
// решает, пускать ли на страницу, поэтому значения должны совпадать.
const NEXT_PUBLIC_SESSION_COOKIE_NAME = process.env.NEXT_PUBLIC_SESSION_COOKIE_NAME;

function assertRequired(value: string | undefined, name: string): string {
  if (!value || value.trim() === "") {
    throw new Error(
      `Missing required environment variable: ${name}. Add it to .env.local (see .env.example) before starting the app.`,
    );
  }

  return value;
}

export const env = {
  apiBaseUrl: assertRequired(NEXT_PUBLIC_API_BASE_URL, "NEXT_PUBLIC_API_BASE_URL"),
  sessionCookieName: NEXT_PUBLIC_SESSION_COOKIE_NAME || "workflow_access_token",
} as const;
