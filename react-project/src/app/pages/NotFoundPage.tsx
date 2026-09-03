import { Button, Result } from 'antd'
import { useNavigate } from 'react-router'

export function NotFoundPage() {
  const navigate = useNavigate()

  return (
    <Result
      status="404"
      title="页面不存在"
      subTitle="该地址尚未迁移，或者页面不存在。"
      extra={
        <Button type="primary" onClick={() => void navigate('/')}>
          返回迁移首页
        </Button>
      }
    />
  )
}
