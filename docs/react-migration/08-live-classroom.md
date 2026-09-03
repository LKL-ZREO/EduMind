# Stage 8: real-time classroom and STOMP lifecycle

## Outcome

The React application now owns the complete live-classroom workflow:

```text
teacher opens a class
-> restore an active session or create a new one
-> hydrate the HTTP question/attendance/history snapshot
-> connect and subscribe over STOMP
-> share the six-character code or QR code
-> student previews the session and confirms device identity
-> connect with a session-scoped student token
-> receive questions, answer, ask, react, raise a hand, or mark confusion
-> teacher receives presence, statistics, questions, reactions, and hand queue
-> reconnect and re-subscribe after a transient connection loss
-> end the classroom and release every subscription and timer
```

The migrated routes are:

| Route                    | Audience | Capability                                        |
| ------------------------ | -------- | ------------------------------------------------- |
| `/live/join`             | public   | normalized six-character classroom-code entry     |
| `/live/:sessionCode`     | student  | identity, questions, responses, feedback, history |
| `/teacher/live/:classId` | teacher  | session, question board, presence, statistics, QA |

The class-detail page now contains the normal entry into the teacher route. No
Spring Boot HTTP endpoint, STOMP destination, or security rule was changed.

## Why this stage matters

An HTTP page requests a resource, receives a response, and finishes. A
real-time page owns a long-lived external process:

- the socket can be connecting, connected, disconnected, and reconnecting;
- a successful reconnect creates a new broker session and needs new
  subscriptions;
- events arrive outside React's event handlers;
- teacher and student identities have different permissions;
- the initial HTTP snapshot can race with live increments;
- timers and subscriptions must stop when identity, session, or route changes;
- user input must survive a reconnect without being mistaken for server state.

Those problems appear in AI streaming, collaborative editors, monitoring
dashboards, games, chat, and notification systems. This stage therefore adds a
strong interview-quality example beyond ordinary React CRUD.

## Feature structure

```text
features/live/
├─ api/
│  └─ liveApi.ts
├─ hooks/
│  └─ useLiveSocketLifecycle.ts
├─ model/
│  ├─ types.ts
│  └─ live.ts
├─ realtime/
│  └─ liveSocket.ts
├─ store/
│  └─ liveStore.ts
└─ pages/
   ├─ LiveJoinPage.tsx
   ├─ StudentLivePage.tsx
   └─ TeacherLivePage.tsx
```

The layers have separate jobs:

- `liveApi.ts` owns HTTP commands, Result envelopes, and classroom bearer
  headers;
- `liveSocket.ts` owns the imperative STOMP client;
- `useLiveSocketLifecycle.ts` connects that client to React mount/unmount;
- `liveStore.ts` reduces external events into a shared classroom snapshot;
- `model/live.ts` contains pure normalization, merge, countdown, and formatting
  logic;
- pages own transient form state and user intent.

## HTTP snapshot plus STOMP increments

The teacher does not connect before the initial snapshot is ready. The startup
sequence is:

```text
GET active session
-> reuse active session or POST create session
-> Promise.all(history, question board, presence, active statistics)
-> write one coherent Zustand snapshot
-> render with session identity
-> lifecycle hook opens STOMP
```

This order prevents an older HTTP response from overwriting a newer event that
arrived while hydration was still in flight.

After hydration, HTTP remains appropriate for commands that need a canonical
response, such as sending a question or extending its deadline. STOMP carries
the fan-out updates to every connected participant.

The two transports are complementary:

```text
HTTP = command acknowledgement and refreshable snapshot
STOMP = low-latency incremental broadcast
```

## Why Zustand owns the live snapshot

TanStack Query continues to own ordinary request/response resources such as an
expanded response detail and confusion polling. The active live session is
different: events originate from a socket callback outside the component tree
and several distant panels must update atomically.

Zustand stores:

- role and session identity;
- connection status;
- current interaction;
- question board and interaction history;
- online and absent students;
- live statistics;
- anonymous questions and reactions;
- hand queue;
- teacher online and session-ended status.

