# Stage 3: Classroom vertical slice

## Outcome

The React application now owns the complete teacher classroom workflow at
`/teacher/classes` and `/teacher/classes/:id`. The former migration placeholders
were replaced with real backend integration while the Vue pages remain runnable
as the behavior reference.

This stage includes:

- grouped class and course display, search, loading, empty, and error states;
- class creation, editing, archiving/restoring, and guarded deletion;
- course creation, preset selection, editing, and deletion;
- class detail and student roster display;
- student search and removal;
- XLS/XLSX/CSV student import with validation and preview;
- import-template download;
- invitation code, origin-aware invitation link, QR code, and copy actions;
- lazy-loaded list/detail routes and a separately loaded spreadsheet parser.

## Why migrate a vertical slice

A vertical slice moves one user journey through every frontend layer:

```text
route
-> page component
-> query or mutation hook
-> typed API function
-> existing Spring Boot endpoint
-> cache refresh
-> updated UI
```

This is more useful than converting all Vue templates first and wiring data
later. It proves that the React foundation can perform real authenticated work,
preserve backend contracts, display failures, and remain testable before the
next feature starts.

## Feature-first structure

The classroom capability owns its UI, network code, state coordination, and
domain types:

```text
features/classroom/
├─ api/
│  ├─ classroomApi.ts
│  └─ classroomQueries.ts
├─ components/
│  ├─ ClassCard.tsx
│  ├─ ClassEditorModal.tsx
│  ├─ CourseEditorModal.tsx
│  ├─ InviteModal.tsx
│  ├─ StudentImportModal.tsx
│  └─ StudentTable.tsx
├─ hooks/
│  └─ useClassroomMutations.ts
├─ model/
│  ├─ groupClasses.ts
│  └─ types.ts
└─ pages/
   ├─ ClassListPage.tsx
   └─ ClassDetailPage.tsx
```

Pages compose a use case. Components render focused UI. Hooks coordinate server
state. API functions know HTTP. Pure model functions group, sort, filter, and
format data without knowing React.

## Typed API boundary

The backend continues to return the shared `Result<T>` envelope. Classroom API
functions unwrap that envelope before data reaches components:

```text
AxiosResponse<ApiResponse<ClassGroupResponse[]>>
-> unwrapApiResponse(...)
-> Promise<ClassGroupResponse[]>
```

This has two benefits:

- a component cannot accidentally depend on `response.data.data`;
- a business failure with HTTP 200 but a non-success result code still becomes
  an exception handled by the query or mutation.

The detail adapter also normalizes backend compatibility fields such as
`createdAt` versus `joinedAt` exactly once. Every student component receives one
stable `Student` shape.

## Server state and UI state

React code becomes easier to reason about when state is classified first:

| State                      | Owner                      | Example                                    |
| -------------------------- | -------------------------- | ------------------------------------------ |
| Server state               | TanStack Query             | classes, courses, presets, roster          |
| Mutation state             | TanStack Query             | saving, deleting, importing                |
| Shareable navigation state | URL                        | class search `q`, student search `student` |
| Ephemeral UI state         | component `useState`       | an open modal, current import preview      |
| Derived state              | `useMemo` or pure function | filtered groups, totals, valid import rows |

The class list is not copied into local state after fetching. TanStack Query is
the single client-side owner of that server snapshot. Local state only records
what the user is currently doing.

## Query keys and cache invalidation

The query-key factory creates a predictable hierarchy:

```text
['classroom']
├─ ['classroom', 'class-groups']
├─ ['classroom', 'class-detail', classId]
├─ ['classroom', 'courses']
└─ ['classroom', 'course-presets']
```

Mutations invalidate only data made stale by the operation:

| Mutation                     | Invalidated data                                 |
| ---------------------------- | ------------------------------------------------ |
| Create class                 | class groups                                     |
| Create/update/delete course  | courses and class groups                         |
| Update/archive/remove/import | current class detail and class groups            |
| Delete class                 | remove its detail cache and refresh class groups |

This stage deliberately uses invalidation and a backend refetch instead of
optimistic updates. Classroom operations are infrequent and the backend applies
authorization, duplicate checks, import limits, and derived counts. Refetching
the authoritative result is simpler and safer here. A chat token stream in the
next stage will need a different update strategy.

## React component composition

The list page passes domain objects and callbacks downward:

```text
ClassListPage
├─ ClassCard[]
├─ ClassEditorModal
├─ CourseEditorModal
└─ InviteModal
```

