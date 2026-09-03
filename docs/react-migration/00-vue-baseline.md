# Stage 0: Vue behavior baseline

## Purpose

This document is the executable contract for the React migration. A React page
is not considered migrated merely because it renders the same fields: its
routing, authentication, error handling, network protocol, cleanup behavior,
and important user flows must also match this baseline.

## Recorded baseline

- Repository commit: `d11bf1a` (`refactor: simplify project package structure`)
- Vue application: `vue-project/`
- Backend application: `edumind/`
- Vue source files: 58
- Vue single-file components: 21
- Business routes: 17, plus the catch-all redirect
- Test files: 4
- Development URL: `http://localhost:5173`
- Backend URL through the Vite proxy: `http://localhost:8080`

The working tree contained an unrelated, pre-existing modification to
`edumind/src/main/java/com/firedemo/edumind/assistant/chat/ChatHistoryService.java`
when this baseline was recorded. The React migration must not overwrite or
include that change accidentally.

## Build and quality commands

Run from `vue-project/`:

```bash
npm run format:check
npm run type-check
npm run lint
npm run test
npm run build-only
```

The combined local quality gate is:

```bash
npm run ci:check
```

The result observed while recording this baseline is documented in
"Quality-gate result" below.

## Route contract

| Path                      | Name             | Access                 | Layout         | Vue source                                       |
| ------------------------- | ---------------- | ---------------------- | -------------- | ------------------------------------------------ |
| `/`                       | `home`           | Public                 | Full page      | `features/homework/views/StudentSubmit.vue`      |
| `/login`                  | `login`          | Guest only             | Full page      | `features/auth/views/LoginView.vue`              |
| `/register`               | `register`       | Guest only             | Full page      | `features/auth/views/RegisterView.vue`           |
| `/teacher/chat`           | `chat`           | Teacher session        | Teacher layout | `features/assistant/views/AIChat.vue`            |
| `/teacher/docs`           | `docs`           | Teacher session        | Teacher layout | `features/knowledge/views/KnowledgeBase.vue`     |
| `/teacher/classes`        | `classes`        | Teacher session        | Teacher layout | `features/classroom/views/ClassList.vue`         |
| `/teacher/classes/:id`    | `classManage`    | Teacher session        | Teacher layout | `features/classroom/views/ClassManage.vue`       |
| `/teacher/tasks`          | `tasks`          | Teacher session        | Teacher layout | `features/homework/views/TaskManage.vue`         |
| `/teacher/tasks/:id`      | `taskDetail`     | Teacher session        | Teacher layout | `features/homework/views/TaskDetail.vue`         |
| `/view/submission/:id`    | `submissionView` | Teacher session        | Full page      | `features/homework/views/SubmissionView.vue`     |
| `/teacher/data`           | `data`           | Teacher session        | Teacher layout | `features/teaching/views/Dashboard.vue`          |
| `/teacher/live/:classId`  | `liveTeacher`    | Teacher session        | Teacher layout | `features/live/views/LiveDashboard.vue`          |
| `/live/join`              | `liveJoin`       | Public                 | Full page      | `features/live/views/LiveJoin.vue`               |
| `/live/:sessionCode`      | `liveStudent`    | Public/classroom token | Full page      | `features/live/views/StudentLive.vue`            |
| `/teacher/pre-lesson`     | `preLesson`      | Teacher session        | Teacher layout | `features/teaching/views/PreLessonDashboard.vue` |
| `/teacher/preview/create` | `previewCreate`  | Teacher session        | Teacher layout | `features/teaching/views/PreviewTaskCreate.vue`  |
| `/preview/:taskId`        | `previewView`    | Public                 | Full page      | `features/teaching/views/PreviewTaskView.vue`    |
| `/:catchAll(.*)*`         | `not-found`      | Public                 | N/A            | Redirects to `/`                                 |

### Route behavior that must be preserved

1. A protected route first waits for the `/auth/me` session probe.
2. An unauthenticated teacher route redirects to `/login?redirect=<fullPath>`.
3. An authenticated user visiting `/login` or `/register` redirects to
   `/teacher/chat`.
