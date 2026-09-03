# Stage 4: AI chat and streaming Agent workspace

## Outcome

The React application now owns `/teacher/chat`. The migration placeholder has
been replaced with a complete AI teaching workspace connected to the existing
Spring Boot Agent and chat-session APIs.

The workspace supports:

- persistent session list, search, selection, creation, rename, pin, and delete;
- class, knowledge-base, and Agent-mode context per session;
- historical message loading and per-session message caching;
- text and image multimodal requests;
- POST-based Server-Sent Event streaming;
- stop generation with `AbortController`;
- tool execution status, citations, partial output, errors, and completion;
- safe Markdown and syntax-highlighted code rendering;
- copy, retry, and edit-as-new-branch actions;
- lesson-plan artifacts with preview, editing, and teaching-calendar save;
- SPA navigation blocking and browser-close warnings while a response is active;
- responsive session navigation;
- route-level and feature-level lazy loading.

## Why this stage matters

Classroom management proved ordinary React CRUD. AI chat introduces a different
kind of frontend workload: one request stays alive, produces many events, can be
cancelled, and changes multiple pieces of UI before it completes.

```text
one user action
-> create or reuse a session
-> append user + empty assistant messages
-> open a streaming HTTP response
-> parse arbitrary network chunks
-> reduce typed events into immutable UI snapshots
-> render partial output
-> complete, stop, or fail
```

This is representative of the work expected from an AI-application frontend:
stream lifecycle, cancellation, safe generated content, tool visibility,
context selection, and recovery from partial failure.

## Feature structure

```text
features/assistant/
├─ api/
│  ├─ chatApi.ts
│  └─ chatQueries.ts
├─ components/
│  ├─ SessionSidebar.tsx
│  ├─ ContextHeader.tsx
│  ├─ MessageList.tsx
│  ├─ ChatComposer.tsx
│  └─ ArtifactDrawer.tsx
├─ hooks/
│  ├─ useChatMutations.ts
│  ├─ useChatStream.ts
│  └─ useChatNavigationGuard.ts
├─ model/
│  ├─ types.ts
│  ├─ messages.ts
│  ├─ markdown.ts
│  └─ prompts.ts
├─ store/
│  └─ chatRunStore.ts
└─ pages/
   └─ ChatPage.tsx
```

`ChatPage` coordinates the use case. Components own focused presentation.
Queries and mutations own remote resources. `useChatStreamRunner` owns the
long-running request lifecycle. Pure model functions interpret stream events.

## Why `EventSource` is not used

The backend stream requires:

- an authenticated `POST` request;
- a JSON body or multipart `FormData`;
- an `X-XSRF-TOKEN` header;
- an `AbortSignal`.

The browser `EventSource` API only opens a GET request and does not provide the
required request-body/header control. The correct client is therefore:

```text
fetch(...)
-> Response.body
-> ReadableStreamDefaultReader
-> TextDecoder
-> SSE frame parser
```

This distinction is a common AI frontend interview topic: SSE describes the
response format; it does not require using the `EventSource` browser class.

## SSE framing and network chunks

A network read is not guaranteed to contain one complete event. One JSON value
can be split anywhere:

```text
chunk 1: event: token\ndata: {"content":"你
chunk 2: 好"}\n\nevent: done\ndata: {}
```

The parser maintains a buffer, decodes UTF-8 incrementally, and only dispatches
after finding a blank-line frame boundary. It also joins multiple `data:` lines,
uses `message` as the default event name, and flushes the final unterminated
frame when the stream closes.

The supported backend events include:

| Event            | React behavior                                  |
| ---------------- | ----------------------------------------------- |
| `session`        | compatible with server-created session metadata |
| `run_started`    | accepted as lifecycle metadata                  |
| `token`          | append content to the current assistant message |
| `tool_started`   | append a running tool step                      |
| `tool_completed` | finish the latest matching tool step            |
| `citation`       | append one deduplicated knowledge citation      |
| `artifact`       | open a lesson-plan result                       |
| `done`           | allow the request to complete                   |
| `error`          | dispatch the event and reject the stream        |

## CSRF and session expiry

Streaming uses `fetch`, so it does not automatically pass through the Axios
interceptors. `chatApi.ts` explicitly preserves the same security behavior:

```text
obtain CSRF token
-> POST stream with session cookie + X-XSRF-TOKEN
-> if 403 / code 40301
-> force-refresh CSRF token
-> retry exactly once
```

A streaming 401 invokes the same unauthorized handler used by Axios. That
handler clears the authentication cache and redirects to login with the current
internal path preserved.

The login response's legacy local-storage `sessionId` is not treated as the
teacher credential. Chat sessions come from `/api/chat/sessions`; teacher
authentication continues to use the secure server cookie.

## Immutable streaming updates

Vue's reactive proxy allowed the old page to mutate a message directly:

```text
assistant.content += token
assistant.toolSteps.push(step)
```

React state must be treated as immutable. The stream reducer instead creates a
new path to the changed message:

```text
previous message array
-> map to a new array
-> clone the matching assistant message
-> append token/tool/citation into a new value
-> Query Client publishes the new session-message snapshot
-> React renders
```

The reducer is a pure function. It has no DOM, network, Query Client, or React
dependency, so token/tool/citation behavior can be unit-tested independently.

## Why messages live in the Query cache

Each session message list represents a server resource:

```text
['assistant', 'messages', sessionId]
```

History is fetched into that key. While a request is active, the same cached
snapshot temporarily contains the pending user/assistant pair and partial Agent
events. This provides:

