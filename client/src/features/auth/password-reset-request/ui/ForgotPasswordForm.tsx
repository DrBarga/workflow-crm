"use client";

import { useState, type FormEvent } from "react";
import { BackLink, FormAlert, FormHead, SecureNote, SubmitButton, TextField } from "@shared/ui";

interface ForgotPasswordFormProps {
  onSubmit?: (email: string) => void;
  /** Ошибка от backend: почта с ошибкой, недоступная отправка писем. */
  error?: string;
  loading?: boolean;
}

export function ForgotPasswordForm({ onSubmit, error, loading = false }: ForgotPasswordFormProps) {
  const [email, setEmail] = useState("");
  const [validationError, setValidationError] = useState<string>();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!email.trim() || !email.includes("@")) {
      setValidationError("Перевірте робочу пошту.");
      return;
    }
    setValidationError(undefined);
    onSubmit?.(email.trim());
  }

  return (
    <div>
      <BackLink href="/login">До входу</BackLink>
      <FormHead
        title="Відновлення пароля"
        description="Вкажіть робочу пошту — надішлемо посилання для встановлення нового пароля."
      />
      <form noValidate onSubmit={handleSubmit}>
        <TextField
          id="recovery-email"
          label="Робоча пошта"
          type="email"
          autoComplete="username"
          placeholder="name@example.com"
          value={email}
          disabled={loading}
          invalid={Boolean(validationError ?? error)}
          onChange={(event) => setEmail(event.target.value)}
        />
        <SubmitButton loading={loading} loadingLabel="Надсилаємо…">
          Надіслати посилання
        </SubmitButton>
        {(validationError ?? error) ? <FormAlert>{validationError ?? error}</FormAlert> : null}
      </form>
      <SecureNote withIcon={false}>
        З міркувань безпеки система не повідомляє, чи існує обліковий запис із вказаною адресою.
      </SecureNote>
    </div>
  );
}
