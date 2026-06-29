# Issue tracker

Issues for this repo are tracked in **GitHub Issues** for `jjundev/project-oce-android`.

## How agents interact with it

- Use the `gh` CLI for all issue operations.
  - Create: `gh issue create --title "..." --body "..." --label "..."`
  - List: `gh issue list --label "..."`
  - View: `gh issue view <number>`
  - Comment: `gh issue comment <number> --body "..."`
  - Label: `gh issue edit <number> --add-label "..." --remove-label "..."`
- Skills that read from / write to this tracker include `to-issues`, `triage`, `to-prd`, and `qa`.

## External PRs as a request surface

**No.** External pull requests are **not** pulled into the triage queue. `/triage` processes issues only. Collaborators' in-flight PRs are out of scope for triage regardless.

## Triage state machine

Triage applies the labels defined in `docs/agents/triage-labels.md`.
