"use client";

import { useState, type FormEvent } from "react";
import { isPasswordStrong } from "@shared/lib";
import { FormAlert, FormHead, PasswordSetupFields, SecureNote, SubmitButton } from "@shared/ui";

interface NewPasswordFormProps {
  onSubmit?: (password: string) => void;
  /** Ошибка от backend, например пароль не прошёл проверку. */
  error?: string;
  loading?: boolean;
}

export function NewPasswordForm({ onSubmit, error, loading = false }: NewPasswordFormProps) {
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [mismatch, setMismatch] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const matches = password === confirmation;
    setMismatch(!matches);
    if (matches && isPasswordStrong(password)) onSubmit?.(password);
  }

  return (
    <div>
      <FormHead title="Новий пароль" description="Придумайте пароль." />
      <form noValidate onSubmit={handleSubmit}>
        <PasswordSetupFields
          idPrefix="reset"
          password={password}
          confirmation={confirmation}
          onPasswordChange={setPassword}
          onConfirmationChange={setConfirmation}
          confirmationError={mismatch ? "Паролі не збігаються" : undefined}
          disabled={loading}
        />
        <SubmitButton
          loading={loading}
          loadingLabel="Зберігаємо…"
          disabled={!isPasswordStrong(password)}
        >
          Зберегти пароль
        </SubmitButton>
        {error ? <FormAlert>{error}</FormAlert> : null}
      </form>
      <SecureNote withIcon={false}>
        Після зміни пароля всі активні сеанси буде завершено.
      </SecureNote>
    </div>
  );
}
