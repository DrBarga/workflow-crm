import { InfoPanel, StepScreen, SubmitButton } from "@shared/ui";

interface AccessDeniedProps {
  email: string;
  onRetry?: () => void;
}

/*
 * Вход запрещён: устройство заблокировано после «Це не я» в Telegram либо
 * аккаунт отключён администратором. Backend отвечает на оба случая 403 с кодом
 * ACCESS_DENIED и не различает их наружу — намеренно, чтобы не подсказывать
 * подбирающему, в чём дело. Поэтому экран один и формулировка общая.
 */
export function AccessDenied({ email, onRetry }: AccessDeniedProps) {
  return (
    <StepScreen
      title="Вхід заборонено"
      description={
        <>
          Увійти під <b>{email}</b> із цього пристрою не вдалося.
        </>
      }
    >
      <InfoPanel variant="bad">
        Обліковий запис вимкнено або пристрій заблоковано після відхилення входу. Зверніться до
        адміністратора системи — самостійно це не знімається.
      </InfoPanel>
      {onRetry ? (
        <SubmitButton type="button" onClick={onRetry}>
          Повернутися до форми
        </SubmitButton>
      ) : null}
    </StepScreen>
  );
}
