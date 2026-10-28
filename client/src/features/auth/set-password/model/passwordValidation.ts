import { ApiError } from "@shared/api";

const messages: Record<string, string> = {
  "Password must contain 12 to 128 characters": "Пароль должен содержать от 12 до 128 символов.",
  "Password must not exceed 72 UTF-8 bytes": "Пароль слишком длинный: допустимо до 72 байт UTF-8.",
  "Password must include an uppercase Latin letter":
    "Добавьте латинскую букву в верхнем регистре (A-Z).",
  "Password must include a lowercase Latin letter":
    "Добавьте латинскую букву в нижнем регистре (a-z).",
  "Password must include a digit": "Добавьте цифру (0-9).",
  "Password must include a special character from !@#$%^&*()_+-=":
    "Добавьте спецсимвол из !@#$%^&*()_+-=.",
  "Password must not contain common passwords":
    "Не используйте стандартный пароль или его варианты.",
  "Password must not contain Workflow": "Пароль не должен содержать Workflow или его варианты.",
  "Password must not contain your email or name":
    "Пароль не должен содержать ваш email, имя или фамилию.",
};

export function validatePassword(password: string): string {
  if (password.length < 12 || password.length > 128)
    return messages["Password must contain 12 to 128 characters"];
  if (new TextEncoder().encode(password).length > 72)
    return messages["Password must not exceed 72 UTF-8 bytes"];
  if (!/[A-Z]/.test(password)) return messages["Password must include an uppercase Latin letter"];
  if (!/[a-z]/.test(password)) return messages["Password must include a lowercase Latin letter"];
  if (!/[0-9]/.test(password)) return messages["Password must include a digit"];
  if (!/[!@#$%^&*()_+\-=]/.test(password))
    return messages["Password must include a special character from !@#$%^&*()_+-="];
  return "";
}

export function passwordValidationError(error: ApiError): string {
  const violation = error.violations.find((item) => item.field === "password");
  return (
    (violation && messages[violation.message]) ||
    "Пароль не соответствует требованиям. Проверьте правила под полем ввода."
  );
}