4. Student pages do not require a teacher session.
5. `/view/submission/:id` is authenticated but intentionally does not use the
   teacher navigation layout.
6. Leaving any route while the AI is responding asks for confirmation. Refusing
   the confirmation cancels navigation; accepting it allows the response to be
   interrupted and navigation to continue.
7. Unknown routes redirect to `/`.

## Authentication and CSRF contract

EduMind uses a server-side teacher session, not a browser JWT.

### Session behavior

- All Axios API requests use `baseURL: /api` and `withCredentials: true`.
- Application startup and protected navigation probe `GET /api/auth/me`.
- Login calls `POST /api/auth/login` and stores the returned user in memory.
- The optional chat `sessionId` is stored in `localStorage.sessionId`; it is not
  the teacher authentication credential.
- Logout calls `POST /api/auth/logout`, clears the in-memory user, and removes
  `localStorage.sessionId`.
- Legacy `token`, `user`, and `auth:user` local-storage entries are removed.

### CSRF behavior

- Unsafe methods are all methods other than `GET`, `HEAD`, `OPTIONS`, and
  `TRACE`.
- Before an unsafe request, the client obtains a token from
  `GET /api/auth/csrf`.
- Unsafe requests send the token in `X-XSRF-TOKEN`.
- A `403` response with application code `40301` refreshes the token and retries
  the original request exactly once.
- A general `401` redirects to `/login`, except for the `/auth/me` probe,
  explicit login/register attempts, and public student live routes.

## Core behavior flows

### Teacher authentication

```text
Open protected route
-> probe /auth/me
-> redirect to /login when no session
-> submit credentials
-> receive session cookie and user
-> preserve optional chat sessionId
-> return to the original protected route
```

### Classroom management

```text
Open class list
-> load teacher classes and courses
-> create/edit/archive/delete a class
-> open /teacher/classes/:id
-> load class and students
-> import an XLSX roster or remove a student
-> create/display an invitation QR code
```

### AI chat

```text
Load chat sessions, teacher classes, and accessible knowledge bases
-> create/select a chat session
-> send text or image input with class/knowledge-base context
-> receive SSE frames incrementally
-> update the partial assistant response
-> finish, fail, or abort the response
-> prevent accidental route changes while streaming
```

### Knowledge base

```text
Load owned/joined knowledge bases and a directory tree
-> create folders or upload documents
-> rename/delete/move tree nodes
-> preview processed content
-> manage members and invitations
-> generate preview/question materials from a document
-> save or delete generated materials
```

### Homework

```text
Teacher creates a task or draft
-> optionally edits rich text and questions
-> publishes to one or more classes
-> student binds identity and submits answers/files
-> result page polls until grading completes
-> teacher reviews task and submission details
```

### Live classroom

```text
Teacher creates or resumes a class session
-> teacher and students connect through STOMP
-> presence and question state are hydrated through REST
-> live events merge into local state
-> teacher sends/extends/closes interactions
-> students answer, ask questions, react, or raise hands
-> leaving the route deactivates the STOMP client
```

## Streaming and realtime protocol contract

### AI SSE

- Text endpoint: `POST /api/chat/stream`
- Multimodal endpoint: `POST /api/chat/multimodal/stream`
- Request credentials: `same-origin`
- Required response type: `text/event-stream`
- Unsafe requests include the CSRF header.
- A single event can arrive across multiple network chunks.
- An event can contain multiple `data:` lines.
- Cancellation uses `AbortSignal`/`AbortController` and must not be presented as
  a normal AI failure.
- Nginx buffering and gzip remain disabled for both streaming endpoints.

### Live STOMP/WebSocket

- WebSocket endpoint: `/ws/live`
- Reconnect delay: 5 seconds
- Incoming/outgoing heartbeat: 10 seconds
- Teacher connection header: `X-Session-Id`
- Student connection header: `Authorization: Bearer <classroomToken>`

Teacher subscriptions:

