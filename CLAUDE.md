# project-oce-android

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues (`gh` CLI) for `jjundev/project-oce-android`. External PRs are not a triage surface. See `docs/agents/issue-tracker.md`.

### Triage labels

Canonical five-role vocabulary using default label strings (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

### Android verification

워크트리에서 gradle 검증은 `scripts/verify-android.sh` 로 돌린다(공유 `~/.gradle` 캐시·데몬 오염, `google-services.json` 부재, KGP 변이 테스트 미컴파일 등 재발 함정을 우회). See `docs/agents/android-verification.md`.
