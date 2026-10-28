import Link from "next/link";
import { StepScreen, SubmitButton } from "@shared/ui";
import { SpinnerIcon } from "@shared/ui/common/Icons";
import block from "@shared/ui/common/block.module.css";
import step from "@shared/ui/common/step.module.css";

export function InvitationChecking() {
  return (
    <p className={step.stepText}>
      <SpinnerIcon className={block.spinner} /> Перевіряємо посилання…
    </p>
  );
}

// Просроченную ссылку пользователь сам не обновит: письмо повторно отправляет
// администратор, поэтому кнопки «запросить ещё раз» здесь нет.
export function InvitationInvalid({ reason }: { reason: "invalid" | "failed" }) {
  const expired = reason === "invalid";

  return (
    <StepScreen
      title={expired ? "Посилання втратило чинність" : "Не вдалося перевірити посилання"}
      description={
        expired
          ? "Лист-запрошення діє 72 години й одноразове. Зверніться до адміністратора системи — він надішле його повторно."
          : "Спробуйте відкрити посилання з листа ще раз. Якщо помилка повторюється, зверніться до адміністратора системи."
      }
    >
      <Link href="/login">
        <SubmitButton type="button">До входу</SubmitButton>
      </Link>
    </StepScreen>
  );
}

export function InvitationDone() {
  return (
    <StepScreen
      title="Пароль створено"
      description="Пристрій привʼязано до облікового запису — наступного разу вхід без підтвердження."
    >
      <Link href="/login">
        <SubmitButton type="button">Увійти</SubmitButton>
      </Link>
    </StepScreen>
  );
}
