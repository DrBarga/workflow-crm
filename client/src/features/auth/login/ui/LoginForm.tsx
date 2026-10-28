"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import {
  FormAlert,
  FormHead,
  PasswordField,
  SecureNote,
  SubmitButton,
  TextField,
} from "@shared/ui";
import styles from "@shared/ui/common/form.module.css";

export interface LoginCredentials {
  email: string;
  password: string;
}

interface LoginFormProps {
  onSubmit?: (credentials: LoginCredentials) => void;
  /** Ошибка от backend: неверная пара, отключённый аккаунт. */
  error?: string;
  loading?: boolean;
}

export function LoginForm({ onSubmit, error, loading = false }: LoginFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [validationError, setValidationError] = useState<string>();

  // Ошибки формы не называют поле: по ним нельзя понять, что именно неверно —
  // пошта, пароль или и то и другое. То же правило и у backend: на неверную
  // пару он отвечает одним сообщением.
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!email.trim() || !password) {
      setValidationError("Заповніть пошту й пароль.");
      return;
    }
    if (!email.includes("@")) {
      setValidationError("Перевірте пошту й пароль.");
      return;
    }
    setValidationError(undefined);
    onSubmit?.({ email: email.trim(), password });
  }

  const shownError = validationError ?? error;

  return (
    <div>
      <FormHead title="Вхід у систему" description="Використовуйте робочу пошту WORKFLOW" />
      <form noValidate onSubmit={handleSubmit}>
        <TextField
          id="email"
          label="Робоча пошта"
          type="email"
          autoComplete="username"
          placeholder="name@example.com"
          value={email}
          disabled={loading}
          invalid={Boolean(shownError)}
          onChange={(event) => setEmail(event.target.value)}
        />
        <PasswordField
          id="password"
          label="Пароль"
          autoComplete="current-password"
          placeholder="••••••••"
          value={password}
          disabled={loading}
          invalid={Boolean(shownError)}
          onChange={(event) => setPassword(event.target.value)}
          hint={
            <Link className={styles.hint} href="/forgot-password">
              Забули пароль?
            </Link>
          }
        />
        <SubmitButton loading={loading} loadingLabel="Перевіряємо…">
          Увійти
        </SubmitButton>
        {shownError ? <FormAlert>{shownError}</FormAlert> : null}
      </form>
      <SecureNote>
        Пристрій прив&apos;язується під час першого входу за посиланням з листа — далі з нього вхід
        без підтвердження. З іншого пристрою код прийде на пошту або в Telegram, якщо ви привʼязали
        бота в профілі.
      </SecureNote>
    </div>
  );
}
