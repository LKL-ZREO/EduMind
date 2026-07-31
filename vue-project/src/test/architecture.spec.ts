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

describe('frontend architecture', () => {
  it('keeps application code in app, features, and shared', () => {
    const legacyRoots = ['api', 'components', 'extensions', 'router', 'stores', 'utils', 'views']
    for (const root of legacyRoots) {
      expect(filesUnder(root), `legacy src/${root} files`).toEqual([])
    }
  })

  it('does not reintroduce imports from legacy global folders', () => {
    const applicationFiles = [
      ...filesUnder('app'),
      ...filesUnder('features'),
      ...filesUnder('shared'),
    ].filter((path) => path.endsWith('.ts') || path.endsWith('.vue'))

    for (const path of applicationFiles) {
      const source = readFileSync(path, 'utf8')
      expect(source, path).not.toMatch(/@\/(api|components|extensions|router|stores|utils|views)\//)
    }
  })

  it('routes directly to feature-owned views', () => {
    const router = readFileSync(resolve(sourceRoot, 'app/router/index.ts'), 'utf8')
    expect(router).toContain("import('@/features/")
    expect(router).not.toContain("import('../views/")
  })
})
