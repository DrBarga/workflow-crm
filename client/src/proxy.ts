import { NextResponse, type NextRequest } from "next/server";
import { env } from "@shared/config";
import { safeNextPath } from "@shared/lib";

/*
 * Единственный файл вне слоёв FSD: Next.js ищет его строго рядом с app,
 * то есть в src/proxy.ts (бывший middleware, переименован в Next 16).
 * Исключение отражено в eslint.config.mjs и scripts/check-fsd.mjs,
 * логика держится тонкой.
 *
 * Проверка оптимистичная — наличие cookie сессии. Валидность токена
 * решает backend: он отвечает 401, и клиент уводит на /login.
 */
const PUBLIC_ROUTES = ["/login", "/forgot-password", "/reset-password", "/set-password"];

/*
 * Страницы входа и восстановления — вход в систему, и вошедшему там делать
 * нечего: уводим его обратно в приложение.
 *
 * Установка пароля по ссылке из письма сюда не входит: ссылку могут открыть
 * при живой сессии (своей или чужой), и токен из письма важнее.
 */
const ENTRY_ROUTES = ["/login", "/forgot-password"];

function matches(pathname: string, routes: string[]): boolean {
  return routes.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const authenticated = request.cookies.has(env.sessionCookieName);

  if (authenticated && matches(pathname, ENTRY_ROUTES)) {
    const next = safeNextPath(request.nextUrl.searchParams.get("next"));
    return NextResponse.redirect(new URL(next, request.url));
  }

  if (matches(pathname, PUBLIC_ROUTES)) {
    return NextResponse.next();
  }

  if (authenticated) {
    return NextResponse.next();
  }

  const loginUrl = new URL("/login", request.url);
  // Куда вернуть после входа. Только путь: внешний адрес был бы open redirect.
  loginUrl.searchParams.set("next", pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  // Статика, картинки и favicon мимо proxy: они не защищаются.
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\.png$|.*\\.svg$).*)"],
};