```text
/topic/session/{id}/stats
/topic/session/{id}/interaction
/topic/session/{id}/interaction-timing
/topic/session/{id}/qa
/topic/session/{id}/students
/topic/session/{id}/reactions
/topic/session/{id}/hand-queue
```

Student subscriptions:

```text
/topic/session/{id}/interaction
/topic/session/{id}/interaction-timing
/topic/session/{id}/hand-queue
/topic/session/{id}/teacher-status
```

The React implementation must have one active STOMP client per live page,
reload server state after reconnecting, and deactivate the client on unmount.

## Security invariants

1. Never render raw AI, document, or student-provided HTML without sanitizing it.
2. Raw Markdown is converted and sanitized before insertion into the DOM.
3. KaTeX output permits the minimum MathML/layout attributes required for
   rendering while still removing event handlers.
4. Teacher authentication remains cookie based.
5. Student classroom tokens remain separate from teacher sessions.
6. The React migration must not weaken the current Nginx CSP, WebSocket proxy,
   SSE buffering rules, or upload-size limit.

## Page complexity and migration risk

| Page                                    | Approximate lines | Primary risk                                           |
| --------------------------------------- | ----------------: | ------------------------------------------------------ |
| `KnowledgeBase.vue`                     |             2,003 | Tree operations, uploads, sharing, generated materials |
| `LiveDashboard.vue`                     |             1,456 | STOMP lifecycle and many concurrent live states        |
| `PreLessonDashboard.vue`                |             1,101 | Multiple async datasets and derived presentation state |
| `TaskManage.vue`                        |             1,095 | Large forms, drafts, questions, and rich text          |
| `ClassList.vue`                         |             1,067 | CRUD, courses, filters, invitations                    |
| `AIChat.vue`                            |             1,061 | SSE, cancellation, Markdown, session state             |
| `StudentLive.vue`                       |               982 | Student token, realtime subscriptions, timers          |
| `Dashboard.vue` plus `dashboardPage.js` |   More than 2,000 | Legacy Options API model and ECharts lifecycle         |

This risk ordering is why the React migration starts with authentication and a
classroom vertical slice before AI streaming, knowledge-base trees, and live
WebSocket state.

## Framework-independent code to port first

The following modules are expected to move with little or no behavioral change:

- `shared/api/errors.ts`
- `shared/api/types.ts`
- `shared/utils/safeHtml.ts`
- `features/assistant/api/chat.ts`
- `features/homework/api/tasks.ts`
- `features/live/api/live.ts`
- `features/knowledge/model/types.ts`
- `features/knowledge/model/tree.ts`
- `features/knowledge/model/presentation.ts`
- `shared/editor/MathNode.ts`

Pinia stores, Vue Router configuration, Vue single-file components, Element
Plus components, and scoped-style behavior must be redesigned for React rather
than copied mechanically.

## Visual acceptance checklist

For each migrated route, compare the Vue and React applications against the
same backend data and viewport:

- page title and navigation state;
- loading, empty, success, and error states;
- form validation and disabled/pending states;
- confirmation dialogs for destructive actions;
- table columns, filters, pagination, and responsive overflow;
- upload progress and failure recovery;
- keyboard behavior and focus after dialogs;
- Markdown, code, math, and rich-text rendering;
- cleanup after route changes;
- browser console and network errors.

Pixel-perfect parity is not required during the feature migration. Behavioral,
security, and protocol parity are required. Visual redesign happens only after
the React core version is complete.

## Quality-gate result

Executed on 2026-08-03 with Node.js `v24.15.0`:

```text
PASS  prettier --check src/
PASS  vue-tsc --build
PASS  oxlint (0 warnings, 0 errors)
PASS  eslint
PASS  vitest (4 files, 19 tests)
PASS  vite production build (2,432 modules transformed)
```

The combined `npm run ci:check` command exited with code `0` in 28.6 seconds.
The production build emitted a pre-existing chunk-size warning: two generated
JavaScript chunks exceeded 1 MB before gzip. This does not block the migration,
but bundle boundaries and lazy loading must be reviewed before the React
cutover rather than silently accepting the same performance problem.