It does not store the student's unfinished text answer, the teacher's modal
visibility, board filter, or a temporary QA reply. Those remain local UI state.

This is not “put everything in a global store.” It is one store for one
external session boundary.

## The STOMP adapter

Pages never construct `@stomp/stompjs` clients. The adapter receives:

```ts
{
  (role, sessionId, token, onStatus, onEvent);
}
```

It creates the correct URL from the current page protocol:

```text
http:  -> ws://host/ws/live
https: -> wss://host/ws/live
```

Every connection includes:

```text
X-Session-Id: <live session id>
```

Student connections additionally include:

```text
Authorization: Bearer <classroom token>
```

The teacher is authenticated by the existing login session. The student token
is scoped by the backend to one student and one live session; it is not a
teacher access token.

## Role-specific subscriptions

Both roles subscribe to interaction changes, deadline changes, and hand-queue
state. Teacher-only subscriptions include:

- statistics;
- anonymous QA;
- student presence;
- reactions.

The student subscribes to teacher status instead, including the terminal
`sessionEnded` event.

This client separation mirrors the backend authorization interceptor. It is a
UX optimization, not the security boundary: the backend still rejects a role
that attempts to subscribe or publish to a forbidden destination.

## Reconnect and re-subscribe

The STOMP client uses:

```text
reconnect delay:     5 seconds
incoming heartbeat: 10 seconds
outgoing heartbeat: 10 seconds
```

`onWebSocketClose`, `onWebSocketError`, and `onStompError` move the UI to a
disconnected state. The student is told not to refresh. When STOMP reconnects,
`onConnect` runs again and creates a fresh set of subscriptions for the same
session.

Subscriptions belong to a broker connection and cannot be assumed to survive a
network reconnect. Re-subscribing in `onConnect` is therefore a correctness
requirement.

The adapter also ignores callbacks from a client that is no longer the active
instance. This prevents a late close callback from an old connection from
marking a newer connection as disconnected.

## React lifecycle and cleanup

`useLiveSocketLifecycle` is the only React-to-STOMP bridge. Its Effect depends
on role and session identity:

```text
valid role + session
-> connect
-> receive status/events into Zustand

cleanup
-> deactivate STOMP
```

The page initialization Effect separately guards asynchronous HTTP work with a
`cancelled` flag. A response that finishes after unmount does not start a
socket, replace store state, or update local loading UI.

Teacher and student route wrappers render keyed session components. A changed
class ID or classroom code therefore creates a new lifecycle boundary instead
of trying to reset a large live session from a synchronization Effect.

These cleanup rules are especially important in React development Strict Mode,
where mount/cleanup behavior exposes duplicated external subscriptions.

## Event reduction and immutable state

Every parsed STOMP frame becomes a typed event:

```text
stats | interaction | timing | qa | presence |
reaction | handQueue | teacherStatus
```

The Zustand store reduces those events into new arrays and objects. An
interaction push updates both the logical history item and the matching
question-board card. A statistics event updates response count, distribution,
correct rate, and status in both views.

Stage testing caught a real integration defect here: the pure merge helper
returned `{ history, board }`, while the store fields are named
`interactionHistory` and `questionBoard`. The connection test alone could not
catch it; a store-event test proved that the final UI snapshot changed. The
store now maps the helper result explicitly.

That is an important testing lesson: verify observable state transitions, not
only whether a callback was invoked.

## Absolute deadlines instead of decrementing truth

The backend sends `deadlineEpochMs`. The client derives:

```text
remaining = ceil((deadlineEpochMs - Date.now()) / 1000)
```

The deadline is the authoritative value. The interval updates only the local
clock used for rendering. It does not decrement and persist a second “remaining
seconds” truth that can drift after a suspended browser tab or network delay.

The teacher updates once per second. The student updates four times per second
for smoother progress feedback. Both intervals clean up when no timed active
question exists or the component unmounts.

