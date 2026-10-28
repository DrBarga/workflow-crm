import { SetPasswordForm } from "@features/auth/set-password";
import { AuthLayout } from "@widgets/auth/auth-layout";

export function SetPasswordPage() {
  return (
    <AuthLayout>
      <SetPasswordForm />
    </AuthLayout>
  );
}
