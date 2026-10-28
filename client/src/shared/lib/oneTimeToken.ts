/**
 * Забирает одноразовый токен из адреса и сразу убирает его из строки браузера.
 * Ссылку из письма нельзя оставлять в адресной строке: она попадёт в историю,
 * в закладки и в заголовок Referer при первом же переходе наружу.
 */
export function takeTokenFromUrl(parameter = "token"): string {
  if (typeof window === "undefined") return "";

  const url = new URL(window.location.href);
  const token = url.searchParams.get(parameter) ?? "";
  if (!token) return "";

  url.searchParams.delete(parameter);
  window.history.replaceState(window.history.state, "", url.pathname + url.search + url.hash);

  return token;
}
