# Triage labels

The `triage` skill moves each incoming issue through a five-state machine. Each state maps to a GitHub label. This repo uses the **canonical default strings** (each label equals its role name).

| Role | Label string | Meaning |
|------|--------------|---------|
| needs-triage | `needs-triage` | Maintainer needs to evaluate the issue |
| needs-info | `needs-info` | Waiting on the reporter for more information |
| ready-for-agent | `ready-for-agent` | Fully specified, AFK-ready — an agent can pick it up with no human context |
| ready-for-human | `ready-for-human` | Needs human implementation |
| wontfix | `wontfix` | Will not be actioned |

## Notes for agents

- Apply these exact label strings via `gh issue edit <number> --add-label "<label>"`.
- These labels may not exist in the GitHub repo yet. Create them on first use with:
  `gh label create "<label>" --description "..."` (ignore "already exists" errors).
- An issue should generally carry exactly one of these state labels at a time; remove the previous state label when transitioning.
