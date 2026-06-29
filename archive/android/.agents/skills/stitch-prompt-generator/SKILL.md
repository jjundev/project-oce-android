---
name: stitch-prompt-generator
description: Google Stitch용 UI/UX 프롬프트를 생성합니다. 사용자가 앱 아이디어나 화면 디자인을 설명하면 Stitch에 바로 붙여넣기 가능한 단계별 프롬프트 시퀀스를 만들어줍니다. "Stitch 프롬프트 만들어줘", "Stitch로 앱 디자인하고 싶어", "UI 생성 프롬프트 작성", "앱 화면 디자인 아이디어를 Stitch 프롬프트로" 같은 요청이 오면 반드시 이 스킬을 사용하세요. UI/UX 디자인 아이디어를 구체화하거나 Stitch 작업 흐름을 최적화할 때도 활용하세요.
---

# Google Stitch Prompt Generator

사용자가 UI/UX 아이디어를 설명하면, **Google Stitch에 바로 붙여넣기 가능한 단계별 프롬프트 시퀀스**를 생성합니다.

## 출력 형식

아래 형식을 반드시 지킵니다:

```
**Prompt 1 — [단계 이름]**
(Stitch에 첫 번째로 입력)

"[영어로 작성된 Stitch 프롬프트]"

→ [이 단계에서 무엇이 만들어지는지 한국어로 한 줄 설명]
```

프롬프트는 **반드시 영어**로, 설명은 **한국어**로 작성합니다.

---

## 핵심 원칙: 반드시 단계를 분리할 것

하나의 프롬프트에 여러 변경사항을 넣으면 Stitch가 레이아웃을 초기화합니다.

- ❌ 필터 추가 + 색상 변경 + 아이콘 배치를 한 번에
- ✅ Prompt 1: 구조 → Prompt 2: 필터 → Prompt 3: 아이콘

**프롬프트 1개 = 변경사항 1~2개** 원칙을 절대 어기지 않습니다.

---

## 프롬프트 구성 절차

사용자 입력을 받으면 아래 순서로 프롬프트를 구성합니다.

### Step 1. 첫 프롬프트 — 전체 구조 + 분위기 설정

앱 유형, 타겟 사용자, 핵심 기능(최대 3개), 스타일 형용사, 색상, 폰트를 포함합니다.

**템플릿:**
```
[App type] for [target users]. [Feature 1], [Feature 2], [Feature 3].
[Style adjective] design. [Color]. [Font style].
```

**예시:**
```
"An app for marathon runners to engage with a community, find running partners,
and discover races nearby. Vibrant, energetic design. Primary blue color. Clean sans-serif font."
```

### Step 2. 이후 프롬프트 — 화면/컴포넌트 단위로 하나씩

```
"On the [screen name], [what] [how]."
```

복잡한 화면(대시보드, 테이블 등)은 구조 → 필터 → 디테일 순으로 3단계 이상 분리합니다.

### Step 3. 테마 변경 프롬프트

색상/분위기를 바꿀 때는 이미지와 아이콘도 함께 명시합니다.
```
"Update theme to [color/mood]. Ensure all images and illustrative icons match this new color scheme."
```

### Step 4. 이미지 수정 프롬프트

대상을 구체적으로 특정합니다.
```
"Change background of all product images on [page] to [color]."
"On '[Page]' page, image of '[Element]': update [attribute] to [value]."
```

---

## Stitch 언어 사전

프롬프트 작성 시 아래 용어를 사용합니다.

**스타일 형용사**
- 밝고 활기찬 → `vibrant`, `energetic`, `bold`
- 미니멀 → `minimalist`, `clean`, `focused`
- 따뜻한 → `warm`, `inviting`, `cozy`
- 고급스러운 → `elegant`, `sophisticated`, `premium`
- 기술적/산업적 → `modern`, `industrial`, `functional`

**UI 컴포넌트 용어**
- 네비게이션: `navigation bar`, `tab bar`, `sidebar`, `hamburger menu`
- 버튼: `call-to-action button`, `primary button`, `floating action button`
- 레이아웃: `card layout`, `grid layout`, `hero section`
- 입력: `search bar`, `input field`, `filter dropdown`, `toggle`
- 기타: `modal`, `badge`, `avatar`, `divider`, `tooltip`

**폰트**
- 모던/기술적 → `clean sans-serif font`
- 클래식/편집적 → `serif font`
- 장난스러운 → `playful sans-serif font`

---

## 주의사항

- Stitch는 이전 대화를 기억하지 못합니다. 매 프롬프트마다 컨텍스트(테마, 스타일)를 유지합니다.
- 프롬프트가 5,000자를 넘으면 컴포넌트가 누락됩니다. 화면 단위로 쪼갭니다.
- 레이아웃 변경과 UI 컴포넌트 추가를 같은 프롬프트에 혼합하지 않습니다.
- 각 단계가 잘 완성됐다면 스크린샷을 저장하도록 사용자에게 안내합니다.

---

*Source: [Google AI Developers Forum — Stitch Prompt Guide](https://discuss.ai.google.dev/t/stitch-prompt-guide/83844)*
