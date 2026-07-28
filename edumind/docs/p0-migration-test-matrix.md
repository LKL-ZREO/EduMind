# P0 Migration Test Matrix

This document records the active characterization tests established before the
authentication, execution-context, and HTTP error migrations. Tests must be
updated in the owning phase; they must not be deleted merely to make a build pass.

## Phase status

- Phase 0 complete: characterization tests protect the current context, auth,
  HTTP error, SSE, and WebSocket behavior.
- Phase 1 complete: `AgentExecutionContext`, its boundary factory,
  `AgentRunTrace`, and the context-aware tool contract are available.
- Phase 2 complete: Web, QQ, MCP, and LangChain4j tool calls pass the immutable
  context explicitly. The legacy tool entry point and agent business
  `ThreadLocal` holders have been removed.
- Phases 3-5 complete: teacher Web authentication uses indexed Redis-backed
  Spring Session cookies, unsafe browser requests require CSRF, disabled users
  have all sessions revoked, and the SPA no longer stores teacher credentials.
- Phase 6 complete: teacher WebSocket identity and lifetime are bound to the
  Redis-backed HTTP session; students use a separate classroom-scoped token.
  STOMP destinations are restricted by role and classroom, and message resource
  IDs are checked against the bound classroom before writes occur.
- Phase 7 complete: domain error codes retain the existing JSON envelope while
  mapping independently to proper HTTP 4xx/5xx statuses. Exception handlers set
  explicit statuses, and `ResultHttpStatusAdvice` covers legacy direct errors.

| Concern | Active baseline test | Target phase | Target contract |
|---|---|---:|---|
| Tool context isolation | `ToolContextConcurrencyTest` | 1-2 | Concurrent and async tool calls receive an explicit immutable context; no `ToolContextHolder` remains. |
| RAG authorization | `ToolContextConcurrencyTest#keepsRagAuthorizationScopesSeparateDuringConcurrentToolCalls` | 1-2 | User, knowledge-base, and course scopes come from `AgentExecutionContext`, never model arguments. |
| Login transport | `AuthControllerTest#shouldLoginSuccessfully` | 3-5 | Login creates a Redis-backed session cookie and returns no access or refresh token. |
| CSRF | New session-security integration tests | 3-5 | Unsafe requests without a valid CSRF token return HTTP 403. |
| HTTP errors | `GlobalExceptionHttpStatusTest`, `ErrorCodeHttpStatusTest` | 7 | Business errors use the mapped HTTP status while preserving the JSON error envelope. |
| SSE protocol | `ChatControllerStreamingTest` | 5, 7 | Session-cookie authentication works; token/done frames remain valid; committed streams use an error event. |
| Teacher WebSocket | `WebSocketAuthInterceptorTest`, `WebSocketSessionLifecycleTest` | 6 | Teacher identity comes from the authenticated HTTP session; logout, revocation, or expiry closes every associated socket. |
| Student WebSocket | `WebSocketAuthInterceptorTest`, `LiveMessageResourceScopeTest` | 6 | Classroom tokens, destinations, and referenced interaction/QA resources cannot cross the token-bound classroom. |

## Required migration integration tests

The following tests require Spring Session Redis and run in CI under the
`integration` tag:

- Login creates a Redis session and rotates the session ID. (`SessionSecurityIntegrationTest`)
- `/api/auth/me` restores the authenticated principal from the session. (`SessionSecurityIntegrationTest`)
- Logout deletes the session and invalidates the cookie immediately. (`SessionSecurityIntegrationTest`)
- A disabled user has all indexed sessions revoked. (`SessionSecurityIntegrationTest`)
- Session expiry closes or invalidates the associated teacher WebSocket. (`WebSocketSessionLifecycleTest`, `SessionSecurityIntegrationTest`)
- A second application instance can load a session created by the first. (`SessionSecurityIntegrationTest`)
- Redis unavailability returns a controlled 503 instead of a partial login.

## Build gates

The backend gate for every migration phase is:

```shell
mvn test
```

Infrastructure-backed tests remain tagged `integration` and run separately in
CI with PostgreSQL and Redis services. The final migration gate must also run
the frontend type check and production build after all legacy token references
have been removed.
