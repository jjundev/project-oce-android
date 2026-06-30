# 딸깍영어 (One-Click English) — Design System

A self-contained design system for **딸깍영어**, a Korean **voice-based English conversation
learning app** (Android / Jetpack Compose product, recreated here for web). "딸깍" is the
onomatopoeia for a *click* — the brand promise is "tap once, start speaking English."

The aesthetic is **Toss-like, not Toss-branded**: refined minimalism — flat surfaces, generous
whitespace, bold headings, rounded corners — but none of Toss's brand, finance context, or
Tossface. The product brand color is its own blue (`#39A0ED`), explicitly **not** Toss blue
(`#3182F6`).

## Sources

This system was built **entirely from the written spec** in the attached folder
`design_system_src/` (no product code or Figma existed — only design contracts):

- `README.md` — entry point / structure
- `foundations.md` — state axes, accessibility baselines, sheet IA (in-folder mirror of external SoT)
- `design-tokens.md` — color / type / spacing / radius / motion **value** source of truth (+ dark values, Compose mapping)
- `product-design-system.md` — component **contract** SoT (appearance, state matrix, icons, QA gates)
- `product-design-system-buildspec.md` — full anatomy specs, dark values, motion tokens, line-heights, Theme API
- `product-design-system-pilot.md` — first QA pilot (dialogue + turn-feedback component × state matrix)

External provenance referenced but **not required** (and not accessible here): `../../ux/*`,
`../../design-system/PROVENANCE.md`, `PRD.md`. Token values trace to the legacy Android app's
`colors.xml` / `dimens.xml` measured values, re-named into semantic tokens.

---

## Content fundamentals

- **Language: Korean UI, English learning content.** All chrome, labels, and copy are Korean;
  the *learned material* (chat practice, examples) is English and tagged `lang="en"` for correct
  pronunciation/screen-reader behavior.
- **Voice: warm, encouraging, casual-polite (해요체).** Feedback praises first, then corrects:
  "탄탄한 문장이에요. 시제만 한 곳 다듬으면 완벽해요." Not clinical, not stiff. Uses 요-ending,
  second-person implied (you-as-learner) without overusing 당신.
- **Concise and concrete.** Short sentences. Numbers carry meaning (점수 88, 🔥 7일, 8분). Labels
  are nouns ("작문 점수", "자연스러운 표현"), buttons are verbs ("다음", "다시 시도", "더 보기").
- **Emoji: sparing, functional only.** 🔥 for streak is the one sanctioned emoji (a gamification
  signal). No decorative emoji, no Tossface. Meaning is never carried by emoji/color alone — always
  paired with text.
- **Casing:** Korean has no case; English content uses natural sentence case, never ALL CAPS except
  the saved-card *type taxonomy* tokens (WORD / SENTENCE / EXPRESSION) which are system enums shown
  via badges with Korean labels (단어 / 문장 / 표현).

---

## Visual foundations

