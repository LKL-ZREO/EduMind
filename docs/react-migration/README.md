# EduMind React migration

This directory records the completed incremental migration of the EduMind
frontend from Vue 3 to React. `react-project/` is now the only development, CI,
and production frontend. `vue-project/` remains as a read-only behavior and
migration reference.

## Consolidated study guide

For one continuous Chinese-language review of the complete migration, React
concepts, engineering decisions, verification history, and interview topics,
read [`REACT_MIGRATION_HANDBOOK.md`](./REACT_MIGRATION_HANDBOOK.md).

## Stages

| Stage                         | Status   | Deliverable                                                          |
| ----------------------------- | -------- | -------------------------------------------------------------------- |
| 0. Freeze the Vue baseline    | Complete | [`00-vue-baseline.md`](./00-vue-baseline.md)                         |
| 1. React foundation           | Complete | [`01-react-foundation.md`](./01-react-foundation.md)                 |
| 2. Authentication and routing | Complete | [`02-auth-routing.md`](./02-auth-routing.md)                         |
| 3. Classroom vertical slice   | Complete | [`03-classroom-vertical-slice.md`](./03-classroom-vertical-slice.md) |
| 4. AI chat                    | Complete | [`04-ai-chat.md`](./04-ai-chat.md)                                   |
| 5. Knowledge base             | Complete | [`05-knowledge-base.md`](./05-knowledge-base.md)                     |
| 6. Homework                   | Complete | [`06-homework.md`](./06-homework.md)                                 |
| 7. Teaching dashboards        | Complete | [`07-teaching.md`](./07-teaching.md)                                 |
| 8. Live classroom             | Complete | [`08-live-classroom.md`](./08-live-classroom.md)                     |
| 9. Cutover                    | Complete | [`09-cutover.md`](./09-cutover.md)                                   |

## Migration rules

1. Keep `vue-project/` as a read-only reference after the stage 9 cutover.
2. Do not change backend contracts merely to make a React page easier to write.
3. Migrate behavior by feature, not by file extension or framework primitive.
4. UI code receives domain data, never raw Axios responses.
5. Server state belongs in TanStack Query; transient local UI state stays local.
6. Every completed stage must pass its documented acceptance checks.
7. Preserve security behavior for session cookies, CSRF, HTML sanitization, SSE,
   student classroom tokens, and WebSocket connection headers.
