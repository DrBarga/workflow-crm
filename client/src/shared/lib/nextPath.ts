const DEFAULT_PATH = "/";

/**
 * Куда вернуть после входа. Адрес приходит из query-параметра `next`,
 * поэтому наружу пропускаем только относительный путь: «//example.com»
 * браузер трактует как внешний адрес — это был бы open redirect.
 */
export function safeNextPath(value: string | null): string {
  if (!value || !value.startsWith("/") || value.startsWith("//")) return DEFAULT_PATH;

  return value;
}
