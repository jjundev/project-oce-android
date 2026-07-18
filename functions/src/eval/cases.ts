/**
 * Golden case set for the turn-feedback eval harness.
 *
 * Six categories, 13 cases. Each probes a distinct way a Korean learner's English can
 * be wrong (or right), because the failure modes need different things from the model:
 * catching a tense slip is not the same skill as declining to "fix" a sentence that is
 * already native-sounding. `expect` holds only what is MECHANICALLY checkable — whether
 * the correction is pedagogically good is a human call, made against the markdown report
 * that eval/run.js emits.
 *
 * Pure data. No network, no I/O — test/eval-harness.test.ts validates it offline.
 */
import type { FeedbackRequestPayload } from "../llm/feedback";
import { CaseExpectation } from "./validate";

export type EvalCategory =
  | "typo-grammar"
  | "konglish"
  | "awkward-but-correct"
  | "already-good"
  | "minimal-input"
  | "level-variance";

export interface EvalCase {
  /** stable id — used by `--only=` and as the report anchor */
  id: string;
  category: EvalCategory;
  /** what this case probes, in Korean — printed in the report to frame human review */
  note: string;
  payload: FeedbackRequestPayload;
  expect: CaseExpectation;
}

export const CASES: readonly EvalCase[] = [
  // ── typo-grammar — plain mistakes the model MUST catch ──────────────────────
  {
    id: "tg-past-tense",
    category: "typo-grammar",
    note: "과거 시제 오류(meet → met). 가장 기본적인 문법 실수를 잡는지.",
    payload: {
      koreanPrompt: "어제 친구를 만났어요.",
      userEnglish: "I meet my friend yesterday.",
      referenceEnglish: "I met my friend yesterday.",
      level: "normal",
    },
    expect: { requiresIncorrectSegments: true, scoreMax: 89 },
  },
  {
    id: "tg-typo-redundant",
    category: "typo-grammar",
    note: "오타(moring)와 중복 표현(every morning ... in the morning)이 함께 있는 경우.",
    payload: {
      koreanPrompt: "저는 매일 아침에 커피를 마셔요.",
      userEnglish: "I drink coffee every morning in the moring.",
      referenceEnglish: "I drink coffee every morning.",
      level: "normal",
    },
    expect: { requiresIncorrectSegments: true, scoreMax: 89 },
  },

  // ── konglish — grammatical but not what a native would say ──────────────────
  {
    id: "kl-hand-phone",
    category: "konglish",
    note: "'핸드폰'의 직역(hand phone). 콩글리시 어휘를 잡아내는지.",
    payload: {
      koreanPrompt: "핸드폰 충전기 좀 빌릴 수 있을까요?",
      userEnglish: "Can I borrow your hand phone charger?",
      referenceEnglish: "Could I borrow your phone charger?",
      level: "normal",
    },
    expect: { requiresIncorrectSegments: true },
  },
  {
    id: "kl-condition",
    category: "konglish",
    note: "'컨디션'의 오용. 영어 condition은 몸 상태를 뜻하지 않는다.",
    payload: {
      koreanPrompt: "저는 오늘 컨디션이 안 좋아요.",
      userEnglish: "My condition is not good today.",
      referenceEnglish: "I'm not feeling well today.",
      level: "normal",
    },
    expect: {},
  },
  {
    id: "kl-eat-rice",
    category: "konglish",
    note: "'밥 먹다'의 직역(eat rice). 문법은 완벽해서 grammar 섹션이 아니라 naturalExpression이 일해야 하는 케이스.",
    payload: {
      koreanPrompt: "다음에 밥 한번 먹어요.",
      userEnglish: "Let's eat rice next time.",
      referenceEnglish: "Let's grab a meal sometime.",
      level: "normal",
    },
    expect: {},
  },

  // ── awkward-but-correct — nothing to "correct", plenty to improve ───────────
  {
    id: "ab-possible-to-sit",
    category: "awkward-but-correct",
    note: "문법적으로 흠이 없지만 원어민은 이렇게 말하지 않는다. 과잉 문법 교정 없이 자연스러움만 다뤄야 한다.",
    payload: {
      koreanPrompt: "이 자리에 앉아도 될까요?",
      userEnglish: "Is it possible for me to sit on this seat?",
      referenceEnglish: "Is this seat taken?",
      level: "normal",
    },
    expect: {},
  },
  {
    id: "ab-move-meeting",
    category: "awkward-but-correct",
    note: "'the next week'의 불필요한 관사. 거의 맞는 문장에서 미세한 차이를 잡아내는지.",
    payload: {
      koreanPrompt: "회의를 다음 주로 미룰 수 있을까요?",
      userEnglish: "Can we move the meeting to the next week?",
      referenceEnglish: "Could we push the meeting to next week?",
      level: "normal",
    },
    expect: {},
  },

  // ── already-good — the over-correction trap ─────────────────────────────────
  {
    id: "ag-coffee-please",
    category: "already-good",
    note: "이미 자연스럽고 정중한 문장. 고칠 것이 없는데도 억지 교정을 만들어내는지 확인한다.",
    payload: {
      koreanPrompt: "커피 한 잔 주시겠어요?",
      userEnglish: "Could I get a coffee, please?",
      referenceEnglish: "Can I get a coffee?",
      level: "normal",
    },
    expect: { noIncorrectSegments: true, scoreMin: 85 },
  },
  {
    id: "ag-thank-you",
    category: "already-good",
    note: "완벽한 문장. 프롬프트의 '이미 훌륭하면 축하하라' 규칙이 실제로 지켜지는지.",
    payload: {
      koreanPrompt: "도와주셔서 정말 감사합니다.",
      userEnglish: "Thank you so much for your help.",
      referenceEnglish: "Thanks a lot for your help.",
      level: "normal",
    },
    expect: { noIncorrectSegments: true, scoreMin: 90 },
  },

  // ── minimal-input — can the model stay honest with almost nothing to go on ──
  {
    id: "mi-one-word",
    category: "minimal-input",
    note: "한 단어 입력. 의미는 전달되지만 문장이 아니다. 점수와 교정이 폭주하지 않는지.",
    payload: {
      koreanPrompt: "커피 주세요.",
      userEnglish: "Coffee.",
      referenceEnglish: "Can I get a coffee?",
      level: "normal",
    },
    expect: {},
  },
  {
    id: "mi-broken-fragment",
    category: "minimal-input",
    note: "의미를 알 수 없는 파편. 모델이 근거 없이 학습자 의도를 지어내는지 확인하는 케이스.",
    payload: {
      koreanPrompt: "그건 좀 그래요.",
      userEnglish: "That is little bit.",
      referenceEnglish: "That doesn't quite work for me.",
      level: "normal",
    },
    expect: { requiresIncorrectSegments: true },
  },

  // ── level-variance — identical English, different level ─────────────────────
  // FEEDBACK_SYSTEM_PROMPT never mentions `level`; it only rides along in the payload
  // JSON. These two exist to make that visible: near-identical output across the pair
  // is evidence that level-aware grading is unimplemented, not that it failed.
  {
    id: "lv-starter",
    category: "level-variance",
    note: "level=starter. 아래 lv-expert와 영어 문장이 동일하다 — level만 다르다.",
    payload: {
      koreanPrompt: "주말에 뭐 했어요?",
      userEnglish: "What you do weekend?",
      referenceEnglish: "What did you do over the weekend?",
      level: "starter",
    },
    expect: { requiresIncorrectSegments: true },
  },
  {
    id: "lv-expert",
    category: "level-variance",
    note: "level=expert. 위 lv-starter와 영어 문장이 동일하다 — level만 다르다.",
    payload: {
      koreanPrompt: "주말에 뭐 했어요?",
      userEnglish: "What you do weekend?",
      referenceEnglish: "What did you do over the weekend?",
      level: "expert",
    },
    expect: { requiresIncorrectSegments: true },
  },
];