- one message snapshot per session;
- instant switching back to a recently viewed session;
- no second local copy that can drift from Query data;
- a single immutable update function for historical and streaming messages.

The session list, messages, and context use these keys:

```text
['assistant']
├─ ['assistant', 'sessions']
├─ ['assistant', 'messages', sessionId]
└─ ['assistant', 'context']
```

Session mutations update or remove the matching cache entries. A completed
stream invalidates the session list so the generated title and `updatedAt`
ordering come back from the backend.

## Custom stream hook

`useChatStreamRunner` encapsulates the request state machine:

```text
idle
-> responding
   -> complete
   -> stopped
   -> error
-> idle
```

It owns the active `AbortController`, local message IDs, Query cache updates,
connection state callbacks, error normalization, and unmount cleanup. The page
only decides which session, prompt, mode, and optional file should be sent.

This is a useful custom-Hook boundary: it reuses React capabilities and owns a
cohesive lifecycle, rather than existing only to shorten a component.

## Zustand scope

Only the cross-cutting `responding` boolean is placed in Zustand. Sessions and
messages are not duplicated there.

The global flag is consumed by:

- the composer, which switches Send to Stop;
- session/context controls, which must not change mid-run;
- the navigation guard;
- stream cleanup.

This demonstrates a practical store rule: use global client state only when
multiple distant consumers need it. Do not create a store merely because a
library is installed.

## Cancellation and navigation protection

Clicking Stop aborts the active fetch. The partial message remains visible and
is marked `stopped`; it is not discarded.

React Router's `useBlocker` handles in-app navigation:

```text
navigation requested while responding
-> native confirmation
-> Cancel: blocker.reset()
-> Leave: AbortController.abort() + blocker.proceed()
```

`useBeforeUnload` separately covers refresh, closing the tab, or leaving the
site. SPA blocking and browser-document blocking are different mechanisms and
both are required.

## Safe Markdown and code highlighting

Partial output is rendered as plain text while streaming. Parsing incomplete
Markdown on every token is wasteful and can cause code blocks to repeatedly
change structure.

After completion, stop, or error:

```text
Agent Markdown
-> Marked
-> highlight.js for registered languages
-> DOMPurify
-> dangerouslySetInnerHTML
```

`dangerouslySetInnerHTML` is safe here because the generated HTML is sanitized
at the final boundary. Script tags, event handlers, forms, iframes, and other
forbidden markup are removed. User messages, tool labels, filenames, and
citations remain ordinary React text nodes.

Only C, C++, Java, JavaScript, JSON, Python, and TypeScript highlighters are
registered. Importing the entire language catalog would increase the route
bundle for languages this teaching application does not need.

## Image lifecycle

Image messages use multipart `FormData`; the browser supplies its boundary, so
the code intentionally does not set `Content-Type` manually.

The preview uses `URL.createObjectURL(file)`. Its previous URL is revoked when a
file changes, clears, sends, or the page unmounts. This avoids retaining browser
blob memory for abandoned previews.

Only image MIME types are accepted. Documents continue through the knowledge
base flow, where parsing, indexing, and access control already exist.

## Tool calls, citations, and artifacts

Tool lifecycle events are rendered before the assistant answer so a teacher can
see when the Agent searches knowledge, reads class status, or invokes another
teaching capability. Completion updates the latest running step for the same
tool and records its elapsed time.

Citations are deduplicated by document and section and link to the knowledge
base route.

Lesson-plan mode restores an artifact from the last assistant answer and also
accepts the backend `artifact` event. The drawer supports sanitized preview,
local editing, copy, and saving a calendar entry through the existing dashboard
API.

## Lazy-loading result

There are two code-splitting boundaries:

```text
/teacher/chat route
-> ChatPage chunk
   -> open “成果”
      -> ArtifactDrawer + DatePicker/dayjs chunk
```

After the nested boundary, the main `ChatPage` module is approximately 26.43 kB
(10.31 kB gzip). The optional artifact module is approximately 135.76 kB
(43.58 kB gzip), and Markdown/highlighting is a shared approximately 120.10 kB
chunk. The largest generated JavaScript chunk is below 500 kB, so the previous
Vite large-chunk warning is no longer emitted.

## Tests

Tests cover:

- SSE frames split across arbitrary network chunks;
- multiline and non-JSON `data:` payloads;
- error-event dispatch followed by rejection;
- expired-CSRF refresh and exactly-one stream retry;
- token concatenation and tool lifecycle;
- immutable previous-message snapshots;
- citation deduplication and lesson artifacts;
- preserving partial output after cancellation;
- code highlighting plus script/event-handler sanitization;
- loading sessions, context, and Markdown history;
- creating a session and rendering streamed tokens;
- blocking navigation while streaming, cancelling the first navigation, and
  aborting/proceeding after confirmation;
- all previous authentication, classroom, security, and architecture behavior.

## Verification

The final React quality gate completed successfully:

```text
PASS  prettier --check .
PASS  tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (11 files, 32 tests)
PASS  vite production build (1,740 modules)
PASS  npm audit --audit-level=high (0 vulnerabilities)
```

No Spring Boot source was changed in this stage. Automated network tests use
MSW with the actual endpoint shapes and streaming response format. PostgreSQL,
Redis, and the configured LLM service were not started for a manual end-to-end
generation run.

## Stage boundary

Stage 4 consumes knowledge bases as selectable Agent context but does not
migrate knowledge-base management. Stage 5 replaces `/teacher/docs` with the
resource tree, uploads, directory operations, sharing, previews, and generated
materials.
