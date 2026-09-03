import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { RouteObject } from 'react-router'
import { routeObjects } from '@/app/router'

const repositoryRoot = resolve(process.cwd(), '..')

function routePaths(routes: RouteObject[], parent = ''): string[] {
  return routes.flatMap((route) => {
    let current = parent
    if (route.index) current = parent || '/'
    else if (route.path === '*') current = '*'
    else if (route.path) current = `${parent}/${route.path}`.replace(/\/+/g, '/')
    return [current || '/', ...(route.children ? routePaths(route.children, current) : [])]
  })
}

describe('React production cutover', () => {
  it('owns every route from the frozen Vue behavior contract', () => {
    const paths = new Set(routePaths(routeObjects))
    const expected = [
      '/',
      '/login',
      '/register',
      '/view/submission/:id',
      '/live/join',
      '/live/:sessionCode',
      '/preview/:taskId',
      '/teacher/chat',
      '/teacher/docs',
      '/teacher/classes',
      '/teacher/classes/:id',
      '/teacher/tasks',
      '/teacher/tasks/:id',
      '/teacher/data',
      '/teacher/live/:classId',
      '/teacher/pre-lesson',
      '/teacher/preview/create',
    ]
    for (const path of expected) expect(paths, path).toContain(path)
    expect(paths).not.toContain('/migration/foundation')
  })

  it('builds the production Nginx image from the React lockfile and source', () => {
    const dockerfile = readFileSync(resolve(repositoryRoot, 'edumind/Dockerfile.nginx'), 'utf8')
    expect(dockerfile).toContain('COPY react-project/package.json react-project/package-lock.json')
    expect(dockerfile).toContain('COPY react-project/ ./')
    expect(dockerfile).not.toContain('vue-project')
  })

  it('preserves SPA fallback, SSE streaming, WebSocket upgrade, and production CSP', () => {
    const configurations = ['nginx.bootstrap.conf.template', 'nginx.conf.template'].map((file) =>
      readFileSync(resolve(repositoryRoot, `edumind/${file}`), 'utf8'),
    )

    for (const nginx of configurations) {
      expect(nginx).toContain('try_files $uri $uri/ /index.html;')
      expect(nginx).toMatch(/api\/chat\/\(stream\|multimodal\/stream\)[\s\S]*proxy_buffering off;/)
      expect(nginx).toMatch(/location = \/ws\/live[\s\S]*proxy_set_header Upgrade \$http_upgrade;/)
    }

    const httpsNginx = configurations[1]
    expect(httpsNginx).toContain("script-src 'self';")
    expect(httpsNginx).not.toContain("script-src 'self' 'unsafe-inline'")
  })
})
