# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

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

The backend is a business-first modular monolith. Top-level packages describe product capabilities instead of technical layers:
- `auth/` — users, sessions, authentication filters, and resource authorization
- `classroom/` — classes, courses, students, and QQ bindings
- `teaching/` — dashboard, lesson preparation, question bank, preview tasks, and timeline
- `homework/` — drafts, tasks, submissions, reminders, grading streams, and grading workflow
- `knowledge/` — documents, directories, shared knowledge bases, vocabulary, and reclassification
- `live/` — classroom sessions, interactions, presence, WebSocket handlers, and student security
- `assistant/` — Agent API and LangChain4j implementation, chat, memory, tools, vision, and evaluation
- `integration/` — MCP transport, OneBot, document parsing, and storage adapters
- `platform/` — configuration, cache, messaging, rate limiting, web, security, and shared ports
- `shared/` — result envelopes and business exceptions only

**RAG pipeline** (`knowledge/retrieval/`) — the core retrieval system:
1. `EmbeddingService` — ONNX text embedding (DJL + ONNX Runtime, local CPU inference)
2. `VectorStoreService` — pgvector similarity search + PostgreSQL full-text keyword search
3. `RrfFusionService` — RRF (Reciprocal Rank Fusion) merging dual-path results
4. `RerankerService` — Cross-encoder fine-ranking (ONNX bge-reranker-base)
5. `QueryRewriter` — LLM-based query rewriting for vague queries and low-confidence fallback
6. `RagService` — unified entry point orchestrating the full pipeline; used by `KnowledgeSearchTool`, `OnebotRagController`, and `DocumentService`
7. `SmartChunkService` — document chunking with markdown-structure, code-function, dialogue, and semantic-boundary strategies

**MCP tool system** — `integration/mcp/` implements the JSON-RPC transport at `/mcp`, while reusable tool contracts and implementations live in `assistant/tool/`:
- `McpController` — JSON-RPC handler (initialize, tools/list, tools/call)
- `ToolDefinition` — neutral interface auto-discovered by Spring, used locally through `LangChain4jToolBridge`, and optionally exposed to external MCP clients
- `AgentSessionStore` — Redis-backed user/session context shared by the built-in Agent and MCP transport
- Tools: `KnowledgeSearchTool`, `ClassStatusTool`, `HomeworkTasksTool`, `StudentStatsTool`, `CurrentTimeTool`

**Agent workflow engine** (`homework/grading/workflow/`) — DAG-based execution for homework grading:
- `WorkflowEngine` — topological DAG executor with max-step guard, fallback nodes, and trace tracking via Caffeine cache
- `GradingWorkflow` — concrete workflow definition for homework auto-grading

**Async processing** — generic Redis Stream support lives in `platform/messaging/`; grading producers and consumers live in `homework/grading/`:
- `AbstractStreamConsumer` — base class for Redis Stream consumers
- `GradingStreamProducer` / `GradingStreamConsumer` — async homework grading via Redis Streams with distributed lock (Redisson) to prevent duplicate processing

Controllers, application services, models, request/response types, and MyBatis mappers are colocated in their owning feature. Controllers must depend on application services, never directly on mappers. Mapper interfaces are self-registering with `@Mapper`; do not restore a package-wide `@MapperScan`.

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

Frontend code is feature-first under `features/` (`auth`, `assistant`, `classroom`, `homework`, `knowledge`, `live`, `teaching`). Each feature owns its views, API wrappers, and Pinia store. Cross-feature UI, editor, API client, styles, and utilities live in `shared/`; app bootstrap and routing live in `app/`.

**UI**: Element Plus component library, ECharts for visualizations, Tiptap rich text editor, Marked + highlight.js + KaTeX for markdown rendering.

## Key Architectural Patterns

- **The built-in LangChain4j Agent is the active LLM path** — it connects directly to the OpenAI-compatible endpoint configured by `LLM_BASE_URL`/`LLM_API_KEY`; optional vision settings may use a separate endpoint.
- **Local tools and external MCP share tool definitions** — the built-in Agent executes `ToolDefinition` beans through `LangChain4jToolBridge`; `/mcp` exposes the same business capabilities only for optional external clients.
- **`AgentService` is the provider-neutral Agent port** — its active implementation is `LangChain4jAgentService`.
- **RAG is the single truth** for knowledge retrieval — `RagService.search()` is the only retrieval implementation; all callers (MCP tools, QQ bot, document service) route through it.
- **Redis Streams for async work** — homework grading is queued via Redis Streams, consumed asynchronously with distributed locks preventing duplicate processing.
- **Rate limiting is at the interceptor layer** — per-endpoint configurable token buckets in `application.properties` under `rate-limit.rules[...]`.
- **File storage is abstracted** — the `platform.storage.FileStorage` port has `LocalFileStorage` and `S3FileStorage` adapters, toggled by `STORAGE_TYPE`.
