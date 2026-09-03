# Stage 6: homework authoring, submission, and grading

## Outcome

The React application now owns the complete homework loop:

```text
teacher drafts questions
-> saves the editable draft
-> publishes it to one or more classes
-> student selects the class and task
-> student uploads a conventionally named file
-> backend queues asynchronous AI grading
-> public page polls the grading result
-> teacher views task statistics and the original submission
```

The migrated routes are:

| Route                  | Audience | Capability                                        |
| ---------------------- | -------- | ------------------------------------------------- |
| `/teacher/tasks`       | teacher  | drafts, question bank, rich text, and publishing  |
| `/teacher/tasks/:id`   | teacher  | task metrics, score distribution, and submissions |
| `/`                    | public   | class/task selection, file upload, and grading    |
| `/view/submission/:id` | teacher  | protected source-content view                     |

The Vue application remains runnable, and no Spring Boot contract was changed.

## Why this stage matters

Earlier stages covered ordinary CRUD, recursive resources, and streaming chat.
Homework joins those ideas into one stateful product workflow:

- the teacher edits a local draft that is not yet server truth;
- one publish action can create tasks for several classes;
- public submission has no teacher login but still needs CSRF protection;
- file naming is part of the domain protocol;
- some HTTP errors are recoverable UI states rather than terminal failures;
- grading is asynchronous and eventually consistent;
- generated HTML and formulas must stay safe at the rendering boundary.

This is representative of an AI application frontend because the response is
not immediate: upload acceptance and AI completion are separate events.

## Feature structure

```text
features/homework/
├─ api/
│  ├─ homeworkApi.ts
│  └─ homeworkQueries.ts
├─ components/
│  ├─ TaskLibrarySidebar.tsx
│  ├─ QuestionEditorCard.tsx
│  └─ QuestionBankPanel.tsx
├─ hooks/
│  └─ useHomeworkMutations.ts
├─ model/
│  ├─ types.ts
│  ├─ homework.ts
│  └─ submission.ts
└─ pages/
   ├─ TaskManagePage.tsx
   ├─ TaskDetailPage.tsx
   ├─ StudentSubmitPage.tsx
   └─ SubmissionViewPage.tsx

shared/
├─ editor/MathInlineNode.ts
└─ ui/RichTextEditor.tsx
```

The shared editor is reusable by later teaching features. Homework-specific
state and transformations remain under the homework feature.

## Server state versus an editable snapshot

Draft lists, published tasks, question-bank results, task statistics, and
grading results are server resources, so TanStack Query owns them.

The draft currently open in the editor is different. It may contain unsaved
keystrokes and should not overwrite the shared query cache on every change.
The page therefore creates a local editable snapshot:

```text
saved HomeworkDraft from Query
-> clone questions into DraftEditor state
-> user edits immutable local copies
-> Save mutation
-> backend returns canonical HomeworkDraft
-> replace local snapshot + invalidate draft/question queries
```

This is an important exception to “do not duplicate server state”: a form draft
is intentionally a temporary client-side transaction buffer.

The page stores no second copy of the draft list, task list, or question bank.
Only the resource being actively edited becomes local form state.

## Immutable nested form updates

A homework draft contains an array of question objects. Editing one question
creates a new path to the changed value:

```text
previous draft
-> new draft object
-> new questions array
-> new object for the edited index
-> unchanged question objects retain their references
```

Adding from the question bank clones the source question. Editing the draft
therefore cannot accidentally mutate the cached bank question.

Removing a question uses `filter`; adding uses array spread. At least one
question is retained as a product rule.

## Derived defaults instead of synchronization Effects

Class data arrives asynchronously. The page needs the first class selected by
default but must still allow the teacher to intentionally clear all classes.

The state uses `null` as “the user has not chosen yet” and `[]` as “the user
explicitly selected none”:

```text
selectedClassIds === null
-> derive [firstClass.id]

selectedClassIds === []
-> keep no selection and show validation on publish
```

No Effect copies the first class into state. The first published task is also
derived when no explicit key is selected. Effects remain reserved for external
subscriptions such as the countdown interval.

This follows React's rule: if a value can be calculated during render, do not
store and synchronize another copy of it.

## Save before publish

Publishing must use the latest question text, not the last previously saved
version. The React workflow always performs the operations in order:

```text
validate local form
-> save current editable snapshot
-> receive canonical draft ID and content
-> publish that saved draft to selected class IDs
-> invalidate drafts and all teacher-task queries
```

This also handles a brand-new draft because the create response supplies the ID
needed by the publish endpoint.

The publish button is one user intent, but it coordinates two dependent
mutations. The second mutation never starts if saving fails.

## Grouping multi-class publications

The backend creates one task row per class. A teacher who publishes the same
assignment to three classes should see one logical assignment in the sidebar,
not three nearly identical rows.

The pure `groupTasks` function groups by:

- task name;
- normalized description;
- deadline;
- late-submission policy;
- late penalty.

It then accumulates task IDs and class names. Active groups sort before closed
groups, followed by most recent creation time. The source task array is not
mutated.

## Tiptap as a controlled React adapter

The rich-text editor uses `@tiptap/react`, `StarterKit`, and a placeholder
extension. Its HTML is passed through a small controlled interface:

```ts
value: string
onChange(nextHtml: string): void
```

Tiptap owns its internal ProseMirror document while mounted. A React Effect is
appropriate here because it synchronizes an external editor instance when the
parent selects another question. The latest `onChange` callback is stored in a
ref so Tiptap's long-lived update callback does not capture stale props.

Toolbar commands cover bold, italic, heading, lists, blockquotes, code blocks,
and inline formulas.

