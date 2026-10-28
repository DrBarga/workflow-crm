import { PASSWORD_RULES } from "@shared/lib/passwordPolicy";
import { CheckIcon } from "./common/Icons";
import styles from "./common/step.module.css";

// Живой чек-лист: отмечает выполненные требования по мере ввода.
export function PasswordRules({ password }: { password: string }) {
  return (
    <>
      <ul className={styles.rules}>
        {PASSWORD_RULES.map((rule) => {
          const met = rule.isMet(password);
          return (
            <li key={rule.id} className={met ? styles.ruleMet : ""}>
              <i aria-hidden>{met ? <CheckIcon /> : null}</i>
              <span>{rule.label}</span>
            </li>
          );
        })}
      </ul>
      <p className={styles.rulesNote}>
        Не використовуйте стандартні паролі та комбінації зі своїм імʼям, поштою або назвою
        компанії.
      </p>
    </>
  );
}
