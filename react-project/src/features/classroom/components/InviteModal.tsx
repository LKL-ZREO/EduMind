import { useEffect, useMemo, useRef } from 'react'
import { App, Button, Descriptions, Modal, Space, Typography } from 'antd'
import QRCode from 'qrcode'

type InviteModalProps = {
  open: boolean
  className?: string
  inviteCode: string
  onClose: () => void
}

export function InviteModal({ open, className, inviteCode, onClose }: InviteModalProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const { message } = App.useApp()
  const inviteLink = useMemo(() => {
    const url = new URL('/', window.location.origin)
    url.searchParams.set('inviteCode', inviteCode)
    return url.toString()
  }, [inviteCode])

  useEffect(() => {
    if (!open || !canvasRef.current || !inviteCode) return
    void QRCode.toCanvas(canvasRef.current, inviteLink, {
      width: 184,
      margin: 1,
      color: { dark: '#172033', light: '#ffffff' },
    }).catch(() => {
      message.error('二维码生成失败')
    })
  }, [inviteCode, inviteLink, message, open])

  async function copy(value: string, label: string) {
    try {
      await navigator.clipboard.writeText(value)
      message.success(`${label}已复制`)
    } catch {
      message.error(`无法复制${label}`)
    }
  }

  return (
    <Modal
      open={open}
      title={`邀请学生加入${className ? `「${className}」` : ''}`}
      footer={null}
      onCancel={onClose}
    >
      <Space direction="vertical" size={20} style={{ width: '100%' }}>
        <div style={{ display: 'grid', placeItems: 'center' }}>
          <canvas ref={canvasRef} width={184} height={184} aria-label="班级邀请二维码" />
          <Typography.Text type="secondary">学生扫描二维码打开作业提交页</Typography.Text>
        </div>

        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="邀请码">
            <Space>
              <Typography.Text code copyable={false}>
                {inviteCode}
              </Typography.Text>
              <Button size="small" onClick={() => void copy(inviteCode, '邀请码')}>
                复制
              </Button>
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="邀请链接">
            <Space>
              <Typography.Text ellipsis={{ tooltip: inviteLink }} style={{ maxWidth: 260 }}>
                {inviteLink}
              </Typography.Text>
              <Button size="small" onClick={() => void copy(inviteLink, '邀请链接')}>
                复制
              </Button>
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </Space>
    </Modal>
  )
}
