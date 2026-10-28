import styles from "./SectionPlaceholderPage.module.css";

// Один экран на все разделы, которых ещё нет: навигация уже работает,
// а содержимое приедет со своими задачами.
export function SectionPlaceholderPage({ title }: { title: string }) {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>{title}</h1>
      <p className={styles.text}>Розділ у розробці.</p>
    </div>
  );
}
