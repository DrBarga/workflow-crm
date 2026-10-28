import { useDispatch, useSelector } from "react-redux";
import type { ThunkDispatch, UnknownAction } from "@reduxjs/toolkit";

/*
 * Типизированные обёртки над react-redux. Сам стор живёт в app/_providers:
 * он знает про редьюсеры сущностей, и в shared ему не место.
 *
 * Здесь остаётся только типизация dispatch по форме thunk-middleware — она
 * не зависит от набора редьюсеров, поэтому shared не начинает знать о
 * сущностях и слои остаются на месте.
 */
export type AppDispatch = ThunkDispatch<unknown, unknown, UnknownAction>;

export function useAppDispatch(): AppDispatch {
  return useDispatch();
}

export const useAppSelector = useSelector;
