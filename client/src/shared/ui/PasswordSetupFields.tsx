import { PasswordField } from "./PasswordField";
import { PasswordRules } from "./PasswordRules";

interface PasswordSetupFieldsProps {
  password: string;
  confirmation: string;
  onPasswordChange: (value: string) => void;
  onConfirmationChange: (value: string) => void;
  /** Префикс id полей: на странице может быть только один такой блок. */
  idPrefix: string;
  passwordError?: string;
  confirmationError?: string;
  disabled?: boolean;
}

// Общий блок «новый пароль + повтор + чек-лист»: одинаков на экранах
// установки нового пароля и первого входа по приглашению.
export function PasswordSetupFields({
  password,
  confirmation,
  onPasswordChange,
  onConfirmationChange,
  idPrefix,
  passwordError,
  confirmationError,
  disabled = false,
}: PasswordSetupFieldsProps) {
  return (
    <>
      <PasswordField
        id={`${idPrefix}-password`}
        label="Новий пароль"
        autoComplete="new-password"
        placeholder="••••••••••"
        value={password}
        disabled={disabled}
        error={passwordError}
        onChange={(event) => onPasswordChange(event.target.value)}
      />
      <PasswordRules password={password} />
      <PasswordField
        id={`${idPrefix}-confirmation`}
        label="Повторіть пароль"
        autoComplete="new-password"
        placeholder="••••••••••"
        revealable={false}
        value={confirmation}
        disabled={disabled}
        error={confirmationError}
        onChange={(event) => onConfirmationChange(event.target.value)}
      />
    </>
  );
}
