import { useRef, useState } from 'react'
import { Button, Input, Typography } from 'antd'
import type { InputRef } from 'antd'
import { useNavigate } from 'react-router'
import { normalizeLiveCode } from '@/features/live/model/live'
import styles from './LiveJoinPage.module.css'

export function LiveJoinPage() {
  const navigate = useNavigate()
  const inputRef = useRef<InputRef>(null)
  const [code, setCode] = useState('')
  const [touched, setTouched] = useState(false)
  const complete = code.length === 6

  function join() {
    setTouched(true)
    if (!complete) {
      inputRef.current?.focus()
      return
    }
    void navigate(`/live/${code}`)
  }

  return (
    <main className={styles.page}>
      <section className={styles.card}>
        <div className={styles.logo}>EM</div>
        <span className={styles.eyebrow}>EDUMIND LIVE</span>
        <Typography.Title>加入实时课堂</Typography.Title>
        <Typography.Paragraph>输入老师展示的 6 位课堂码</Typography.Paragraph>
        <div className={styles.codeSlots} aria-hidden="true">
          {Array.from({ length: 6 }, (_, index) => (
            <span className={index === code.length ? styles.current : ''} key={index}>
              {code[index] || ''}
            </span>
          ))}
        </div>
        <Input
          ref={inputRef}
          autoFocus
          className={styles.realInput}
          aria-label="6 位课堂码"
          value={code}
          maxLength={6}
          autoCapitalize="characters"
          onChange={(event) => {
            setCode(normalizeLiveCode(event.target.value))
            setTouched(false)
          }}
          onPressEnter={join}
          placeholder="例如 ABC234"
        />
        {touched && !complete && <p className={styles.error}>请输入完整的 6 位课堂码</p>}
        <Button size="large" type="primary" block disabled={!complete} onClick={join}>
          进入课堂
        </Button>
        <small>课堂码不包含容易混淆的 I、L、O、0、1</small>
      </section>
    </main>
  )
}
