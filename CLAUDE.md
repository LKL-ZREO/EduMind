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

Environment config: copy `edumind/.env.example` to `edumind/.env` and fill in required values (`DB_PASS`, `JWT_SECRET`, `OPENCLAW_API_KEY`, `ONEBOT_TOKEN`).

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
| OpenClaw | AI gateway (LLM API key management, model routing, tool calling) | `localhost:18789` |
| OneBot/NapCat | QQ bot client | `localhost:3000` |

## Architecture

### Backend (`edumind/src/main/java/com/firedemo/demo/`)

**RAG pipeline** (`rag/`) — the core retrieval system:
1. `EmbeddingService` — ONNX text embedding (DJL + ONNX Runtime, local CPU inference)
2. `VectorStoreService` — pgvector similarity search + PostgreSQL full-text keyword search
3. `RrfFusionService` — RRF (Reciprocal Rank Fusion) merging dual-path results
4. `RerankerService` — Cross-encoder fine-ranking (ONNX bge-reranker-base)
5. `QueryRewriter` — LLM-based query rewriting for vague queries and low-confidence fallback
6. `RagService` — unified entry point orchestrating the full pipeline; used by `KnowledgeSearchTool`, `OnebotRagController`, and `DocumentServiceImpl`
7. `SmartChunkService` — document chunking with markdown-structure, code-function, dialogue, and semantic-boundary strategies

**MCP tool system** (`mcp/`) — implements the Model Context Protocol JSON-RPC endpoint at `/mcp`:
- `McpController` — JSON-RPC handler (initialize, tools/list, tools/call)
- `ToolDefinition` — interface; implementations in `mcp/tools/` are auto-discovered by Spring and exposed to OpenClaw's agent
- `ToolContextHolder` — ThreadLocal context carrying user/session info injected from MCP session store
- Tools: `KnowledgeSearchTool`, `ClassStatusTool`, `HomeworkTasksTool`, `StudentStatsTool`, `CurrentTimeTool`

**Agent workflow engine** (`agent/workflow/`) — DAG-based execution for homework grading:
- `WorkflowEngine` — topological DAG executor with max-step guard, fallback nodes, and trace tracking via Caffeine cache
- `GradingWorkflow` — concrete workflow definition for homework auto-grading

**Async processing** (`common/async/`) — Redis Stream-based consumer framework:
- `AbstractStreamConsumer` — base class for Redis Stream consumers
- `GradingStreamProducer` / `GradingStreamConsumer` — async homework grading via Redis Streams with distributed lock (Redisson) to prevent duplicate processing

**Infrastructure** (`common/`):
- `limiter/` — Bucket4j + Redisson distributed token-bucket rate limiting (`TokenBucketInterceptor` registered on `/api/**`)
- `cache/` — Cache-Aside pattern with Caffeine local cache + Redis synchronization via `CacheConsistencyService`
- `prompt/` — Prompt template loader from `src/main/resources/prompts/`
- `ai/` — structured output caller for LLM JSON responses

**Config** (`config/`):
- `SecurityConfig` — JWT stateless auth via `JwtAuthenticationFilter` + MCP API key filter; public endpoints: `/api/auth/**`, `/api/homework/**`, `/api/onebot/rag`, `/api/teacher/classes/join`
- `WebMvcConfig` — registers `TokenBucketInterceptor` (excludes `/api/auth/**`, `/actuator/**`, `/error`)
- `properties/` — typed configuration property classes

**REST controllers** (`Controller/`): `AgentController`, `AuthController`, `ChatController`, `ClassController`, `CourseController`, `DashboardController`, `DocumentController`, `FileUploadController`, `HomeworkController`, `OnebotRagController`, `SharedKbController`, `SubmissionController`, `TaskController`

**Data layer**: MyBatis-Plus mappers in `mapper/`, entities in `Entity/`, DTOs in `DTO/`. Flyway migrations at `src/main/resources/db/migration/`.

**Key dependencies**: PostgreSQL 16 + pgvector, Redis 7 (Redisson), MinIO (S3), LangChain4j 1.13.0 (OpenAI-compatible via OpenClaw), DJL 0.28.0 + ONNX Runtime, Resilience4j circuit breaker on AI calls.

### Frontend (`vue-project/src/`)

**Route structure** (see `router/index.ts`):
- `/` — student submit page (public, no auth)
- `/login`, `/register` — teacher auth (guests only)
- `/teacher/chat` — AI chat
- `/teacher/docs` — knowledge base management
- `/teacher/classes`, `/teacher/classes/:id` — class list & management
- `/teacher/tasks`, `/teacher/tasks/:id` — homework tasks & details
- `/teacher/data` — dashboard with heatmaps
- `/view/submission/:id` — submission review
- Route guards: `requiresAuth` meta → redirect to login; `requiresGuest` → redirect to chat if logged in; AI-responding guard with confirm dialog

**State management** (`stores/`): Pinia stores — `auth.ts` (user, token, login/register/logout), `chat.ts`, `class.ts`

**API layer** (`api/`): Axios instance with base `/api`, auto Bearer token injection, 401 → login redirect. `cache.ts` provides `getCached`/`setCache` for client-side caching.

**UI**: Element Plus component library, ECharts for visualizations, Tiptap rich text editor, Marked + highlight.js + KaTeX for markdown rendering.

## Key Architectural Patterns

- **All LLM calls go through OpenClaw** at `OPENCLAW_BASE_URL` (default `http://localhost:18789/v1`), never directly to model providers. OpenClaw handles API key management, model routing, and agent tool calling.
- **MCP tools are the bridge** between the LLM agent and backend data — when the AI needs to search knowledge, check class status, or query student stats, OpenClaw routes these as MCP tool calls to `/mcp`.
- **RAG is the single truth** for knowledge retrieval — `RagService.search()` is the only retrieval implementation; all callers (MCP tools, QQ bot, document service) route through it.
- **Redis Streams for async work** — homework grading is queued via Redis Streams, consumed asynchronously with distributed locks preventing duplicate processing.
- **Rate limiting is at the interceptor layer** — per-endpoint configurable token buckets in `application.properties` under `rate-limit.rules[...]`.
- **File storage is abstracted** — `FileStorageService` interface with `LocalFileStorageServiceImpl` (disk) and `S3FileStorageServiceImpl` (MinIO/OSS/COS) implementations, toggled by `STORAGE_TYPE` env var.
