"use client";

import Link from "next/link";
import { InfoPanel, StepScreen, SubmitButton } from "@shared/ui";
import { SpinnerIcon } from "@shared/ui/common/Icons";
import block from "@shared/ui/common/block.module.css";
import styles from "./common/confirmation.module.css";

interface ConfirmationResultProps {
  status: "approved" | "rejected" | "expired";
  email: string;
  onRetry?: () => void;
}

// Итог подтверждения устройства. Каждое состояние — отдельный экран целиком,
// без наложения частичных правил.
export function ConfirmationResult({ status, email, onRetry }: ConfirmationResultProps) {
  if (status === "approved") {
    return (
      <StepScreen
        title="Вхід підтверджено"
        description="Пристрій привʼязано до облікового запису — наступного разу підтвердження не знадобиться."
      >
        <div className={styles.wait}>
          <SpinnerIcon className={block.spinner} />
          <span>Відкриваємо систему…</span>
        </div>
      </StepScreen>
    );
  }

  if (status === "expired") {
    return (
      <StepScreen
        title="Запит втратив чинність"
        description="Підтвердження діє 5 хвилин. Надішліть новий запит, щоб продовжити вхід."
      >
        <SubmitButton type="button" onClick={onRetry}>
          Надіслати новий запит
        </SubmitButton>
      </StepScreen>
    );
  }

  return (
    <StepScreen
      title="Вхід відхилено"
      description={
        <>
          У Telegram натиснуто «Це не я». Вхід для <b>{email}</b> не виконано.
        </>
      }
    >
      <InfoPanel variant="bad">
        Якщо запит надсилали не ви — хтось знає ваш пароль. Змініть його зараз і повідомте
        адміністратора.
      </InfoPanel>
      <Link href="/forgot-password">
        <SubmitButton type="button">Змінити пароль</SubmitButton>
      </Link>
      <button type="button" className={styles.secondary} onClick={onRetry}>
        Це був я — надіслати запит ще раз
      </button>
    </StepScreen>
  );
}
