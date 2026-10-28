import { PasswordReset } from "@features/auth/password-reset";
import { AuthLayout } from "@widgets/auth/auth-layout";

export function ResetPasswordPage() {
  return (
    <AuthLayout>
      <PasswordReset />
    </AuthLayout>
  );
}
