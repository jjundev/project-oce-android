# Domain docs

This repo uses a **single-context** layout.

- **Domain language / context:** `CONTEXT.md` at the repo root.
- **Architecture decisions:** `docs/adr/` at the repo root.

## Consumer rules for agents

- Skills such as `improve-codebase-architecture`, `diagnosing-bugs`, and `tdd` read `CONTEXT.md` to learn the project's domain language before making changes.
- Past architectural decisions live as ADR files under `docs/adr/`. Read them before proposing changes that touch the same area; record new decisions there.
- There is one global context for the whole repo — there is no `CONTEXT-MAP.md` and no per-package `CONTEXT.md`.

> `CONTEXT.md` does not exist yet. Create it at the repo root when you first capture the project's domain language.
