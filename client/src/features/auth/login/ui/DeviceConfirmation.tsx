"use client";

import { useCountdown } from "@shared/lib";
import { FormAlert } from "@shared/ui";
import type { Challenge } from "../api/loginApi";
import { useChallengeFlow } from "../model/useChallengeFlow";
import { ConfirmationResult } from "./ConfirmationResult";
import { EmailCodeConfirmation } from "./EmailCodeConfirmation";
import { TelegramConfirmation } from "./TelegramConfirmation";

interface DeviceConfirmationProps {
  challenge: Challenge;
  email: string;
  onApproved: () => void;
  /** Возврат к форме: повторный вход создаёт новый challenge. */
  onRestart: () => void;
}

export function DeviceConfirmation({
  challenge,
  email,
  onApproved,
  onRestart,
}: DeviceConfirmationProps) {
  const { status, error, resendIn, submitCode, resend } = useChallengeFlow(challenge, onApproved);
  const { formatted, left } = useCountdown(challenge.expiresInSeconds);

  if (status === "REJECTED") {
    return <ConfirmationResult status="rejected" email={email} onRetry={onRestart} />;
  }

  if (status === "EXPIRED" || left === 0) {
    return <ConfirmationResult status="expired" email={email} onRetry={onRestart} />;
  }

  if (status === "APPROVED" || status === "CONSUMED") {
    return (
      <>
        {error ? <FormAlert>{error}</FormAlert> : null}
        <ConfirmationResult status="approved" email={email} />
      </>
    );
  }

  if (status === "PENDING_EMAIL" || status === "BINDING_REQUIRED") {
    return (
      <EmailCodeConfirmation
        email={email}
        expiresIn={formatted}
        error={error}
        onSubmit={submitCode}
      />
    );
  }

  return (
    <>
      {error ? <FormAlert>{error}</FormAlert> : null}
      <TelegramConfirmation
        email={email}
        expiresIn={formatted}
        resendIn={resendIn}
        onResend={resend}
      />
    </>
  );
}
