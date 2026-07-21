# Task 1 report

## Status

Implemented and committed the static Firebase Hosting policy-site slice. Firebase Hosting configuration, deployment, `firebase.json`, Android files, and the plan file were not modified.

Commit: `09da22c feat: add policy and account deletion pages`

## Delivered files

- `web/index.html`: policy landing page and shared navigation.
- `web/privacy/index.html`: beta 개인정보 처리방침 draft with the required sections.
- `web/terms/index.html`: beta 이용약관 draft with service, account, AI-result, prohibited-use, IP, changes, limits, liability, jurisdiction, and contact sections.
- `web/delete-account/index.html`: beta account-deletion instructions for the in-app path and the not-yet-configured web-request path.
- `web/styles.css`: shared mobile-first layout, readable contrast, keyboard focus styles, semantic document-region styles, and reduced-motion handling.

All pages include the service name `딸깍영어 (OneClickEng)`, title, final-modified date `2026-07-21`, and relative links to `/privacy`, `/terms`, and `/delete-account`.

## Repository facts used

- Firebase Authentication supports anonymous users and Google-linked users.
- Firestore stores the user profile and user-scoped learning data under `users/{uid}`: saved cards, point ledger, progress marks, gamification documents, study time, and usage data.
- Backend AI tasks include dialogue, speaking analysis, feedback, deep feedback, summary, and TTS. Speaking requests contain base64 WAV audio; TTS requests contain text.
- The repository does not establish that speaking recordings are persistently stored, so the policy does not claim server-side audio retention.
- Firebase Analytics is wired in the Android app. The policy states this fact without inventing a complete SDK auto-collection inventory or retention period.
- Account deletion recursively deletes `users/{uid}` and its descendants, then deletes the Firebase Authentication user.
- Root `sessions/{sessionId}` and `idempotency/{key}` documents are outside `users/{uid}` and are left to `expiresAt` TTL cleanup by the current deletion implementation. The repository does not specify the TTL duration.

## Validation

Passed:

- Required file existence checks.
- Required `rg` content check for policy routes, final-modified date, and contact sections.
- `git diff --check`.
- Python standard-library relative-link validation for all HTML documents.
- Local `python3 -m http.server` smoke test: `/`, `/privacy/`, `/terms/`, and `/delete-account/` each returned HTTP 200.
- Unsupported-value scan found no email address, external URL, legal entity, address, jurisdiction, or numeric retention value in `web/`.
- Staged diff contained only the five requested `web/` files.

The initial optional HTTP check attempted to use `curl`, but `curl` is not installed in the environment. It was replaced with the Python standard-library HTTP check above; no implementation issue resulted.

## Concerns before public release

- Confirm and publish the legal entity, official contact channel, jurisdiction/choice of law, and any required address.
- Confirm retention periods and deletion behavior for Firebase Authentication, Firestore TTL documents, AI provider requests/responses, TTS/speaking audio, and Firebase Analytics.
- Confirm the exact Firebase/Google processor or subprocessor disclosures, processing locations, contracts, and production SDK settings.
- Replace the beta drafts with legally reviewed production text.
- Provide the official secure web deletion email/form and final processing SLA. The current page intentionally does not collect passwords, Firebase ID tokens, or authentication codes in HTML.

## Working-tree note

The pre-existing untracked file `docs/plans/2026-07-21-settings-policy-pages.md` remains unmodified and uncommitted.

## Review-fix report — 2026-07-21

### Changes

- Replaced the directory-index policy layout with flat `web/privacy.html`, `web/terms.html`, and `web/delete-account.html` files. With Firebase Hosting `cleanUrls`, these filenames map directly to the canonical no-trailing-slash paths `/privacy`, `/terms`, and `/delete-account`; the old empty policy directories were removed.
- Updated every document link to use the canonical no-trailing-slash paths and kept the Android-facing paths unchanged.
- Clarified that Firebase Hosting in this change serves only the public static policy documents and is not the app's Firestore or Cloud Functions data store. The policy continues to describe the actual Authentication, Firestore, Functions, Analytics, Gemini, TTL, and account-deletion facts without adding unverified retention values.
- Added the confirmed public contact channel `<a href="mailto:ufo4hyun@gmail.com">ufo4hyun@gmail.com</a>` to the landing page, privacy policy, terms, and account-deletion request instructions. Other missing operational/legal facts remain explicitly marked for pre-release confirmation.

### Focused validation

Passed:

- Static layout check: all five committed web assets exist, no old policy directories remain, and canonical route names resolve to the corresponding flat `.html` documents.
- HTML link check: all internal document and stylesheet links resolve; no internal policy link has a trailing slash; all four documents contain the final-modified date and confirmed `mailto:` contact.
- Privacy wording check: Firebase Hosting/static-document clarification is present and the Firestore/Cloud Functions data-store distinction is explicit.
- `git diff --check`.
- Scope check: only `web/` policy-site files are changed; `firebase.json`, Android files, and the plan remain unmodified. The pre-existing untracked plan file remains uncommitted.

### Remaining concerns

- Firebase Hosting deployment/configuration was intentionally not changed. The flat-file layout is the focused static-site change for `cleanUrls`; deployment should still be smoke-tested in the configured Firebase project.
- Legal entity, address, jurisdiction/choice of law, retention periods, processor disclosures, processing locations, contracts, SDK settings, and production legal review remain unresolved and must be confirmed before public release.
