"use client";

import { useLoginFlow } from "../model/useLoginFlow";
import { AccessDenied } from "./AccessDenied";
import { DeviceConfirmation } from "./DeviceConfirmation";
import { LoginForm } from "./LoginForm";
import { LoginLocked } from "./LoginLocked";

// Сценарий входа целиком: форма → подтверждение устройства → редирект.
export function Login() {
  const { step, submitting, error, submit, finish, backToForm } = useLoginFlow();

  if (step.kind === "locked") {
    return <LoginLocked email={step.email} onRetry={backToForm} />;
  }

  if (step.kind === "denied") {
    return <AccessDenied email={step.email} onRetry={backToForm} />;
  }

  if (step.kind === "challenge") {
    return (
      <DeviceConfirmation
        challenge={step.challenge}
        email={step.email}
        onApproved={finish}
        onRestart={backToForm}
      />
    );
  }

  return (
    <LoginForm
      loading={submitting}
      error={error}
      onSubmit={({ email, password }) => void submit(email, password)}
    />
  );
}
