# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EduMind — AI-driven intelligent teaching assistant. A monorepo with a Spring Boot 4 backend (`edumind/`) and Vue 3 frontend (`vue-project/`).

## Build & Run Commands

### Backend (`edumind/`)

```bash
# Start infrastructure (PostgreSQL, Redis, MinIO, Nginx, app)
cd edumind && docker compose up -d

# Run Spring Boot app directly (requires infra services running)
./mvnw spring-boot:run

# Build (skip tests)
./mvnw package -DskipTests

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=ClassName
```

The app runs on `http://localhost:8080`. Swagger UI at `http://localhost:8080/swagger-ui.html`.

Environment config: copy `edumind/.env.example` to `edumind/.env` and fill in the values marked REQUIRED. `ONEBOT_WS_TOKEN` is required only when OneBot is enabled.

### Frontend (`vue-project/`)

```bash
cd vue-project
npm install
npm run dev          # Dev server on http://localhost:5173, proxies /api → :8080
npm run build        # Type-check + production build
npm run lint         # ESLint + oxlint
npm run format       # Prettier
```

### External Services (must be started separately)

| Service | Purpose | Default Address |
|---------|---------|-----------------|
| OneBot/NapCat | QQ bot WebSocket service | `localhost:3001` |

## Architecture

### Backend (`edumind/src/main/java/com/firedemo/edumind/`)

**RAG pipeline** (`knowledge/retrieval/`) — the core retrieval system:
1. `EmbeddingService` — ONNX text embedding (DJL + ONNX Runtime, local CPU inference)
2. `VectorStoreService` — pgvector similarity search + PostgreSQL full-text keyword search
3. `RrfFusionService` — RRF (Reciprocal Rank Fusion) merging dual-path results
4. `RerankerService` — Cross-encoder fine-ranking (ONNX bge-reranker-base)
5. `QueryRewriter` — LLM-based query rewriting for vague queries and low-confidence fallback
6. `RagService` — unified entry point orchestrating the full pipeline; used by `KnowledgeSearchTool`, `OnebotRagController`, and `DocumentService`
7. `SmartChunkService` — document chunking with markdown-structure, code-function, dialogue, and semantic-boundary strategies

**MCP tool system** — transport lives in `integration/mcp/`; reusable tool contracts and implementations live in `assistant/tool/`:
- `McpController` — JSON-RPC handler (initialize, tools/list, tools/call)
- `ToolDefinition` — neutral interface auto-discovered by Spring and shared by LangChain4j and MCP
- `AgentSessionStore` — Redis-backed user/session context shared by Agent and MCP
- Tools: `KnowledgeSearchTool`, `ClassStatusTool`, `HomeworkTasksTool`, `StudentStatsTool`, `CurrentTimeTool`

**Agent workflow engine** (`homework/grading/workflow/`) — DAG-based execution for homework grading:
- `WorkflowEngine` — topological DAG executor with max-step guard, fallback nodes, and trace tracking via Caffeine cache
- `GradingWorkflow` — concrete workflow definition for homework auto-grading

**Async processing** — generic support is in `platform/messaging/`, with grading consumers in `homework/grading/`:
- `AbstractStreamConsumer` — base class for Redis Stream consumers
- `GradingStreamProducer` / `GradingStreamConsumer` — async homework grading via Redis Streams with distributed lock (Redisson) to prevent duplicate processing

**Infrastructure** (`common/`):
- `limiter/` — Bucket4j + Redisson distributed token-bucket rate limiting (`TokenBucketInterceptor` registered on `/api/**`)
- `cache/` — Cache-Aside pattern with Caffeine local cache + Redis synchronization via `CacheConsistencyService`
- `prompt/` — Prompt template loader from `src/main/resources/prompts/`
- `ai/` — structured output caller for LLM JSON responses

**Config** (`config/`):
- `SecurityConfig` — teacher Web auth via Redis-backed Spring Session + CSRF; classroom students use a separate session-scoped bearer token; MCP uses its own API key filter
- `WebMvcConfig` — registers `TokenBucketInterceptor` (excludes `/api/auth/**`, `/actuator/**`, `/error`)
- `properties/` — typed configuration property classes

Controllers, services, models, request/response types, and MyBatis mappers are colocated in the owning business feature. Controllers must not access mappers directly.

**Key dependencies**: PostgreSQL 16 + pgvector, Redis 7 (Redisson), MinIO (S3), LangChain4j 1.15.1 (direct OpenAI-compatible model integration), DJL 0.28.0 + ONNX Runtime, Resilience4j circuit breaker on AI calls.

### Frontend (`vue-project/src/`)

**Route structure** (see `app/router/index.ts`):
- `/` — student submit page (public, no auth)
- `/login`, `/register` — teacher auth (guests only)
- `/teacher/chat` — AI chat
- `/teacher/docs` — knowledge base management
- `/teacher/classes`, `/teacher/classes/:id` — class list & management
- `/teacher/tasks`, `/teacher/tasks/:id` — homework tasks & details
- `/teacher/data` — dashboard with heatmaps
- `/view/submission/:id` — submission review
- Route guards: `requiresAuth` meta → redirect to login; `requiresGuest` → redirect to chat if logged in; AI-responding guard with confirm dialog

Frontend code is feature-first under `features/`; shared API infrastructure, UI, editor, styles, and utilities live under `shared/`, while bootstrap and routing live under `app/`.

**UI**: Element Plus component library, ECharts for visualizations, Tiptap rich text editor, Marked + highlight.js + KaTeX for markdown rendering.

## Key Architectural Patterns

- **The built-in LangChain4j Agent is the active LLM path** — it connects directly to the OpenAI-compatible endpoint configured by `LLM_BASE_URL`/`LLM_API_KEY`; optional vision settings may use a separate endpoint.
- **Local tools and external MCP share tool definitions** — the built-in Agent executes `ToolDefinition` beans through `LangChain4jToolBridge`; `/mcp` exposes the same business capabilities only for optional external clients.
- **`AgentService` is the provider-neutral Agent port** — its active implementation is `LangChain4jAgentService`.
- **RAG is the single truth** for knowledge retrieval — `RagService.search()` is the only retrieval implementation; all callers (MCP tools, QQ bot, document service) route through it.
- **Redis Streams for async work** — homework grading is queued via Redis Streams, consumed asynchronously with distributed locks preventing duplicate processing.
- **Rate limiting is at the interceptor layer** — per-endpoint configurable token buckets in `application.properties` under `rate-limit.rules[...]`.
- **File storage is abstracted** — the `platform.storage.FileStorage` port has `LocalFileStorage` and `S3FileStorage` adapters, toggled by `STORAGE_TYPE`.
