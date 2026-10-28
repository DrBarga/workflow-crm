"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError } from "@shared/api";
import { takeTokenFromUrl } from "@shared/lib";
import { acceptPasswordInvitation, openPasswordInvitation } from "../api/invitationApi";
import { passwordValidationError } from "./passwordValidation";

type InvitationState = "checking" | "ready" | "saving" | "done" | "invalid" | "failed";

export function usePasswordInvitation() {
  const [state, setState] = useState<InvitationState>("checking");
  const [error, setError] = useState("");
  const token = useRef("");
  const opening = useRef<Promise<unknown> | null>(null);
  const submitting = useRef(false);

  useEffect(() => {
    if (!opening.current) {
      token.current = takeTokenFromUrl();
      if (token.current) opening.current = openPasswordInvitation(token.current);
    }

    if (!opening.current) {
      setState("invalid");
      return;
    }

    let active = true;
    opening.current.then(
      () => active && setState("ready"),
      (reason: unknown) => {
        if (active) {
          setState(reason instanceof ApiError && reason.status === 410 ? "invalid" : "failed");
        }
      },
    );
    return () => {
      active = false;
    };
  }, []);

  async function submit(password: string) {
    if (state !== "ready" || !token.current || submitting.current) return;

    submitting.current = true;
    setError("");
    setState("saving");
    try {
      await acceptPasswordInvitation(token.current, password);
      token.current = "";
      setState("done");
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 410) {
        setState("invalid");
      } else {
        setError(
          reason instanceof ApiError && reason.status === 400
            ? passwordValidationError(reason)
            : "Не удалось сохранить пароль. Попробуйте ещё раз.",
        );
        setState("ready");
        submitting.current = false;
      }
    }
  }

  return { state, error, submit };
}
