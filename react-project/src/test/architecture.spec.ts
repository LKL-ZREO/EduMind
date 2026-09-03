import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const sourceRoot = resolve(process.cwd(), 'src')

function filesUnder(relativePath: string): string[] {
  const root = resolve(sourceRoot, relativePath)
  if (!existsSync(root)) return []
  return readdirSync(root).flatMap((name) => {
    const path = resolve(root, name)
    return statSync(path).isDirectory() ? filesUnder(path.slice(sourceRoot.length + 1)) : [path]
  })
}

describe('React frontend architecture', () => {
  it('keeps application code in app, features, shared, and test', () => {
    const legacyRoots = [
      'api',
      'components',
      'hooks',
      'pages',
      'router',
      'stores',
      'utils',
      'views',
    ]
    for (const root of legacyRoots) {
      expect(filesUnder(root), `legacy src/${root} files`).toEqual([])
    }
  })

  it('does not import Vue or Pinia into the React application', () => {
    const applicationFiles = [
      ...filesUnder('app'),
      ...filesUnder('features'),
      ...filesUnder('shared'),
    ].filter((path) => path.endsWith('.ts') || path.endsWith('.tsx'))

    for (const path of applicationFiles) {
      const source = readFileSync(path, 'utf8')
      expect(source, path).not.toMatch(/from ['"](?:vue|vue-router|pinia)['"]/)
    }
  })

  it('keeps route pages owned by app or features', () => {
    const router = readFileSync(resolve(sourceRoot, 'app/router/index.tsx'), 'utf8')
    expect(router).toMatch(/@\/app\/pages|@\/features\//)
    expect(router).not.toMatch(/@\/(?:pages|views)\//)
  })
})
