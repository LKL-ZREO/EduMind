import type { PropsWithChildren, ReactNode } from 'react'
import { Link } from 'react-router'
import styles from './AuthPageShell.module.css'

type AuthPageShellProps = PropsWithChildren<{
  eyebrow: string
  title: string
  summary: string
  steps: Array<{ title: string; description: string }>
  cardTitle: string
  cardDescription: string
  footer: ReactNode
}>

export function AuthPageShell({
  eyebrow,
  title,
  summary,
  steps,
  cardTitle,
  cardDescription,
  footer,
  children,
}: AuthPageShellProps) {
  return (
    <main className={styles.shell}>
      <section className={styles.briefing} aria-label="EduMind 产品概览">
        <Link to="/" className={styles.brand}>
          <span className={styles.brandMark}>EM</span>
          <span>EduMind</span>
        </Link>

        <div>
          <p className={styles.eyebrow}>{eyebrow}</p>
          <h1>{title}</h1>
          <p className={styles.summary}>{summary}</p>
        </div>

        <ol className={styles.steps}>
          {steps.map((step, index) => (
            <li key={step.title}>
              <span>{String(index + 1).padStart(2, '0')}</span>
              <div>
                <strong>{step.title}</strong>
                <p>{step.description}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className={styles.formPanel}>
        <div className={styles.card}>
          <header className={styles.cardHeader}>
            <h2>{cardTitle}</h2>
            <p>{cardDescription}</p>
          </header>
          {children}
          <footer className={styles.footer}>{footer}</footer>
        </div>
      </section>
    </main>
  )
}
