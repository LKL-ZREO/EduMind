# Stage 5: knowledge-base workspace

## Outcome

The React application now owns `/teacher/docs`. The placeholder has been
replaced with a complete knowledge-base workspace connected to the existing
Spring Boot document, shared-knowledge-base, question, preview, and dashboard
APIs.

The workspace supports:

- personal, owned-team, and joined-team knowledge spaces;
- a recursively nested and draggable resource tree;
- folder navigation, breadcrumbs, search, creation, rename, move, and delete;
- multi-file uploads with per-batch progress;
- Markdown document preview with HTML sanitization;
- document metadata and previously generated teaching materials;
- team knowledge-base creation, editing, dissolution, invitations, and members;
- invitation deep links through `/teacher/docs?joinToken=...`;
- AI generation of preview assignments and classroom questions from PPT files;
- teacher review and editing before generated material is persisted;
- loading, empty, partial-error, and retry states;
- route-level and feature-level lazy loading.

No backend endpoint was changed for the React implementation.

## Why this stage comes after AI chat

The chat stage taught a long-lived request lifecycle. The knowledge-base stage
adds a different set of production React problems: hierarchical data, multiple
server-resource scopes, file transfer, permissions, rich previews, and a
human-in-the-loop AI workflow.

```text
one feature page
├─ personal directory tree
├─ one tree per team knowledge base
├─ content cache per document
├─ generated-material cache per document
├─ member cache per owned knowledge base
└─ global list of available spaces
```

This is a useful interview project because it is not a flat CRUD table. It
shows that React can coordinate several independent server resources without
copying all of them into one large global store.

## Feature structure

```text
features/knowledge/
├─ api/
│  ├─ knowledgeApi.ts
│  └─ knowledgeQueries.ts
├─ components/
│  ├─ KnowledgeSidebar.tsx
│  ├─ DirectoryPane.tsx
│  ├─ DocumentPreview.tsx
│  ├─ UploadDocumentsModal.tsx
│  ├─ KnowledgeBaseSettingsModal.tsx
│  ├─ GenerateMaterialsModal.tsx
│  └─ MaterialDetailModal.tsx
├─ hooks/
│  └─ useKnowledgeMutations.ts
├─ model/
│  ├─ types.ts
│  ├─ tree.ts
│  └─ presentation.ts
└─ pages/
   └─ KnowledgePage.tsx
```

`KnowledgePage` coordinates the use case. Focused components render each pane
or dialog. The API layer translates HTTP responses into domain values. Query
options define remote-resource identity. Pure model functions perform tree and
presentation transformations.

## Flat backend data, nested React data

The directory endpoint returns rows with `id` and `parentId`. The UI needs
nested children for tree rendering and folder navigation.

```text
flat rows
  1: 高一数学, parent = null
  2: 函数笔记.md, parent = 1

buildTree()

高一数学
└─ 函数笔记.md
```

`buildTree` performs two passes:

1. create exactly one React-domain node for every row and index it in a `Map`;
2. attach each node to its parent, or append it to the root collection.

It then returns a new, recursively sorted tree with folders before files. The
source response is never mutated. That matters because TanStack Query owns the
response snapshot; mutating cached objects in place makes updates hard to
reason about and may prevent consumers from observing a new reference.

Other pure functions derive paths, current-folder contents, recursive counts,
and selected nodes. Keeping these transformations outside components makes
them deterministic and directly unit-testable.

## Store identifiers, derive objects

The selected resource is stored as `selectedNodeId`, not as a copied
`TreeNode` object:

```text
selectedNodeId
+ current query tree
-> findTreeNode(...)
-> selectedNode
```

After rename, move, upload, or delete, the query fetches a new tree snapshot.
Deriving the object from the stable identifier prevents local state from
holding an obsolete node object from the previous response.

The same principle applies to the active knowledge space: local state holds
only `activeKbId`; its name and ownership are derived from the spaces query.

## Three local-state values can be meaningful

The upload dialog uses this state:

```ts
TreeNode | null | undefined;
```

Each value has a separate meaning:

| Value       | Meaning                               |
| ----------- | ------------------------------------- |
| `undefined` | dialog is closed                      |
| `null`      | dialog is open for the root directory |
| `TreeNode`  | dialog is open for that folder        |

Using only a truthy check would make “open at root” indistinguishable from
“closed”. This small pattern is useful for menus, optional selections, and
dialogs whose valid target may itself be empty.

## Query keys are the server-state model

TanStack Query keys describe the remote resources:

```text
['knowledge']
├─ ['knowledge', 'spaces']
├─ ['knowledge', 'tree', 'personal']
├─ ['knowledge', 'tree', kbId]
├─ ['knowledge', 'content', docId]
├─ ['knowledge', 'materials', docId]
├─ ['knowledge', 'materials', 'detail', type, id]
├─ ['knowledge', 'members', kbId]
└─ ['knowledge', 'classes']
```

Parameters that change the backend result belong in the key. Personal and team
trees therefore cannot overwrite one another, and switching back to a recently
viewed space can reuse its cached snapshot.

This replaces the manual loading maps and duplicate local arrays often found
in a large page component. TanStack Query owns server state; React state owns
only transient UI choices such as an open dialog, search text, or an editable
AI draft.

## Targeted mutation invalidation

Mutations invalidate the smallest resource that can now be stale:

