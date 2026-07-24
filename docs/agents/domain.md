# Domain docs

TrainerLoop uses a single-context domain-documentation layout.

## Before exploring

- Read `CONTEXT.md` at the repository root when it exists.
- Read relevant decisions under `docs/adr/` when that directory exists.
- If either is absent, proceed silently; domain documentation is created lazily
  when terminology or architectural decisions need to be recorded.

Use terminology from `CONTEXT.md` in issues, tests, plans, and implementation.
Surface conflicts with an existing ADR explicitly rather than silently overriding
the recorded decision.
