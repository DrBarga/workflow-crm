import { Login } from "@features/auth/login";
import { AuthLayout } from "@widgets/auth/auth-layout";

export function LoginPage() {
  return (
    <AuthLayout>
      <Login />
    </AuthLayout>
  );
}