## Student answers keyed by interaction ID

An answer draft is stored as:

```ts
Record<interactionId, answer>;
```

Changing the current question naturally selects a different entry. There is no
Effect that clears `answer` whenever `currentInteraction` changes.

The displayed value is derived in priority order:

```text
current local draft
-> locally submitted answer
-> hydrated history answer
-> empty string
```

This preserves a student's input during reconnect, supports changing an answer
before the deadline, and restores an earlier server answer after refresh.

## Device identity and classroom token

The student flow first previews a classroom without joining. It then tries:

```text
quick join using secure device cookie
-> legacy remembered student ID fallback
-> explicit student-ID form
```

After a successful join, the returned classroom token is used for:

- the STOMP `CONNECT` header;
- student-scoped interaction history;
- the “I don't understand” AI explanation endpoint.

Switching identity calls the device-unbind endpoint, clears legacy values,
resets the live store, and returns to the identity form. A borrowed device does
not need a browser-storage reset outside the product.

## Teacher workflow

The teacher page supports:

- active-session restore and new-session creation;
- classroom code copy and QR display;
- online and absent lists;
- student profile details;
- random calling;
- hand calling and dismissal;
- unified question board filters;
- question send, quick extension, and early close;
- live response progress and response details;
- confusion aggregation;
- anonymous QA answers;
- reaction display;
- safe report download as an HTML file;
- end-session confirmation and summary.

The report uses a Blob download rather than `document.write` into the current
origin. This avoids executing server-produced report markup inside the main
application page.

## Student workflow

The student page supports:

- code validation and session preview;
- explicit or device-assisted identity confirmation;
- choice, open, and exercise responses;
- answer modification before closing;
- exact countdown and connection state;
- anonymous questions;
- emoji feedback;
- raising and lowering a hand;
- AI explanation for a confusing interaction;
- participation and correctness summary;
- filtered historical review;
- teacher-offline and session-ended states;
- automatic reconnect messaging.

## Tests

Stage 8 adds coverage for:

- classroom-code normalization and ambiguous-character removal;
- immutable interaction/history/board merging;
- countdown and response derivations;
- Zustand interaction and statistics reduction;
- hand-queue identity state;
- terminal teacher-status events;
- `ws:`/`wss:` URL selection;
- student STOMP session and authorization headers;
- reconnect and heartbeat configuration;
- role-specific subscription destinations;
- publish behavior and deactivation cleanup;
- teacher active-session hydration and HTTP question sending;
- student preview, identity confirmation, bearer-scoped history, and answer
  publishing;
- all earlier authentication, classroom, AI chat, knowledge, homework,
  teaching, security, and architecture behavior.

The STOMP test uses a controlled fake client. It verifies headers,
destinations, callbacks, publish frames, and cleanup without requiring a real
broker or relying on timing-sensitive network behavior.

## Route-level bundles

The live-classroom code remains behind route lazy boundaries:

- STOMP lifecycle and shared live runtime: approximately 28.31 kB
  (8.55 kB gzip);
- student live page: approximately 17.78 kB (7.16 kB gzip);
- teacher live page: approximately 34.72 kB (13.16 kB gzip);
- classroom-code entry page: approximately 1.74 kB (0.98 kB gzip).

Users who only visit authentication, chat, knowledge, homework, teaching, or
preview routes do not download STOMP.

## Verification

The final quality gate completed successfully:

```text
PASS  prettier --check
PASS  tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (25 files, 69 tests)
PASS  vite production build (2,448 modules)
PASS  npm audit --audit-level=moderate (0 vulnerabilities)
```

No backend source was changed. HTTP and STOMP behavior were verified through
MSW and a fake STOMP client. A real PostgreSQL/Redis-backed classroom with two
browsers was not started during the automated stage check.

## Stage boundary

Stage 8 completes every product route from the Vue application. Stage 9 is the
cutover: production Docker/Nginx build source, default development commands,
documentation, CI ownership, final parity checks, and deliberate Vue
retirement or archival.
