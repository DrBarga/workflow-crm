"use client";

import { useResetRequest } from "../model/useResetRequest";
import { maskEmail } from "../lib/maskEmail";
import { ForgotPasswordForm } from "./ForgotPasswordForm";
import { ResetLinkSent } from "./ResetLinkSent";

// Сценарий целиком: форма → экран «письмо отправлено» → возврат к форме.
export function PasswordResetRequest() {
  const { sentTo, sending, error, resendIn, submit, changeEmail } = useResetRequest();

  if (sentTo) {
    return (
      <ResetLinkSent
        maskedEmail={maskEmail(sentTo)}
        resendIn={resendIn}
        onResend={() => void submit(sentTo)}
        onChangeEmail={changeEmail}
      />
    );
  }

  return (
    <ForgotPasswordForm loading={sending} error={error} onSubmit={(email) => void submit(email)} />
  );
}
