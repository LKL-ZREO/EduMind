# Stage 7: teaching intelligence and pre-lesson workflows

## Outcome

The React application now owns the teaching workflow that turns classroom data
into the next lesson:

```text
dashboard metrics + homework errors + QQ/live confusion signals
-> identify weak knowledge points and at-risk students
-> inspect an individual student trend
-> generate a targeted teaching plan
-> prepare the next lesson from evidence
-> generate and share a public preview task
-> student reads, self-tests, and brings a discussion question to class
```

The migrated routes are:

| Route                     | Audience | Capability                                               |
| ------------------------- | -------- | -------------------------------------------------------- |
| `/teacher/data`           | teacher  | metrics, ECharts, knowledge maintenance, student insight |
| `/teacher/pre-lesson`     | teacher  | evidence-driven lesson preparation and local autosave    |
| `/teacher/preview/create` | teacher  | AI preview generation, sharing, listing, and closing     |
| `/preview/:taskId`        | public   | preview reading, local self-test, and explanations       |

The Vue application remains available as the behavior reference. No Spring
Boot endpoint or data contract was changed.

## Why this stage matters

The earlier stages introduced route protection, CRUD, streaming, recursive
knowledge resources, rich text, uploads, and asynchronous grading. Teaching is
where those pieces become an analytical product instead of separate screens.

This stage has four React problems that occur frequently in production AI
applications:

- a dashboard composes several independently cached server resources;
- ECharts is an imperative library and must obey the React lifecycle;
- an editable lesson draft is a client transaction that survives refreshes;
- AI-generated HTML or Markdown crosses a security boundary before rendering.

It also introduces two forms of eventual consistency: AI generation can take a
long time, and historical error reclassification continues in the background
after a teacher changes the knowledge taxonomy.

## Feature structure

```text
features/teaching/
├─ api/
│  ├─ teachingApi.ts
│  └─ teachingQueries.ts
├─ components/
│  ├─ DashboardCharts.tsx
│  └─ StudentInsightModal.tsx
├─ hooks/
│  └─ useTeachingMutations.ts
├─ model/
│  ├─ types.ts
│  ├─ dashboard.ts
│  └─ lesson.ts
└─ pages/
   ├─ DashboardPage.tsx
   ├─ PreLessonPage.tsx
   ├─ PreviewCreatePage.tsx
   └─ PreviewTaskPage.tsx

shared/charts/
└─ EChart.tsx
```

API calls know HTTP and Result envelopes. Query option factories know cache
keys and freshness. Mutation hooks know invalidation. Pure models know business
derivations. Components and pages know interaction and presentation.

## Composing server state with TanStack Query

The dashboard is not one large endpoint. Metrics, score distribution,
knowledge mastery, frequent errors, students, and confusion signals have
different meanings and can be refreshed independently.

Each resource receives a hierarchical key:

```text
['teaching', 'dashboard', classId, 'metrics']
['teaching', 'dashboard', classId, 'distribution']
['teaching', 'dashboard', classId, 'knowledge']
['teaching', 'dashboard', classId, 'errors', knowledgePoint]
['teaching', 'dashboard', classId, 'students']
['teaching', 'dashboard', classId, 'confusions']
```

Changing the selected class changes `classId`, so React renders new query keys
and TanStack Query fetches or reuses the correct cached data. The page does not
manually empty six state variables or coordinate six loading flags in an
Effect.

The common prefix makes targeted invalidation possible:

```ts
queryClient.invalidateQueries({
  queryKey: teachingKeys.dashboard(classId),
});
```

One prefix refreshes all dashboard descendants after a background
reclassification finishes.

## Parallel data without one artificial mega-request

React calls several `useQuery` hooks during the same render. Enabled queries
begin independently and naturally run in parallel. A failure in live confusion
signals does not need to erase already loaded student metrics.

The `getConfusionSignals` API function intentionally uses `Promise.all` because
three backend endpoints together form one UI resource:

- QQ confusion events;
- QQ confusion counts;
- live-classroom confusion data.

