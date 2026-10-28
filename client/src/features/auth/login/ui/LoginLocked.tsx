import { InfoPanel, StepScreen, SubmitButton } from "@shared/ui";

interface LoginLockedProps {
  email: string;
  /**
   * Остаток блокировки, «15:00». Backend его не возвращает (429 приходит без
   * тела с таймером), поэтому обычно не задан — тогда показываем текст без
   * обратного отсчёта.
   */
  unlocksIn?: string;
  onRetry?: () => void;
}

// Несколько неверных попыток подряд — вход заморожен. Длительность задаёт
// LOGIN_LOCK_DURATION на backend.
export function LoginLocked({ email, unlocksIn, onRetry }: LoginLockedProps) {
  return (
    <StepScreen
      title="Вхід тимчасово заблоковано"
      description={
        <>
          Пароль кілька разів поспіль введено неправильно, тому вхід для <b>{email}</b> призупинено.
          Це захист від підбору пароля.
        </>
      }
    >
      {unlocksIn ? (
        <InfoPanel variant="wait" countdown={unlocksIn}>
          Спробувати знову можна через
        </InfoPanel>
      ) : (
        <InfoPanel variant="warn">
          Зачекайте кілька хвилин і спробуйте ще раз. Якщо не памʼятаєте пароль — скиньте його через
          «Забули пароль?».
        </InfoPanel>
      )}
      {onRetry ? (
        <SubmitButton type="button" onClick={onRetry}>
          Повернутися до форми
        </SubmitButton>
      ) : null}
    </StepScreen>
  );
}
