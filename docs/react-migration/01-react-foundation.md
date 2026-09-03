# Stage 1: React foundation

## Outcome

The new `react-project/` is an independently runnable React application. It
uses port 5174 while the Vue reference application keeps port 5173. No backend
contract or Vue business page was changed in this stage.

The application now has:

- React 19 and TypeScript 6 application bootstrap;
- React Router 8 Data Mode;
- TanStack Query server-state provider;
- Ant Design theme and Chinese locale;
- Axios client with the existing cookie-session and CSRF rules;
- shared API error and HTML-sanitizing utilities;
- Vite `/api` and `/ws` development proxies;
- CSS tokens and CSS Modules;
- Vitest, React Testing Library, TypeScript, oxlint, ESLint, and Prettier gates;
- a parallel GitHub Actions job that keeps both Vue and React green;
- architecture tests that reject Vue/Pinia imports and legacy global folders.

## Locked core versions

The npm lockfile records exact dependency resolution. The main versions at the
end of this stage are:

| Package              | Version |
| -------------------- | ------- |
| React / React DOM    | 19.2.8  |
| React Router         | 8.3.0   |
| TanStack React Query | 5.101.4 |
| Ant Design           | 6.5.3   |
| Zustand              | 5.0.14  |
| TypeScript           | 6.0.3   |
| Vite                 | 8.2.0   |
| Vitest               | 4.1.10  |

Zustand is installed for later feature-local cross-component state, but stage 1
does not create a store merely to demonstrate the dependency.

## Runtime composition

```text
index.html #root
-> createRoot(...).render(...)
-> React.StrictMode
-> Ant Design ConfigProvider
-> TanStack QueryClientProvider
-> Ant Design App context
-> App
-> React Router RouterProvider
-> route component
```

Each provider has one responsibility:

- `StrictMode` exposes unsafe render/effect assumptions during development.
- `ConfigProvider` provides design tokens and Chinese UI locale.
- `QueryClientProvider` owns server-state caching and mutation coordination.
- Ant Design `App` supplies contextual messages and confirmation modals.
- `RouterProvider` owns URL matching, navigation, route errors, loaders, and
  later navigation blockers.

Provider order is intentional. Route pages can consume the Ant Design theme and
Query Client, while neither global provider needs to know about a business
route.

## Shared API boundary

The React application keeps the Vue security behavior but separates it into
smaller modules:

```text
shared/api/client.ts  -> Axios instance and interceptors
shared/api/csrf.ts    -> token cache, in-flight request deduplication, refresh
shared/api/errors.ts  -> unknown/Axios error normalization
shared/api/types.ts   -> response envelope and API problem types
shared/api/health.ts  -> first domain-returning API function
```

The API layer does not import React and does not call hooks. Axios interceptors
run outside React's render tree, so they expose a configurable unauthorized
callback instead. Stage 2 will bind that callback to authentication cache
cleanup and navigation.

Unsafe requests continue to:

1. fetch `/api/auth/csrf` when no token is cached;
2. send `X-XSRF-TOKEN`;
3. refresh and retry once when the server returns `40301`;
4. preserve cookie credentials;
5. keep public student live routes separate from teacher-session redirects.

## First TanStack Query

The foundation page treats backend health as server state:

```text
query key: ['system', 'health']
query function: checkHealth
stale time: 3 minutes
retry: disabled for the visible smoke check
```

This deliberately replaces the Vue application's hand-written health cache.
TanStack Query now owns freshness, pending/success/error state, and cached data.
The component only describes how each state should render.

## TypeScript 6 lesson

The first type check rejected the old `baseUrl` compiler option because it is
deprecated in TypeScript 6. The project removed `baseUrl` and retained the
`paths` mapping instead of hiding the warning with `ignoreDeprecations`.

Typed Axios response bodies were also added at the network boundary. This
prevents `any` from spreading from CSRF or error responses into future hooks and
components.

## Test coverage introduced in this stage

- architecture stays feature-first;
- React application code cannot import Vue, Vue Router, or Pinia;
- route pages remain owned by `app` or a feature;
- the foundation page renders through a real Query Client;
- a mocked health request transitions to success;
- the five existing HTML/Markdown/KaTeX security tests pass unchanged in React.

The security test reuse is important evidence that pure domain/security code
can move between frameworks without being rewritten.

## Verification

`npm run ci:check` completed successfully:

```text
PASS  prettier --check .
PASS  tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (3 files, 9 tests)
PASS  vite production build (1,642 modules)
```

The development server was also started temporarily on
`http://127.0.0.1:5174`; an HTTP request returned status 200 and contained the
React root element. The temporary process was stopped after the smoke test.

The repository CI now runs `react-project/npm run ci:check` in a separate job.
The Docker sanity build still packages Vue during the migration, but it depends
on the backend, Vue, and React checks so the new application cannot silently
degrade between stages.

The initial production JavaScript bundle is 895.98 kB before gzip and 290.97 kB
after gzip, which triggers Vite's 500 kB warning. It is already smaller than the
Vue baseline main chunks, but route lazy loading and dependency splitting remain
a required cutover task. The warning is documented rather than suppressed.

## Stage boundary

Stage 1 intentionally does not implement authentication pages, teacher layout,
or business routes. Those depend on decisions about session bootstrap and route
loaders and belong to stage 2. Keeping the boundary small makes foundation
failures distinguishable from authentication behavior failures.