Pure functions then merge counts by knowledge-point name and order events by
time. They return new arrays and never sort TanStack Query's cached arrays in
place.

## URL state versus component state

The selected class belongs in `?classId=` because it is useful when refreshing,
bookmarking, or sharing a teacher page. The effective selection is derived
during render:

```text
URL classId exists in fetched classes -> use it
otherwise                         -> use the first available class
```

No Effect copies “first class” into local state. Search text, modal visibility,
quiz answers, and an open student's identity are transient interaction state,
so they stay in component state.

This distinction is useful in interviews:

- URL state represents navigation;
- Query state represents server resources;
- local state represents unfinished user interaction;
- derived values are calculated, not synchronized.

## Pure derived dashboard models

Pass rate, weak knowledge points, “other” error share, filtered students, and
merged confusion signals live in `model/dashboard.ts`. These functions are
testable without React, Ant Design, a router, or a backend.

`filterStudents` copies and sorts a filtered array instead of sorting the
source. Mutating query data in place would make referential equality
unreliable, surprise other consumers of the same cache entry, and make React
updates harder to reason about.

`useMemo` is used only around derivations whose inputs are stable query data or
whose work includes sorting/merging. It is not used for every string or simple
property lookup.

## Wrapping imperative ECharts in a declarative component

ECharts exposes imperative methods:

```text
init(container)
setOption(option)
resize()
dispose()
```

React pages should not repeat those commands. `shared/charts/EChart.tsx`
provides a small declarative adapter:

```tsx
<EChart option={option} ariaLabel="班级成绩分布柱状图" />
```

The adapter uses:

- a DOM ref for the chart container;
- a second ref for the ECharts instance;
- one mount Effect to initialize, observe size, and dispose;
- one option Effect to update the existing instance;
- `ResizeObserver` instead of a global window resize listener;
- a semantic `role="img"` and accessible label.

The cleanup function disconnects the observer and disposes the canvas. This is
essential under route changes and React development Strict Mode, where missing
cleanup quickly exposes duplicate instances and memory leaks.

Only bar chart, line chart, grid, tooltip, legend, mark-line, and canvas modules
are registered from `echarts/core`. The ECharts runtime is emitted as a
dedicated route-lazy chunk. It is not downloaded by login, chat, homework, or
the public preview route.

The runtime chunk is approximately 554.98 kB minified and 187.12 kB gzip. It is
larger than Vite's default warning threshold, but it is isolated behind the
teaching dashboard boundary. That tradeoff is documented rather than hidden by
raising the warning limit.

## Lazy-loading detailed student insight

The dashboard table is common; the detailed student modal is optional. The
modal component is loaded with `React.lazy` only after a teacher selects a
student.

Its query key includes both class and student identity. The line-chart option
is derived from score history. Closing the modal unmounts its chart, and the
shared adapter handles disposal. Reopening the same student can reuse fresh
TanStack Query data while creating a fresh visual instance.

## Mutations, invalidation, and background polling

Knowledge-point additions and batch edits start backend reclassification of
historical errors. The mutation response supplies a task ID. A conditional
query polls that task every two seconds:

```text
PENDING / RUNNING              -> poll again
COMPLETED / COMPLETED_WITH_ERRORS / FAILED -> stop
```

When a terminal state arrives, the dashboard query prefix is invalidated so
knowledge mastery, errors, students, and summary metrics can converge on the
new taxonomy.

This separation is deliberate:

- Mutation means “the teacher requested a change.”
- Query means “what is the current background-task state?”
- Invalidation means “the old derived server resources may now be stale.”

The component stores only the current task ID. It does not copy task progress
into a second local object.

## An editable lesson is a local transaction

Pre-lesson overview and timeline are server resources. The lesson plan being
edited is different: it contains unsaved teacher decisions. It is initialized
once from server evidence, then owned locally as a `LessonDraft`.

The initial state uses a lazy initializer:

```ts
useState(() => restoreLessonDraft(classId, overview, timeline));
```

