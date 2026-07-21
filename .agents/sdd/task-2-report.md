# Task 2 report — Firebase Hosting policy pages

Date: 2026-07-21
Project: `oce-v1`

## Scope

- Added Firebase Hosting configuration for the existing flat Task 1 files in `web/`.
- Preserved the existing Firestore, Functions, and emulator configuration blocks.
- Updated `docs/ux/settings-data-account.md` §9 with the deployed hostname, Play Console registration purpose, beta-draft limitation, and redeployment procedure.
- Did not modify Android files or policy page content. The supplied contact email remains `ufo4hyun@gmail.com`.

## Configuration

Hosting now serves `web/` with `cleanUrls: true` and ignores `firebase.json`, hidden files, and `node_modules`. No rewrites were added, so `/privacy`, `/terms`, and `/delete-account` resolve to their independent flat HTML files.

## Validation

| Check | Result |
|---|---|
| `firebase use oce-v1` | Passed; active project is `oce-v1`. |
| `firebase deploy --only firestore:rules --dry-run` | Passed; rules compiled and dry run completed. |
| `firebase deploy --only functions --dry-run` | Blocked by the existing Functions predeploy build: `tsc` is not installed in the worktree. |
| Hosting emulator clean routes | Passed; `/privacy`, `/terms`, and `/delete-account` each returned HTTP 200 and the expected Korean title. Emulator was stopped after verification. |
| `firebase deploy --only hosting` | Passed; Hosting release completed for `oce-v1`. |
| `https://oce-v1.web.app/` | Passed; HTTP 200. |
| `https://oce-v1.web.app/{privacy,terms,delete-account}` | Passed; all returned HTTP 200 and contained the expected page headings/titles. |
| `https://oneclickeng.web.app/{privacy,terms,delete-account}` | Blocked; the first route returned HTTP 404, indicating the hostname is not currently connected to this Hosting site. |

## Deployment status

Hosting is deployed at `https://oce-v1.web.app`. The intended `oneclickeng.web.app` hostname requires Firebase Hosting custom-domain/site mapping before it can be used for Play Console registration or Android URL constants. The public legal content remains beta draft and needs legal review before public-production promotion.

## Self-review

- `firebase.json` contains the requested Hosting block and no Hosting rewrites.
- Firestore and Functions blocks are unchanged.
- The documented hostname matches the successful deployment, while the failing hostname is explicitly called out rather than presented as live.
- The diff contains no Android or `web/` policy-content changes.
