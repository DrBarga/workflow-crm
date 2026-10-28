// Маска почты для экрана «Перевірте пошту»: o.kravchenko@example.com → o.kr•••••••@example.com.
// Показывать адрес целиком нельзя: страница открыта без авторизации.
const VISIBLE_CHARACTERS = 4;

export function maskEmail(email: string): string {
  const [name, domain] = email.split("@");
  if (!domain) return email;

  const visible = name.slice(0, VISIBLE_CHARACTERS);
  const hidden = "•".repeat(Math.max(name.length - VISIBLE_CHARACTERS, 3));
  return `${visible}${hidden}@${domain}`;
}
