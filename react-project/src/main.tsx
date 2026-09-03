import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import 'antd/dist/reset.css'
import '@/shared/styles/tokens.css'
import '@/shared/styles/global.css'
import { AppProviders } from '@/app/providers/AppProviders'
import { configureApiClient } from '@/app/bootstrap/configureApiClient'

configureApiClient()

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('缺少 React 根节点 #root')
}

createRoot(rootElement).render(
  <StrictMode>
    <AppProviders />
  </StrictMode>,
)