All Tiptap packages are pinned to the same version. Different copies of
`@tiptap/core` create nominally similar but TypeScript-incompatible `Editor`
and `Extension` types, so version alignment is a correctness requirement rather
than package-manager housekeeping.

## Custom inline mathematics node

`MathInlineNode` is an atomic Tiptap node serialized as:

```html
<span class="math-inline" data-latex="x^2"></span>
```

Its node view renders KaTeX while editing. Clicking the node lets the teacher
replace the LaTeX attribute. The HTML remains portable because the saved value
contains semantic LaTeX rather than KaTeX's large generated DOM.

On the student page:

```text
saved assignment HTML
-> sanitize source HTML
-> find math-inline[data-latex]
-> render KaTeX
-> sanitize the generated HTML again with MathML allowed
-> render
```

Sanitizing both before and after formula transformation prevents source HTML or
generated markup from bypassing the security boundary.

## Public upload and CSRF

The student route is public, but “public” only means it does not require a
teacher session. A POST still changes server state and uses the shared CSRF
token flow.

The selected file is validated for a 20 MB client limit, then submitted as
`FormData` with:

- `file`;
- `expectedClassId`;
- `expectedTaskId`;
- optional `confirm=true`.

The browser supplies the multipart boundary. Axios upload events provide real
transfer progress; the UI no longer needs a simulated random progress timer.

## Filename protocol and defense in depth

The backend identifies a public student submission from this filename shape:

```text
学号_姓名_班级_作业名称.扩展名
```

The client parses the same four fields and warns about malformed names, class
mismatches, and task mismatches before upload. This gives immediate feedback,
but the backend repeats all validation and remains authoritative.

Client validation improves UX. Server validation protects data integrity.

## Recoverable HTTP outcomes as UI states

Not every rejected submission means “show an error and stop”:

| Business code | Meaning                        | React transition                          |
| ------------- | ------------------------------ | ----------------------------------------- |
| `300`         | filename/selection mismatch    | show details and request explicit confirm |
| `428`         | student must bind QQ first     | open binding modal, then retry submission |
| `429`         | duplicate/rate-limited request | keep file selected and ask user to wait   |

Axios rejects the corresponding non-2xx HTTP responses. The page extracts the
typed Result envelope from the error, interprets the business code, and keeps
the selected file available for recovery.

After successful QQ binding, the original file is resubmitted with explicit
confirmation. A mutation's error is therefore sometimes an input to the next
valid UI state rather than a terminal exception.

## Conditional grading polling

Submitting creates a database record in `PENDING` state and queues Redis Stream
work. The HTTP upload response cannot contain a finished AI grade.

The grading query is keyed by submission ID:

```text
['homework', 'grading', submissionId]
```

Its `refetchInterval` examines the latest query data:

```text
PENDING / PROCESSING -> refetch after 2 seconds
COMPLETED / FAILED   -> return false and stop polling
```

TanStack Query owns timer cleanup, overlapping-request prevention, errors, and
the latest result. The page does not maintain a second manual polling interval
or duplicate grading snapshot.

The deadline countdown is different: it is local time-derived UI, so one
`setInterval` subscription updates the current clock and cleans itself up from
the Effect return function.

## Protected source content

`/view/submission/:id` lives in the public layout so a newly opened tab does not
include the teacher sidebar, but its route loader still requires a teacher
session. The backend independently checks submission ownership.

Submission content is rendered inside `<pre><code>` as a normal React text
node. It is not HTML, so a student's `<script>` text remains visible source text
and cannot execute.

## Score distribution without a chart runtime

The task-detail route displays the five backend score buckets with semantic CSS
bars. This preserves the assignment-detail visualization without loading the
full ECharts runtime for one small chart. Stage 7 will introduce ECharts where
the teaching dashboards have multiple interactive charts and lifecycle reuse
justifies the dependency.

## Route-level bundle boundaries

All four homework pages are route-lazy. The production build emits:

- `StudentSubmitPage`: approximately 11.32 kB (4.80 kB gzip);
- `TaskDetailPage`: approximately 4.40 kB (2.11 kB gzip);
- `SubmissionViewPage`: approximately 1.58 kB (0.87 kB gzip);
- `TaskManagePage` with the Tiptap editor: approximately 427.90 kB
  (135.44 kB gzip);
- shared KaTeX runtime: approximately 258.09 kB (77.26 kB gzip).

The large editor and formula runtime are absent from the application entry
route until a homework feature requests them. The largest JavaScript chunk
remains below 500 kB.

## Tests

The stage tests cover:

- grouping equivalent tasks published to different classes;
- keeping differing deadlines in separate logical groups;
- score totals and title escaping in generated assignment HTML;
- filename parsing and class/task mismatch warnings;
- deterministic deadline formatting from an injected clock;
- KaTeX rendering followed by event-handler sanitization;
- saving the latest editable draft before publishing;
- using the correct selected class IDs in the publish request;
- public file selection, upload, and a completed grading query;
- task metrics and score-distribution rendering;
- authenticated source-submission viewing;
- every previous authentication, classroom, chat, knowledge, security, and
  architecture test.

## Verification

The final React quality gate completed successfully:

```text
PASS  tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (17 files, 50 tests)
PASS  vite production build (1,833 modules)
PASS  npm audit --audit-level=high (0 vulnerabilities)
```

No Spring Boot source was changed. Automated network behavior uses MSW with the
actual Result envelopes and endpoint paths. PostgreSQL, Redis Streams, MinIO,
QQ/NapCat, and the configured LLM service were not started for a manual grading
run.

## Stage boundary

Stage 6 completes homework authoring and submission. Stage 7 will migrate the
teaching data center, pre-lesson analysis, and preview-task workflows, including
the full ECharts lifecycle.
