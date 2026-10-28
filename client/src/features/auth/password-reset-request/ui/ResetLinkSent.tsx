import Link from "next/link";
import { SecureNote, StepBox, StepLinks, StepScreen } from "@shared/ui";
import styles from "@shared/ui/common/step.module.css";

interface ResetLinkSentProps {
  /** Замаскированная почта, «o.kr•••••••@example.com». Маску строит backend. */
  maskedEmail: string;
  /** Пауза до повторной отправки в секундах. */
  resendIn?: number;
  linkTtlMinutes?: number;
  onResend?: () => void;
  onChangeEmail?: () => void;
}

export function ResetLinkSent({
  maskedEmail,
  resendIn = 0,
  linkTtlMinutes = 30,
  onResend,
  onChangeEmail,
}: ResetLinkSentProps) {
  return (
    <StepScreen
      title="Перевірте пошту"
      description={
        <>
          Якщо обліковий запис <b>{maskedEmail}</b> існує, ми надіслали на нього посилання для зміни
          пароля. Воно дійсне {linkTtlMinutes} хвилин.
        </>
      }
    >
      {resendIn > 0 ? (
        <StepBox>
          <span className={styles.linkMuted}>Надіслати ще раз можна через {resendIn} с</span>
        </StepBox>
      ) : null}
      <StepLinks>
        {resendIn === 0 && onResend ? (
          <button type="button" className={styles.linkPrimary} onClick={onResend}>
            Надіслати ще раз
          </button>
        ) : null}
        <button type="button" className={styles.linkPrimary} onClick={onChangeEmail}>
          Вказати іншу пошту
        </button>
        <Link className={styles.linkMuted} href="/login">
          Повернутися до входу
        </Link>
      </StepLinks>
      <SecureNote>
        Немає доступу до робочої пошти? Зверніться до адміністратора системи — лише він може змінити
        адресу облікового запису.
      </SecureNote>
    </StepScreen>
  );
}
