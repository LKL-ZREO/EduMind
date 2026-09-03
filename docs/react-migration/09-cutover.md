# Stage 9: production cutover and single release ownership

## Outcome

The migration is complete. `react-project/` is now the only frontend used by:

- local development instructions;
- the frontend CI quality gate;
- the production Nginx image;
- deployment and architecture documentation.

The cutover path is now:

```text
React source + React package lock
-> npm ci
-> type-check + lint + tests + Vite production build
-> copy React dist/ into the Nginx image
-> serve React routes and proxy backend transports
```

The old `vue-project/` is retained as a read-only migration reference. It is no
longer a build input, CI target, deployment target, or documented default.

## Why this stage matters

A feature migration is not finished merely because every page exists in
React. If Docker still copies Vue, CI still protects Vue, or the README still
starts Vue by default, the repository has two competing sources of truth.

Production cutover therefore has to change one coherent release contract:

| Concern       | Before cutover                  | After cutover                             |
| ------------- | ------------------------------- | ----------------------------------------- |
| Source        | Vue production + React staging  | React only                                |
| Lockfile      | Vue dependency graph            | React dependency graph                    |
| CI            | Vue as the main frontend        | React `ci:check`                          |
| Docker        | Vue `dist/` copied to Nginx     | React `dist/` copied to Nginx             |
| Runtime shell | Vue application root            | React `id="root"`                         |
| Documentation | mixed or migration-era commands | React development and deployment commands |
| Vue           | executable behavior baseline    | read-only historical reference            |

Changing these pieces together avoids a split-brain repository where local
development, pull-request checks, and production deploy different
applications.

## The production Docker build

`edumind/Dockerfile.nginx` now copies only:

```text
react-project/package.json
react-project/package-lock.json
react-project source
```

The builder runs `npm ci` before `npm run build`, then copies the generated
`dist/` into the final Nginx image.

Using `npm ci` and the committed React lockfile makes dependency installation
reproducible. The image cannot silently resolve a different package graph from
the one tested in CI. Keeping the Node builder separate from the Nginx runtime
also means production contains static assets and Nginx, not the frontend build
toolchain.

This is a multi-stage Docker build:

```text
Node stage: install + compile
             |
             v
Nginx stage: copy dist/ + runtime configuration
```

## React is the single CI frontend

The GitHub Actions frontend job now uses Node 22 and the React lockfile cache:

```text
npm ci
-> Prettier check
-> TypeScript project build
-> oxlint
-> ESLint
-> Vitest
-> Vite production build
```

The Docker job depends on both the backend and React jobs. It then builds the
real Nginx image, starts it in HTTP bootstrap mode, and checks:

- `/health` responds successfully;
- `/` contains the React `id="root"` mount point;
- `/teacher/chat` also returns the React shell, proving deep-route SPA
  fallback works in the running container.

This is stronger than checking that a Dockerfile contains the right folder
name. A syntactically correct Dockerfile can still produce an image with a bad
entrypoint, an invalid Nginx template, missing assets, or broken route fallback.

## Two Nginx runtime modes

The entrypoint selects one of two templates:

```text
certificate absent  -> nginx.bootstrap.conf.template (HTTP)
certificate present -> nginx.conf.template           (HTTPS)
```

Both modes must preserve the same application transport behavior:

- `try_files $uri $uri/ /index.html` for React Router deep links;
- `/api` reverse proxying for normal HTTP requests;
- buffering disabled for AI SSE streams;
- HTTP Upgrade headers and long timeouts for `/ws/live`;
- `/mcp` streaming proxy behavior;
- immutable caching for fingerprinted `/assets` files;
- `/actuator` blocked at the public edge.

The cutover test reads both templates. This matters because local certificate
bootstrap and normal HTTPS production can otherwise behave differently even
though they are built from the same image.

## SPA fallback is part of the routing contract

React Router handles routes in the browser, but a direct request such as:

```text
GET /teacher/chat
```

reaches Nginx before React exists. Nginx must return `index.html`; React Router
then resolves `/teacher/chat`. Without the fallback, client-side navigation
works while refresh, bookmarks, and pasted links return 404.

The React test also walks the exported route-object tree and asserts ownership
of all 17 routes captured in the frozen Vue behavior contract:

```text
public submission, auth, submission review, live join, student live,
public preview, chat, knowledge, classes, class detail, tasks, task detail,
dashboard, teacher live, pre-lesson, and preview creation
```

It additionally rejects the temporary `/migration/foundation` route. The
migration scaffold cannot accidentally remain visible in the production app.

This demonstrates a useful React Router design: route configuration can be
exported as data, rendered by `useRoutes`, and inspected by tests without
starting a browser server.

