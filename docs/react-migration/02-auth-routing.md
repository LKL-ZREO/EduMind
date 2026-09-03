# Stage 2: Authentication, routing, and teacher layout

## Outcome

The React application now implements the real teacher authentication boundary
and all 17 Vue business routes. Login, registration, session restoration,
guest/protected redirects, logout, and the teacher navigation layout are real
React features. Business modules that belong to later stages render an explicit
migration placeholder while preserving their final path and access policy.

## Authentication data flow

```text
Open a guest or protected route
-> React Router loader
-> queryClient.ensureQueryData(currentUserQueryOptions)
-> GET /api/auth/me with the session cookie
-> cache AuthenticatedUser or null under ['auth', 'current-user']
-> continue, redirect to login, or redirect to /teacher/chat
```

The current user is server state. It is therefore stored in TanStack Query,
not copied into Context or Zustand.

The query uses infinite stale and garbage-collection time because authentication
does not become stale on a timer. Login, logout, a 401 response, or an explicit
session probe changes it.

## Route loaders

`requireAuthLoader` runs before a protected route renders. It preserves the
complete requested path in a URL-encoded `redirect` parameter:

```text
/teacher/classes?tab=active
-> /login?redirect=%2Fteacher%2Fclasses%3Ftab%3Dactive
```

`guestOnlyLoader` prevents an authenticated user from returning to login or
registration and redirects to `/teacher/chat`.

React Router's `redirect()` returns a `Response`. Loaders return that response;
they do not throw an arbitrary non-Error value. This keeps strict TypeScript
and ESLint error rules intact.

## Safe post-login redirect

Query parameters are untrusted input. The login page only accepts a redirect
that:

- starts with one `/`;
- is not protocol-relative (`//host`);
- resolves to the current origin;
- can be parsed as a URL.

Invalid values fall back to `/teacher/chat`. This prevents an attacker from
turning the login page into an open redirect.

## Query and mutation responsibilities

```text
GET /auth/me        -> query
POST /auth/login    -> mutation, then set current-user cache
POST /auth/register -> mutation, no authentication cache change
POST /auth/logout   -> mutation, then clear application cache and set user null
```

The optional login `sessionId` continues to be stored in local storage for AI
chat context. It is not treated as the teacher authentication credential.

Logout clears:

- the server session;
- the chat session ID;
- legacy JWT-era storage keys;
- the cached CSRF token;
- TanStack Query data.

## Axios and React integration

Axios interceptors cannot call React hooks. Application bootstrap now registers
an unauthorized callback before `createRoot` renders the application:

```text
Axios receives a relevant 401
-> configured callback
-> clear Query Client
-> cache current user as null
-> preserve the current internal path
-> full-page redirect to /login
```

`/auth/me`, login/register attempts, and public student-live behavior keep their
documented exceptions.

## Nested layout

The route with ID `teacher` owns the authentication loader and
`TeacherLayout`. Its child pages render through `<Outlet />`:

```text
/teacher
└─ TeacherLayout
   ├─ header: brand, navigation state, user, logout
   ├─ responsive Ant Design sider/menu
   └─ content
      └─ Outlet -> matched child route
```

`/view/submission/:id` stays outside this layout while still using the protected
loader, matching the Vue baseline.

## Route skeleton

All final paths now exist. Pending pages record their intended stage:

- stage 3: classroom list/detail;
- stage 4: AI chat;
- stage 5: knowledge base;
- stage 6: homework, submission, task detail;
- stage 7: dashboards, pre-lesson, preview tasks;
- stage 8: teacher/student live classroom.

Unknown paths redirect to `/`, matching the Vue catch-all behavior. The stage 1
foundation screen moved to `/migration/foundation`.

## Component and form model

Login and registration are function components. Their UI is composed from a
shared `AuthPageShell`, Ant Design form controls, local error state, and mutation
hooks.

Form submission follows this sequence:

```text
controlled form values
-> client validation
-> typed onFinish values
-> mutateAsync
-> server response
-> Query cache update or rendered error
-> navigation
```

Registration navigates immediately to `/login?registered=1` after success. The
login page renders the success notice from URL state. This avoids an unmanaged
timer and remains recoverable after a refresh.

## Lazy loading

Login and registration use the React Router `lazy` route property. The build now
emits separate route chunks:

```text
LoginPage       2.38 kB
RegisterPage    2.78 kB
AuthPageShell  41.19 kB
main entry     339.31 kB
```

The stage 1 main JavaScript bundle was approximately 896 kB. Route boundaries
reduced the main entry to approximately 339 kB. A shared authentication/vendor
chunk is still 607 kB and will be inspected with the later application-wide
bundle split; no warning threshold was raised.

## Tests

MSW intercepts real HTTP requests at the network boundary. Tests do not mock
Axios internals.

Covered behavior includes:

- protected route redirects to login and preserves path/query;
- authenticated users cannot visit login;
- login obtains CSRF, posts credentials, caches the user, stores chat session ID,
  and returns to the requested route;
- mismatched registration passwords never reach the server;
- external/protocol-relative post-login redirects are rejected;
- internal redirects preserve query and hash;
- previous architecture and HTML security tests continue to pass.

## Verification

`npm run ci:check` completed successfully:

```text
PASS  prettier --check .
PASS  tsc --build
PASS  oxlint
PASS  eslint
PASS  vitest (5 files, 16 tests)
PASS  vite production build (1,657 modules)
```

npm reported zero known vulnerabilities after adding MSW.

## Stage boundary

Stage 2 does not fetch classroom, chat, knowledge, homework, dashboard, or live
data. It only gives those final URLs a correct security and layout boundary.
Stage 3 can now replace the classroom placeholders without changing bootstrap,
session restoration, navigation layout, or route protection.
