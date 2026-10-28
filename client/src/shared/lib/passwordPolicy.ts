// Требования к паролю из макета. Проверка на клиенте нужна только для
// подсказки в форме: источник истины — backend (validation-rules-v1.0.md).
export interface PasswordRule {
  id: string;
  label: string;
  isMet: (password: string) => boolean;
}

export const PASSWORD_RULES: readonly PasswordRule[] = [
  { id: "length", label: "Не менше 12 символів", isMet: (value) => value.length >= 12 },
  { id: "upper", label: "Велика латинська літера (A–Z)", isMet: (value) => /[A-Z]/.test(value) },
  { id: "lower", label: "Мала латинська літера (a–z)", isMet: (value) => /[a-z]/.test(value) },
  { id: "digit", label: "Цифра (0–9)", isMet: (value) => /[0-9]/.test(value) },
  {
    id: "special",
    label: "Спеціальний символ (!@#$%^&*()_+-=)",
    isMet: (value) => /[!@#$%^&*()_+\-=]/.test(value),
  },
];

export function isPasswordStrong(password: string): boolean {
  return PASSWORD_RULES.every((rule) => rule.isMet(password));
}