The parent renders the workspace with `key={classId}`. Changing class identity
therefore creates a new editor with the correct initial draft. This avoids an
Effect that would reset topic, stages, objectives, materials, and notes after a
render, which could briefly expose or overwrite the previous class's state.

## Effects are for external synchronization

The lesson editor uses an Effect only to synchronize with `localStorage`, an
external browser system:

```text
draft changes
-> cancel previous timer
-> wait 700 ms
-> persist class-scoped JSON
-> update the saved-at indicator
```

Readiness percentage, total minutes, evidence signals, and export text are
derived from the current draft. They are not stored in state and updated by
Effects.

This is the practical rule:

> If a value can be calculated from current props and state during render,
> calculate it. Use Effects to synchronize with something outside React.

## Immutable nested lesson editing

A lesson contains objectives, stages, material flags, and differentiation
rules. Updates replace only the changed path:

```text
new draft object
-> new stages array
-> new object for the edited stage
-> other stage references remain unchanged
```

Stage reordering copies the array before `splice`. Stable stage IDs are React
keys, so moving a stage does not confuse it with the stage previously at that
array index.

The readiness model validates course metadata, objectives, evidence, complete
teacher/student actions, total duration, and materials. It is pure and can be
unit-tested independently of the form.

## Teacher preview mutation and public preview query

Preview creation is a user-triggered command, so it uses a mutation with a
180-second HTTP timeout inherited from the Vue behavior. Successful creation
invalidates the selected class's preview list.

The student route is public and uses only the route task ID:

```text
/preview/88
-> query key ['teaching', 'preview', 88]
-> render guide, questions, and discussion prompt
```

Quiz choices and whether the student has submitted are local UI state. They do
not belong in TanStack Query because the backend does not persist them. Answer
updates use an immutable record keyed by question index.

## AI content security

Teaching-plan HTML and preview Markdown can contain model-produced content.
Both pass through the shared DOMPurify boundary before
`dangerouslySetInnerHTML`.

The public-route test deliberately includes a `<script>` in the mocked guide
and verifies that the rendered DOM contains no script element. The name
`dangerouslySetInnerHTML` is a reminder that React escaping has been bypassed;
sanitization must happen immediately before that boundary.

The project installed ECharts 6.0.0 initially, but `npm audit` reported a
moderate XSS advisory affecting releases below 6.1.0. The dependency was
upgraded and pinned to 6.1.0 before delivery. The final moderate-level audit is
clean.

## Tests

Stage 7 adds coverage for:

- pass-rate and attention-list derivation;
- filtering without mutating cached student arrays;
- weak knowledge and “other” error-rate calculations;
- merging QQ and live-classroom confusion signals;
- deterministic lesson initialization and local restoration;
- local-date rollover without UTC date drift;
- readable lesson-plan export;
- one ECharts initialization, option updates, and unmount disposal;
- dashboard rendering from independently mocked endpoints;
- pre-lesson draft autosave;
- public preview quiz interaction;
- removal of a malicious script from AI guide content;
- every earlier auth, classroom, chat, knowledge, homework, and architecture
  test.

Testing Library's shared asynchronous wait is now three seconds. As the lazy
route suite grew, the former one-second browser-simulation default could expire
under full parallel CI load even though the same chat test passed alone. The
test case limit remains ten seconds.

## Verification

The final quality gate completed successfully:

```text
PASS  prettier --check
PASS  tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (21 files, 60 tests)
PASS  vite production build (2,438 modules)
PASS  npm audit --audit-level=moderate (0 vulnerabilities)
```

No backend source was changed. MSW verified Result envelopes and endpoint
paths, but PostgreSQL, Redis, MinIO, QQ/NapCat, and the configured LLM were not
started for manual end-to-end data generation.

## Stage boundary

Stage 7 completes the HTTP-based teaching workflows. Stage 8 will migrate live
classroom teacher/student routes, token handling, STOMP connection lifecycle,
presence, interactions, and reconnect behavior.
