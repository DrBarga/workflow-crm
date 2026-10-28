"use client";

import { usePasswordReset } from "../model/usePasswordReset";
import { NewPasswordForm } from "./NewPasswordForm";
import { PasswordChanged } from "./PasswordChanged";
import { ResetLinkExpired } from "./ResetLinkExpired";

// Сценарий смены пароля по ссылке из письма: токен приходит в адресе.
export function PasswordReset() {
  const { state, error, submit } = usePasswordReset();

  if (state === "checking") return null;
  if (state === "expired") return <ResetLinkExpired />;
  if (state === "done") return <PasswordChanged />;

  return (
    <NewPasswordForm
      loading={state === "saving"}
      error={error}
      onSubmit={(password) => void submit(password)}
    />
  );
}