- **Color.** Fixed brand palette, Material You dynamic color **OFF** — meaning colors (score,
  4 voice states, streak) must never be hijacked by device wallpaper. Brand blue `#39A0ED` for
  CTAs / progress / user chat bubbles. Coral `#EF767A` is **demoted to a meaning color** ("정확한
  표현 / 교정"), never the primary. Green `#009B72` = "자연스러운 표현". Orange `#FF5C00` = streak.
  Gold `#FFC107` = saved.
- **Type.** Pretendard, 5 weights (400/500/600/700/800), global. Bold, tight display
  (letter-spacing -0.02em on titles & score). 56px score is the hero number. Body 16px / lh 1.45.
- **Spacing.** 4dp-based scale (6/8/12/16/20/24/40). Sheets & dialogs pad 24, sections gap 24,
  action gap 12, loading pad 40.
- **Backgrounds.** Flat solid fills only — `surface.background` (#F3F4F6) under `surface.card`
  (#FFFFFF). **No imagery, no full-bleed photos, no patterns/textures.** The single gradient is the
  135° brand-blue hero card.
- **Elevation: 0 by default.** Depth = surface layer + 1dp hairline border, **never shadow**. The
  *one* exception is the bottom nav (8dp). This is the system's defining rule.
- **Corners.** 4 (bubble tail / waveform / skeleton) · 8 (input) · 12 (button / mic bg) · 16
  (dialog) · 18 (chat bubble body) · 24 (card / sheet) · pill (chips, segmented).
- **Cards.** `surface.card`, radius.24, 1dp hairline, no shadow. The gradient hero card is the only
  exception (white text on 135° blue).
- **Motion.** Short and restrained — color 100ms, background 200ms. Standard ease
  `cubic-bezier(.25,.1,.25,1)`, out `cubic-bezier(.22,1,.36,1)`, spring `cubic-bezier(.34,1.56,.64,1)`.
  **No infinite decorative animation.** Four signature interactions: mic 4-state (recording ripple
  ×3 phase-offset, analyzing progress ring), crackle waveform (40 bars, ±0.3 jiggle), slot-machine
  count-up (reward only, 1260ms, spring snap), progressive skeleton shimmer (1200ms).
- **Hover/press.** Press: primary → `brand.primaryPressed` (darker), mic → scale 0.96, rows →
  surface-background tint. Disabled → alpha 0.38. Focus → `brand.primary` ring/border.
- **Transparency/blur.** Used only for the sheet scrim (`overlay-dim`, ~0.42 dim) and venn-circle
  alpha (~0.5 sides, darker intersection). No glassmorphism.
- **Accessibility is foundational.** ≥48dp targets (mic 96), color never the sole signal, light/dark
  full token sets, fontScale 1.3× must not break, reduce-motion → static fallbacks, voice transitions
  announce assertively, venn always carries a text alternative.

---

## Iconography

- **Contract:** filled / solid **24-grid** glyphs, ~1px optical weight, color inherited from text.
  Sizes 16 / 20 / 24 (default 24). Every icon needs a `contentDescription` (icons render `aria-hidden`;
  the *parent control* carries the label).
- **⚠️ Status — icons are intentionally BLANK.** The product's official icon set is not yet
  selected (deferred to M0; the Toss-reference Heroicons-solid set is explicitly NOT carried into
  the product). Per request, the `Icon` component currently renders a **blank placeholder** that
  reserves the glyph box so layouts stay intact — no substitute glyph is shown. Every call site
  already passes the intended semantic `name` (e.g. `mic`, `bookmark`, `chevron_right`), so dropping
  in the official set is a **one-component change** (`components/core/Icon.jsx` is the single seam).
- **Emoji as icon:** only 🔥 (streak). No other emoji or unicode glyphs used as icons.
- **No hand-drawn SVG icons** beyond the venn-diagram primitive (which is a data viz, not an icon).

---

## Index (manifest)

**Root**
- `styles.css` — global entry (import this one file). `@import`s all of `tokens/`.
- `tokens/` — `colors.css` (light + `[data-theme="dark"]`), `typography.css`, `spacing.css`,
  `radius.css`, `motion.css`, `fonts.css` (Pretendard @font-face, CDN).
- `readme.md` — this file. `SKILL.md` — Agent-Skill manifest.

**Components** (`window.DesignSystem_498c1d.<Name>`)
- `components/core/` — Button, IconButton, Icon, Card, Badge
- `components/forms/` — Input, Switch, SegmentedControl
- `components/data/` — ListRow, SavedCard
- `components/dialogue/` — ChatBubble, MicButton, Waveform
- `components/feedback/` — BottomSheet, FeedbackSection, FeedbackSheet, VennDiagram, RewardStrip
- `components/navigation/` — BottomNav

Each directory has a `<name>.card.html` (Design System tab specimen). Each component has `.jsx` +
`.d.ts` (props contract) + `.prompt.md` (usage).

**Guidelines** (`guidelines/*.card.html`) — foundation specimens: brand/voice/feedback colors,
display/body type, spacing & radius scale.

**UI kit** (`ui_kits/app/`) — *in progress* — interactive recreations of the app surfaces
(Home, Dialogue learning, Feedback sheet, Summary, Saved cards, Settings).

---

## Status / caveats

- **Icon set is currently BLANK** (placeholder slots, awaiting the official set) — `Icon.jsx` is the
  single seam to swap when chosen.
- **Pretendard is bundled locally** (`assets/fonts/`, 5 weights, OFL).
- Built from written contracts only — no product screenshots/Figma existed, so UI-kit screens are
  faithful *constructions from the spec*, not copies of shipped pixels.
