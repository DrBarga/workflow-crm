"use client";

import { useEffect, useState } from "react";

/**
 * Обратный отсчёт в секундах. Возвращает остаток и его запись «мм:сс».
 * Смена начального значения перезапускает отсчёт: сравнение в теле хука —
 * тот самый приём из документации React, что и сброс состояния по пропу.
 */
export function useCountdown(seconds: number) {
  const [startedFrom, setStartedFrom] = useState(seconds);
  const [left, setLeft] = useState(seconds);

  if (startedFrom !== seconds) {
    setStartedFrom(seconds);
    setLeft(seconds);
  }

  useEffect(() => {
    if (seconds <= 0) return;

    const timer = setInterval(() => setLeft((value) => (value <= 1 ? 0 : value - 1)), 1000);
    return () => clearInterval(timer);
  }, [seconds]);

  return { left, formatted: formatDuration(left) };
}

export function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;

  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}