| Mutation                              | Invalidated query                 |
| ------------------------------------- | --------------------------------- |
| create/rename/move/delete/upload node | active personal or team tree      |
| create/update/join team space         | space list                        |
| remove team member                    | members for that knowledge base   |
| save/archive generated material       | materials for the source document |

For example, creating a folder in team knowledge base `8` invalidates
`['knowledge', 'tree', 8]`; it does not refetch personal documents, chat
sessions, classes, or other team trees.

This is an important distinction between “refetch everything after every
button” and treating cache identity as part of the application design.

## Controlled multi-file upload

Ant Design's upload component is configured with `beforeUpload={() => false}`.
The component therefore manages selection and presentation, while the feature
decides when network transfer starts.

```text
UploadFile[] from Ant Design
-> retain entries with originFileObj
-> File[] domain boundary
-> one FormData request per file
-> aggregate completed + current-file progress
-> invalidate the active tree after the batch succeeds
```

The browser sets the multipart boundary, so the code does not manually set a
`Content-Type` header. Each request still passes through the shared Axios CSRF
interceptor and sends the session cookie.

Files are uploaded sequentially. This produces predictable progress, avoids a
large burst of document-processing jobs, and makes the completed-file fraction
easy to combine with Axios's current request progress.

## Safe document and AI preview

Document content and editable AI guidance can contain Markdown and embedded
HTML. React's `dangerouslySetInnerHTML` is used only after the shared rendering
boundary:

```text
untrusted Markdown
-> Marked
-> DOMPurify
-> sanitized HTML
-> dangerouslySetInnerHTML
```

The route test deliberately returns an image with an `onerror` handler and
asserts that the handler is absent from the rendered DOM. User-entered names,
member names, status labels, and ordinary metadata remain React text nodes and
do not use raw HTML.

## Permissions stay authoritative on the backend

The sidebar shows settings only for knowledge bases in the backend's “owned”
response. This is useful UX, but it is not authorization. Rename, move, member,
document, and generation requests are still protected by the Spring Security
and ownership checks already present in the backend.

Frontend conditions improve clarity; backend checks enforce security. A hidden
button alone never protects a resource.

## Invitations as URL-driven UI

The backend returns an invitation token. React exposes it as:

```text
/teacher/docs?joinToken=<token>
```

Opening this URL initializes the controlled join dialog. After a successful
join, the token is removed with `setSearchParams(..., { replace: true })`, so a
refresh does not repeat the action and browser history is not polluted with a
second entry.

The input also accepts a raw token or an invitation link. A pure parser extracts
`joinToken` or `token`, which keeps URL compatibility logic away from the modal.

## Human-in-the-loop AI generation

Generating materials is modeled as a mutation because it is user-triggered,
changes server-side work, and is not a resource that should run merely because
a component mounted.

```text
select a PPT
-> start long-timeout generation mutation
-> receive preview + quizzes + possible partial errors
-> copy preview fields into controlled editable state
-> teacher reviews and edits
-> explicitly save preview or individual questions
-> invalidate that document's material list
```

Unlike AI chat, this endpoint returns one response rather than an event stream,
so it does not need SSE or an `AbortController` state machine. The response can
contain a successful preview and failed quiz generation, or the reverse. The UI
keeps the successful part and presents both error fields instead of discarding
the entire result.

Generated content is not automatically published. The teacher selects a class,
edits the preview, and explicitly saves it; each generated question is reviewed
and saved separately. This is a product-safety boundary as well as a React form
state example.

## Lazy-loading result

There are nested code-splitting boundaries:

```text
/teacher/docs route
-> KnowledgePage chunk
   ├─ open team settings -> KnowledgeBaseSettingsModal chunk
   ├─ generate material -> GenerateMaterialsModal chunk
   └─ view material      -> MaterialDetailModal chunk
```

The production build emits the main `KnowledgePage` module at approximately
23.52 kB (7.96 kB gzip). The optional settings, generation, and material-detail
modules are separate chunks of approximately 3.39 kB, 4.75 kB, and 3.16 kB.
Users who do not open those workflows do not download their feature code.

## Tests

Tests cover:

- immutable flat-to-nested tree normalization;
- folder-first recursive sorting;
- selected-node paths, current-directory derivation, and recursive counts;
- parsing raw invitation tokens and both supported URL parameter names;
- selecting a document and loading its content and material resources;
- removing an injected HTML event handler before document rendering;
- creating a folder with the real request shape;
- refetching the active directory tree after the mutation;
- consuming a deep-linked invitation and cleaning the URL;
- moving backward and forward through saved classroom questions by ID;
- rendering the self-test questions included in a preview material;
- all previous authentication, classroom, AI-chat, security, and architecture
  behavior.

## Verification

The final React quality gate completed successfully:

```text
PASS  tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (14 files, 40 tests)
PASS  vite production build (1,758 modules)
PASS  npm audit --audit-level=high (0 vulnerabilities)
```

No Spring Boot source was changed in this stage. Automated network tests use
MSW with the actual endpoint and response shapes. PostgreSQL, Redis, MinIO, and
the configured LLM service were not started for a manual upload/generation run.

## Stage boundary

Stage 5 migrates knowledge management and material generation from a source
document. Stage 6 will replace the homework placeholders with task drafts,
question composition, rich text, public student submission, grading results,
and teacher review flows.
