import Link from "next/link";
import { StepScreen, SubmitButton } from "@shared/ui";

export function PasswordChanged() {
  return (
    <StepScreen
      title="Пароль змінено"
      description="Усі активні сеанси на інших пристроях завершено. Увійдіть з новим паролем."
    >
      <Link href="/login">
        <SubmitButton type="button">Увійти</SubmitButton>
      </Link>
    </StepScreen>
  );
}