## SSE and WebSocket proxying

Normal reverse-proxy defaults are insufficient for the two long-lived AI
application transports.

For SSE, proxy buffering and gzip are disabled on the streaming endpoints so
tokens are delivered incrementally instead of being collected into a delayed
response.

For STOMP over WebSocket, Nginx forwards the `Upgrade` and `Connection`
headers and uses long read/write timeouts. The HTTP request becomes a persistent
duplex connection that the stage 8 lifecycle code can reconnect.

The distinction is:

```text
SSE:       server -> browser stream over an HTTP response
WebSocket: browser <-> server persistent upgraded connection
```

Both cross the same Nginx image, but they need different proxy semantics.

## Production Content Security Policy

The HTTPS template now uses:

```text
script-src 'self'
```

The former `'unsafe-inline'` script allowance is removed. Vite emits external,
fingerprinted JavaScript bundles, so the React build does not need inline
scripts. Removing the allowance reduces the impact of an HTML injection that
tries to execute an inline `<script>` or event handler.

`style-src 'unsafe-inline'` remains because the current component and chart
libraries rely on runtime inline styles. Security policy should describe the
application that actually runs; it should be tightened deliberately rather
than copied unchanged from a retired frontend.

## Why Vue is archived instead of deleted

Deleting `vue-project/` during the cutover would be irreversible cleanup with
little immediate production benefit. Keeping it read-only provides:

- evidence of the original behavior contracts;
- a side-by-side explanation for Vue-to-React interview questions;
- a reference when investigating parity regressions;
- a safe rollback aid while the first React release is observed.

It is still retired operationally: no default command, CI job, Dockerfile, or
deployment document consumes it. This separates archival from ownership.

A later repository-cleanup change can remove it after the React release has
been observed in the target environment and version history is considered
sufficient.

## Temporary migration UI removal

The foundation demonstration page and generic migration placeholder were
useful while React owned only part of the route tree. Once every feature route
was implemented, those components became dead product code.

They were removed together with `/migration/foundation`. This illustrates a
broader rule: scaffolding should have an explicit deletion condition. Leaving
temporary routes indefinitely increases bundle, navigation, and maintenance
surface.

## Tests added for the cutover

`src/test/cutover.spec.ts` verifies that:

- React owns all 17 frozen business routes;
- no temporary migration route remains;
- the Nginx Dockerfile copies the React manifest, lockfile, and source;
- the production Dockerfile contains no Vue build input;
- both Nginx modes preserve SPA fallback;
- both Nginx modes preserve SSE buffering rules;
- both Nginx modes preserve WebSocket Upgrade behavior;
- the HTTPS CSP does not allow inline scripts.

These are architecture tests. They protect relationships between repository
layers that a page component test cannot see.

## Verification

The final React quality gate completed successfully:

```text
PASS  prettier --check
PASS  tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (25 files, 71 tests)
PASS  vite production build (2,445 modules)
PASS  npm audit --audit-level=moderate (0 vulnerabilities)
```

The archived Vue baseline was also run once after the cutover changes:

```text
PASS  prettier --check
PASS  vue-tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (4 files, 19 tests)
PASS  vite production build (2,432 modules)
```

The React build continues to report the known ECharts route chunk at about
554.98 kB minified and 187.12 kB gzip. It is already isolated behind the lazy
teaching-dashboard route. The warning threshold was not raised to conceal the
tradeoff.

Docker Desktop was installed but its Linux daemon was not running in the local
environment, so the image could not be built and started locally. Static
cutover tests validate the Dockerfile and both Nginx templates, and GitHub CI
now performs the real image build plus running-container checks.

No backend source was changed by this stage.

## React and delivery knowledge demonstrated

This final stage connects React knowledge to production delivery:

- React Router routes are configuration data and can be contract-tested;
- a client-side router still depends on server-side SPA fallback;
- lazy page boundaries affect deployable chunk topology;
- the Vite build output, lockfile, and HTML mount point form a release
  contract;
- CI should verify source quality and the artifact that will actually run;
- SSE and WebSocket lifecycle code depends on correct reverse-proxy behavior;
- CSP requirements should be derived from the emitted application;
- migration completion means one owner for development, validation, and
  deployment.

These are strong interview discussion points because they show ownership of a
React application beyond component syntax.

## Final boundary

Stages 0 through 9 are complete. All frozen Vue business routes now have React
owners, React is the single production frontend, and the Vue project is a
read-only historical reference.

The next work is no longer migration. It is normal product evolution:
production smoke testing with real services, observability, performance
budgets, accessibility review, and feature development directly in React.
