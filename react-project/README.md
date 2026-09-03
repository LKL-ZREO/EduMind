# EduMind React frontend

This is the production React frontend for EduMind. It replaced the Vue
application after the complete route, protocol, security, CI, and Docker
cutover. The archived `vue-project/` directory is a read-only migration
reference and is not part of the production build.

## Commands

```bash
npm install
npm run dev
npm run ci:check
npm run build
```

The development server proxies `/api` and `/ws` to the Spring Boot application
on `127.0.0.1:8080`.

## Architecture

- `src/app/`: application bootstrap, providers, router, and layouts
- `src/features/`: feature-owned pages, components, APIs, hooks, and models
- `src/shared/`: framework-independent API, security, UI, and utility modules
- `src/test/`: shared test setup and future network handlers

The migration contract and stage history live in
[`../docs/react-migration/`](../docs/react-migration/README.md).