The detail page follows the same composition pattern:

```text
ClassDetailPage
├─ StudentTable
├─ ClassEditorModal
├─ InviteModal
└─ StudentImportModal
```

The child components do not fetch the same resource again. Their props form an
explicit contract, and the page remains the coordinator for navigation,
messages, and mutations.

## Forms and controlled inputs

Ant Design `Form` owns validation and field values inside the class/course
modals. `Form.useForm()` supplies a stable form instance, while `useEffect`
synchronizes the external modal lifecycle with that form instance when a modal
opens.

The submit boundary trims every string. Creation omits empty optional fields;
editing preserves empty description, course-group, and QQ values so a teacher
can intentionally clear existing data through the backend's partial-update
contract.

Search fields use local controlled state for immediate typing and debounce URL
updates by 200 ms. This separates two different speeds:

```text
keystroke -> local state -> immediate filtering
          -> 200 ms quiet period -> replace URL query parameter
```

The first implementation controlled the input directly from the URL. A route
navigation occurred for every key and could overwrite rapid Chinese input with
a stale value. Keeping the responsive draft local and treating URL persistence
as an external effect removes that race and avoids filling browser history with
one entry per character.

## Route parameters and invalid input

The detail page reads `:id` with `useParams()`, converts it once, and validates
that it is a positive safe integer. An invalid ID renders a local 404 result and
does not send `/classes/0` or `/classes/NaN` to the backend.

Both classroom routes use React Router's `lazy` property. The teacher loader and
layout stay mounted while each page module is downloaded only when matched.

## Spreadsheet import

`StudentImportModal` performs client-side validation before the server request:

- accepts XLSX, XLS, and CSV extensions;
- recognizes Chinese and common English student-ID/name headers;
- ignores blank rows;
- reports missing fields and duplicate IDs;
- limits the valid request payload to the backend maximum of 200 students;
- previews the first 50 parsed rows while retaining all validation results;
- sends only valid `{ studentId, studentName }` objects.

The spreadsheet library is loaded with `import('xlsx')` only when the user
parses a file or downloads a template. The production build therefore emits a
separate `xlsx` chunk instead of charging every classroom visit for spreadsheet
support.

The initial npm-registry `xlsx` package resolved to an older release with known
high-severity advisories. It was replaced with SheetJS's official 0.20.3 CDN
tarball, following the [official Node.js installation guidance](https://docs.sheetjs.com/docs/getting-started/installation/nodejs/).
`npm audit --audit-level=high` reports zero known vulnerabilities.

## Invitation behavior

The invitation link is built with `new URL('/', window.location.origin)` and the
`URLSearchParams` API. It never hard-codes a development or production domain.
The same value is rendered as text, copied to the clipboard, and encoded into
the QR canvas.

The public root route does not consume `inviteCode` yet because that belongs to
the stage 6 submission flow. Generating the final URL now lets teachers share a
stable link without inventing a temporary route contract.

## Test strategy

Pure grouping logic has unit tests. Route behavior uses React Testing Library,
a memory router, the real Query Client, the real Ant Design provider hierarchy,
and MSW at the HTTP boundary.

Covered classroom behavior includes:

- grouping, empty courses, ungrouped classes, ordering, counts, and filtering;
- rendering a class list and persisting search in the URL;
- posting a new class and refetching the class-group query;
- removing a student and refetching class detail;
- existing authentication, routing, security, and architecture tests.

The test environment supplies constructible `ResizeObserver` and
`IntersectionObserver` implementations because Ant Design instantiates these
browser APIs. Route tests also render Ant Design's `<App>` provider because
`App.useApp()` messages and confirmation modals depend on that context.

## Verification

The full React quality gate completed successfully:

```text
PASS  prettier --check .
PASS  tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (7 files, 21 tests)
PASS  vite production build (1,702 modules)
PASS  npm audit --audit-level=high (0 vulnerabilities)
```

The build emits separate list, detail, and spreadsheet chunks. A shared Ant
Design/vendor chunk remains above Vite's 500 kB warning threshold. The warning
is still visible and is not hidden by raising the threshold; application-wide
vendor splitting remains part of the cutover performance pass.

## Stage boundary

Stage 3 does not migrate the public invitation-join form or any AI chat state.
The next stage replaces `/teacher/chat` with real session management, streaming
responses, cancellation, Markdown rendering, and navigation blocking while an
AI response is active.
