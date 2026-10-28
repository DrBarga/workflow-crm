import Link from "next/link";
import { StepScreen, SubmitButton } from "@shared/ui";

// Один экран на все отказы токена: истёк, уже использован, подделан.
// Backend отвечает на них одинаково — 410 без подробностей.
export function ResetLinkExpired() {
  return (
    <StepScreen
      title="Посилання втратило чинність"
      description="Посилання для зміни пароля діє 30 хвилин і спрацьовує один раз. Замовте нове — і відкрийте його з останнього листа."
    >
      <Link href="/forgot-password">
        <SubmitButton type="button">Замовити нове посилання</SubmitButton>
      </Link>
    </StepScreen>
  );
}
