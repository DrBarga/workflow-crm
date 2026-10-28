import { PasswordResetRequest } from "@features/auth/password-reset-request";
import { AuthLayout } from "@widgets/auth/auth-layout";

export function ForgotPasswordPage() {
  return (
    <AuthLayout>
      <PasswordResetRequest />
    </AuthLayout>
  );
}
