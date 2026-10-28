"use client";

import { useState, type FormEvent } from "react";
import { isPasswordStrong } from "@shared/lib";
import { FormAlert, FormHead, PasswordSetupFields, SecureNote, SubmitButton } from "@shared/ui";
import { usePasswordInvitation } from "../model/usePasswordInvitation";
import { InvitationChecking, InvitationDone, InvitationInvalid } from "./InvitationStates";

export function SetPasswordForm() {
  const { state, error, submit } = usePasswordInvitation();
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [mismatch, setMismatch] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const matches = password === confirmation;
    setMismatch(!matches);
    if (matches && isPasswordStrong(password)) void submit(password);
  }

  if (state === "checking") return <InvitationChecking />;
  if (state === "invalid" || state === "failed") return <InvitationInvalid reason={state} />;
  if (state === "done") return <InvitationDone />;

  const saving = state === "saving";

  return (
    <div>
      <FormHead
        title="Створіть пароль"
        description="Вас додав адміністратор. Задайте пароль — і одразу потрапите в систему."
      />
      {error ? <FormAlert>{error}</FormAlert> : null}
      <form noValidate onSubmit={handleSubmit}>
        <PasswordSetupFields
          idPrefix="invitation"
          password={password}
          confirmation={confirmation}
          onPasswordChange={setPassword}
          onConfirmationChange={setConfirmation}
          confirmationError={mismatch ? "Паролі не збігаються" : undefined}
          disabled={saving}
        />
        <SubmitButton
          loading={saving}
          loadingLabel="Зберігаємо…"
          disabled={!isPasswordStrong(password)}
        >
          Зберегти пароль і увійти
        </SubmitButton>
      </form>
      <SecureNote>
        Посилання з листа діє 72 години й одноразове. Перехід за ним звʼязує обліковий запис із цим
        пристроєм — підтвердження не знадобиться.
      </SecureNote>
    </div>
  );
}
