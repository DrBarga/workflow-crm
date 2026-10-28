"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  completeChallenge,
  confirmEmailCode,
  fetchChallengeStatus,
  resendChallenge,
  type Challenge,
  type ChallengeStatus,
} from "../api/loginApi";
import { ApiError } from "@shared/api";
import { challengeErrorMessage } from "../lib/loginErrors";

const POLL_INTERVAL_MS = 3000;
// Повторная отправка не чаще раза в 10 секунд.
const RESEND_COOLDOWN_SECONDS = 10;

/** Ожидание подтверждения устройства: опрос статуса, код с почты, завершение. */
export function useChallengeFlow(challenge: Challenge, onApproved: () => void) {
  const [status, setStatus] = useState<ChallengeStatus>(challenge.status);
  const [error, setError] = useState<string>();
  const [resendIn, setResendIn] = useState(0);
  const [verifying, setVerifying] = useState(false);
  const completing = useRef(false);

  // Бот отвечает на стороне Telegram, поэтому статус спрашиваем сами.
  useEffect(() => {
    if (status !== "PENDING_TELEGRAM") return;

    const timer = setInterval(async () => {
      try {
        const next = await fetchChallengeStatus(challenge.challengeId);
        setStatus(next.status);
      } catch {
        // Сеть моргнула — следующий тик повторит запрос.
      }
    }, POLL_INTERVAL_MS);

    return () => clearInterval(timer);
  }, [status, challenge.challengeId]);

  // Подтверждено — забираем cookie сессии. Ровно один раз: complete
  // «съедает» challenge, повторный вызов вернёт 409.
  useEffect(() => {
    if (status !== "APPROVED" || completing.current) return;

    completing.current = true;
    // try/catch, а не .then(onApproved, ...): во втором случае исключение
    // внутри onApproved стало бы необработанным промисом — без редиректа
    // и без сообщения на экране.
    void (async () => {
      try {
        await completeChallenge(challenge.challengeId);
        onApproved();
      } catch (reason) {
        completing.current = false;
        setError(challengeErrorMessage(reason));
      }
    })();
  }, [status, challenge.challengeId, onApproved]);

  useEffect(() => {
    if (resendIn <= 0) return;

    const timer = setTimeout(() => setResendIn((value) => value - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  const submitCode = useCallback(
    async (code: string) => {
      setVerifying(true);
      setError(undefined);
      try {
        const result = await confirmEmailCode(challenge.challengeId, code);
        setStatus(result.status);
      } catch (reason) {
        setError(challengeErrorMessage(reason));
      } finally {
        setVerifying(false);
      }
    },
    [challenge.challengeId],
  );

  const resend = useCallback(async () => {
    setError(undefined);
    try {
      await resendChallenge(challenge.challengeId);
      setResendIn(RESEND_COOLDOWN_SECONDS);
    } catch (reason) {
      // 429 — backend сам ограничил частоту: включаем паузу и здесь.
      if (reason instanceof ApiError && reason.status === 429) setResendIn(RESEND_COOLDOWN_SECONDS);
      setError(challengeErrorMessage(reason));
    }
  }, [challenge.challengeId]);

  return { status, error, resendIn, verifying, submitCode, resend };
}
