import { Button, Result } from 'antd'
import { isRouteErrorResponse, useRouteError } from 'react-router'

function errorMessage(error: unknown) {
  if (isRouteErrorResponse(error)) {
    return error.statusText || `请求失败（${error.status}）`
  }
  return error instanceof Error ? error.message : '页面加载时发生未知错误'
}

export function RouteErrorPage() {
  const error = useRouteError()

  return (
    <Result
      status="error"
      title="页面加载失败"
      subTitle={errorMessage(error)}
      extra={
        <Button type="primary" onClick={() => window.location.reload()}>
          重新加载
        </Button>
      }
    />
  )
}
