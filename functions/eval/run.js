#!/usr/bin/env node
"use strict";

/**
 * Live turn-feedback eval runner. Sweeps temperatures across the golden case set,
 * validates every response structurally, and writes a markdown report for human review.
 *
 * This lives OUTSIDE functions/test/ on purpose: jest.config.js:1 declares that suite
 * "no emulator, no network", and this script is nothing but network. It is also outside
 * tsconfig's `include: ["src"]`, so it is plain JS requiring the built output — that
 * keeps it dependency-free (no ts-node) at the cost of running `npm run build` first,
 * which `npm run eval` does for you.
 *
 * Usage:
 *   GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) \
 *     npm run eval -- --temps=0,0.3,0.7 --repeats=3 --task=feedback
 *
 * This SPENDS REAL QUOTA: cases × temps × repeats calls (default 14 × 3 × 3 = 126).
 */

const fs = require("fs");
const path = require("path");

const { CASES } = require("../lib/eval/cases");
const {
  validateSlim,
  validateDeep,
  scoreOf,
  countIncorrectSegments,
} = require("../lib/eval/validate");
const {
  buildGenerateBody,
  extractJson,
  FEEDBACK_SYSTEM_PROMPT,
  FEEDBACK_RESPONSE_SCHEMA,
  FEEDBACK_DEEP_SYSTEM_PROMPT,
  FEEDBACK_DEEP_RESPONSE_SCHEMA,
} = require("../lib/providers/gemini");
const { modelFor } = require("../lib/config/models");

/** Mirrors the unexported GEMINI_BASE_URL in src/providers/gemini.ts:67. */
const BASE_URL = "https://aiplatform.googleapis.com/v1/publishers/google";

const DEFAULTS = { temps: [0, 0.3, 0.7], repeats: 3, task: "feedback", only: null };

/** delay between every call, regardless of outcome — keeps a ~126-call sweep off a burst pattern. */
const CALL_DELAY_MS = 200;
/** backoff before the single retry on a 429/5xx. */
const RETRY_DELAY_MS = 500;
/** how much of an unparseable response body to retain for the report. */
const RAW_TEXT_KEEP_CHARS = 500;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function parseArgs(argv) {
  const opts = { ...DEFAULTS };
  for (const arg of argv.slice(2)) {
    const match = /^--([a-zA-Z]+)=(.*)$/.exec(arg);
    if (!match) throw new Error(`bad flag: ${arg} (expected --key=value)`);
    const [, key, raw] = match;
    if (key === "temps") {
      opts.temps = raw.split(",").map((t) => {
        const n = Number(t);
        if (!Number.isFinite(n) || n < 0 || n > 2) throw new Error(`bad temperature: ${t}`);
        return n;
      });
    } else if (key === "repeats") {
      opts.repeats = Number(raw);
      if (!Number.isInteger(opts.repeats) || opts.repeats < 1) {
        throw new Error(`bad repeats: ${raw}`);
      }
    } else if (key === "task") {
      if (raw !== "feedback" && raw !== "feedbackDeep") {
        throw new Error(`bad task: ${raw} (expected feedback or feedbackDeep)`);
      }
      opts.task = raw;
    } else if (key === "only") {
      opts.only = raw.split(",");
    } else {
      throw new Error(`unknown flag: --${key}`);
    }
  }
  return opts;
}

/** single HTTP attempt — returns the transport outcome without judging its content. */
async function postVertexOnce(apiKey, model, body) {
  const res = await fetch(`${BASE_URL}/models/${model}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  return { ok: res.ok, status: res.status, text };
}

/**
 * POST with a single bounded retry. A default sweep is ~126 sequential live calls with
 * no backoff otherwise — a mid-sweep quota burst would degrade whole cells and a human
 * could compare temperatures across unequal sample sizes without noticing. Retry once on
 * 429 (quota) and 5xx (transient); a non-429 4xx is deterministic (bad request) and stays
 * terminal, mirroring the provider's own policy (src/providers/gemini.ts postWithRetry /
 * requestText). Kept simple on purpose: one retry, one fixed delay, no backoff library.
 */
async function callVertexWithRetry(apiKey, model, body) {
  let last = null;
  for (let attempt = 1; attempt <= 2; attempt++) {
    const res = await postVertexOnce(apiKey, model, body);
    if (res.ok) {
      return res.text;
    }
    last = res;
    const retryable = res.status === 429 || res.status >= 500;
    if (!retryable || attempt === 2) {
      throw new Error(`HTTP ${res.status}: ${res.text.slice(0, 300)}`);
    }
    await sleep(RETRY_DELAY_MS);
  }
  // unreachable — the loop above always returns or throws — but keeps control flow explicit.
  throw new Error(`HTTP ${last.status}: ${last.text.slice(0, 300)}`);
}

/**
 * Run one (case, temp, repeat) call end to end, keeping the transport step and the parse
 * step distinct: "JSON validity degrades as temperature rises" is the single most classic
 * finding a temperature sweep exists to produce, and conflating a parse failure with an
 * HTTP failure (both used to render as opaque `call-failed`) made this runner unable to
 * report it. NOTE: production's streaming path (feedback.ts / feedback-deep.ts) has NO
 * repair step — repair-once only exists in generateOnce's single-shot path (gemini.ts) —
 * so this pre-repair parse is the production-relevant number.
 */
async function runCase(apiKey, model, body, isDeep, expect) {
  const text = await callVertexWithRetry(apiKey, model, body);
  let json;
  try {
    json = extractJson(text);
  } catch (parseError) {
    return {
      json: null,
      violations: [],
      error: null,
      parseError: String((parseError && parseError.message) || parseError),
      rawText: text.slice(0, RAW_TEXT_KEEP_CHARS),
    };
  }
  const violations = isDeep ? validateDeep(json) : validateSlim(json, expect);
  return { json, violations, error: null, parseError: null, rawText: null };
}

/** Render slim segments as markdown so over-correction is visible at a glance. */
function renderSegments(segments) {
  if (!Array.isArray(segments)) return "_(no segments)_";
  return segments
    .map((s) => {
      const t = String(s && s.text !== undefined ? s.text : "");
      if (!s) return t;
      if (s.type === "incorrect") return `~~${t}~~`;
      if (s.type === "correction") return `**${t}**`;
      if (s.type === "highlight") return `__${t}__`;
      return t;
    })
    .join("");
}

function get(obj, ...keys) {
  let cur = obj;
  for (const k of keys) {
    if (cur === null || typeof cur !== "object") return undefined;
    cur = cur[k];
  }
  return cur;
}

/**
 * Per-temperature rollup. Splits violations into two buckets that used to be conflated:
 * `structuralErrors` (genuinely malformed output — the model broke a hard rule) vs
 * `expectMismatches` (an `expect.*` check — a case's SUBJECTIVE judgment about what the
 * ideal response looks like, e.g. `ab-possible-to-sit` asserting no over-correction on a
 * sentence where a correction is arguably defensible). Some expectations are debatable by
 * design and can fire at every temperature, adding a constant offset that would otherwise
 * bury the real (structural) signal a temperature change actually produces. Also splits
 * transport failures from parse failures (Fix 4) and reports `validated` — the number of
 * calls that both transported and parsed successfully — so unequal sample sizes across
 * temperatures (e.g. from a quota burst, Fix 5) stay visible instead of silently skewing
 * the score-spread comparison.
 */
function summarise(runs, temp) {
  const at = runs.filter((r) => r.temp === temp);
  const transportFailed = at.filter((r) => r.error).length;
  const parseFailed = at.filter((r) => r.parseError).length;
  const validated = at.length - transportFailed - parseFailed;

  let structuralErrors = 0;
  let expectMismatches = 0;
  let warns = 0;
  for (const r of at) {
    for (const v of r.violations) {
      if (v.check.startsWith("expect.")) {
        expectMismatches++;
      } else if (v.severity === "error") {
        structuralErrors++;
      } else {
        warns++;
      }
    }
  }

  // score spread per case: how far apart repeats of the SAME input land
  const spreads = [];
  const byCase = new Map();
  for (const r of at) {
    if (r.error || r.parseError) continue;
    const s = scoreOf(r.json);
    if (s === null) continue;
    if (!byCase.has(r.caseId)) byCase.set(r.caseId, []);
    byCase.get(r.caseId).push(s);
  }
  for (const scores of byCase.values()) {
    if (scores.length > 1) spreads.push(Math.max(...scores) - Math.min(...scores));
  }
  const meanSpread = spreads.length
    ? (spreads.reduce((a, b) => a + b, 0) / spreads.length).toFixed(1)
    : "n/a";
  const maxSpread = spreads.length ? String(Math.max(...spreads)) : "n/a";
  return {
    total: at.length,
    transportFailed,
    parseFailed,
    validated,
    structuralErrors,
    expectMismatches,
    warns,
    meanSpread,
    maxSpread,
  };
}

function writeReport(opts, cases, runs, model) {
  const outDir = path.join(__dirname, "out");
  fs.mkdirSync(outDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const file = path.join(outDir, `${opts.task}-${stamp}.md`);
  const isDeep = opts.task === "feedbackDeep";
  const lowestTemp = Math.min(...opts.temps);
  const L = [];

  L.push(`# ${opts.task} eval — ${stamp}`);
  L.push("");
  L.push(
    `model \`${model}\` · cases ${cases.length} · temps ${opts.temps.join(", ")} · repeats ${opts.repeats} · ${runs.length} calls`
  );
  L.push("");
  L.push("## 요약");
  L.push("");
  L.push(
    "| temperature | 호출 | 전송 실패 | 파싱 실패 | 검증됨 | 구조 위반 | 기대치 불일치 | warn 위반 | 점수 스프레드(평균) | 점수 스프레드(최대) |"
  );
  L.push("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |");
  for (const temp of opts.temps) {
    const s = summarise(runs, temp);
    L.push(
      `| ${temp} | ${s.total} | ${s.transportFailed} | ${s.parseFailed} | ${s.validated} | ${s.structuralErrors} | ${s.expectMismatches} | ${s.warns} | ${s.meanSpread} | ${s.maxSpread} |`
    );
  }
  L.push("");
  L.push(
    "**열 읽는 법**: `전송 실패`는 재시도 후에도 실패한 HTTP 호출(429/5xx는 1회 재시도됨, 그 외 4xx는 즉시 실패) — " +
    "quota burst 같은 인프라 문제다. `파싱 실패`는 응답은 왔지만 `JSON.parse`가 실패한 경우 — production 스트리밍 " +
    "경로에는 복구(repair) 단계가 없으므로(복구는 generateOnce 단발 호출 전용) 이 숫자가 production에서 실제로 " +
    "벌어질 파싱 성공률이다. `검증됨`은 전송·파싱 모두 성공해 구조 검증까지 돌린 호출 수 — 온도 간 표본 크기가 " +
    "달라지면(quota burst 등) 여기서 드러난다. `구조 위반`은 모델이 하드 룰을 어긴 진짜 결함(색상 누출, 스키마 " +
    "위반, 섹션 순서 뒤바뀜 등)이고, `기대치 불일치`는 각 케이스의 `expect.*`가 실패한 것 — 이는 채점 결과에 대한 " +
    "주관적 판단이며 일부는 설계상 논쟁의 여지가 있다(예: `ab-possible-to-sit`는 과잉 교정이 아니라고 보지만 이는 " +
    "논쟁 가능한 주장이다). 두 열을 하나로 합치면 매 온도에서 상수처럼 반복되는 기대치 불일치가 진짜 온도 신호를 " +
    "덮어버리므로 분리했다. `점수 스프레드` = 같은 입력을 반복 호출했을 때 나온 점수의 최대-최소 차이. 피드백은 " +
    "채점이므로 이 값이 작을수록 좋다."
  );
  L.push("");

  if (isDeep) {
    L.push(
      "**deep 태스크 안내**: deep 응답에는 `writingScore`가 없으므로 점수 스프레드는 항상 `n/a`다 — 점수를 억지로 " +
      "만들어내지 않는다. deep에서 온도를 고르는 지표는 위 요약 표의 **온도별 위반율**(`구조 위반` · `기대치 불일치`) " +
      "이다. 특히 스키마가 강제하지 않고 프롬프트로만 요구하는 카디널리티 규칙 — `toneStyle.levels`가 정확히 5개, " +
      "`paraphrasing`이 정확히 3개, `venn.items`가 1-3개 — 이 온도 상승에 가장 먼저 깨지는 규칙이다. " +
      "`FEEDBACK_TEMPERATURE` 자체는 slim 증거로부터 고른 값을 deep과 공유하며, 이 deep 리포트는 그 값이 deep에서 " +
      "위반을 늘리지 않는지 확인하는 용도다."
    );
    L.push("");
  }

  if (!isDeep) {
    L.push("## 케이스별 점수");
    L.push("");
    L.push(`| case | category | ${opts.temps.map((t) => `t=${t}`).join(" | ")} |`);
    L.push(`| --- | --- | ${opts.temps.map(() => "---").join(" | ")} |`);
    for (const c of cases) {
      const cells = opts.temps.map((temp) => {
        const scores = runs
          .filter((r) => r.caseId === c.id && r.temp === temp && !r.error && !r.parseError)
          .map((r) => scoreOf(r.json))
          .filter((s) => s !== null);
        return scores.length ? scores.join(" / ") : "—";
      });
      L.push(`| \`${c.id}\` | ${c.category} | ${cells.join(" | ")} |`);
    }
    L.push("");
  }

  // ── 레벨 민감도 (Fix 6): lv-starter/lv-expert side by side, lowest temp only ─────
  const levelVariancePair = cases.filter((c) => c.category === "level-variance");
  if (levelVariancePair.length === 2) {
    L.push("## 레벨 민감도 (level-variance)");
    L.push("");
    L.push(
      `\`${levelVariancePair[0].id}\`와 \`${levelVariancePair[1].id}\`는 영어 문장이 완전히 동일하고 \`level\`만 ` +
      "다르다 — 프롬프트는 `level`을 전혀 언급하지 않으므로, 두 출력이 비슷하다는 것은 레벨 인지 채점이 " +
      "구현되지 않았다는 증거다."
    );
    L.push("");
    L.push(
      `**이 비교는 스윕의 가장 낮은 온도(t=${lowestTemp})에서만 의미가 있다** — 온도가 높아질수록 두 출력의 차이는 ` +
      "레벨 인지가 아니라 단순 샘플링 노이즈와 뒤섞인다(confound). 따라서 \"두 출력이 비슷하다\"는 관찰은 " +
      `t=${lowestTemp}에서만 레벨 인지 부재의 증거로 읽어야 하고, 더 높은 온도에서의 차이/유사는 이 질문에 답하지 못한다.`
    );
    L.push("");
    for (const c of levelVariancePair) {
      const r = runs.find(
        (x) => x.caseId === c.id && x.temp === lowestTemp && !x.error && !x.parseError
      );
      L.push(`### \`${c.id}\` (level=\`${c.payload.level}\`) — t=${lowestTemp}`);
      L.push("");
      if (!r) {
        L.push("_이 온도에서 모든 반복 호출이 실패했거나 파싱에 실패했다._");
        L.push("");
        continue;
      }
      if (isDeep) {
        L.push("```json");
        L.push(JSON.stringify(r.json, null, 2));
        L.push("```");
      } else {
        const score = scoreOf(r.json);
        L.push(`- 점수: **${score === null ? "—" : score}** · incorrect 세그먼트 ${countIncorrectSegments(r.json)}개`);
        L.push(`- 교정: ${renderSegments(get(r.json, "grammar", "correctedSentence", "segments"))}`);
        L.push(`- 설명: ${get(r.json, "grammar", "explanation") || "—"}`);
      }
      L.push("");
    }
  }

  L.push("## 위반 목록");
  L.push("");
  const withViolations = runs.filter((r) => r.violations.length > 0 || r.error || r.parseError);
  if (withViolations.length === 0) {
    L.push("_구조 위반 없음._");
  } else {
    L.push("| case | t | #  | severity | check | detail |");
    L.push("| --- | --- | --- | --- | --- | --- |");
    for (const r of withViolations) {
      if (r.error) {
        L.push(`| \`${r.caseId}\` | ${r.temp} | ${r.repeat} | error | call-failed | ${r.error} |`);
      }
      if (r.parseError) {
        L.push(
          `| \`${r.caseId}\` | ${r.temp} | ${r.repeat} | error | parse-failed | ${r.parseError} (원문은 아래 "파싱 실패 원문" 참고) |`
        );
      }
      for (const v of r.violations) {
        L.push(
          `| \`${r.caseId}\` | ${r.temp} | ${r.repeat} | ${v.severity} | ${v.check} | ${String(v.detail).replace(/\|/g, "\\|")} |`
        );
      }
    }
  }
  L.push("");

  // ── 파싱 실패 원문 (Fix 4): keep the evidence, not just "call-failed" ────────────
  const parseFailures = runs.filter((r) => r.parseError);
  if (parseFailures.length > 0) {
    L.push("### 파싱 실패 원문");
    L.push("");
    L.push(
      `JSON.parse가 실패한 응답의 원문을 최대 ${RAW_TEXT_KEEP_CHARS}자까지 보존한다. production 스트리밍 경로에는 ` +
      "복구(repair) 단계가 없으므로(복구는 generateOnce 단발 호출 전용, gemini.ts) 이 원문이 곧 학습자가 실제로 " +
      "받았을 (깨진) 응답이다."
    );
    L.push("");
    for (const r of parseFailures) {
      L.push(`**\`${r.caseId}\` t=${r.temp} #${r.repeat}** — ${r.parseError}`);
      L.push("");
      L.push("```");
      L.push(r.rawText || "");
      L.push("```");
      L.push("");
    }
  }

  L.push("## 사람이 읽을 출력");
  L.push("");
  L.push(
    "구조 검증은 여기까지가 한계다. 아래는 **교정이 학습자에게 실제로 옳은가**를 눈으로 판정하기 위한 원문이다. 각 케이스의 `note`가 무엇을 봐야 하는지 알려준다."
  );
  L.push("");
  for (const c of cases) {
    L.push(`### \`${c.id}\` — ${c.category}`);
    L.push("");
    L.push(`> ${c.note}`);
    L.push("");
    L.push(`- 한국어: ${c.payload.koreanPrompt}`);
    L.push(`- 학습자 영어: \`${c.payload.userEnglish}\``);
    L.push(`- 참고 영어: \`${c.payload.referenceEnglish}\``);
    L.push(`- level: \`${c.payload.level}\``);
    L.push("");
    for (const temp of opts.temps) {
      // one representative run per temperature — the rest are in the score table
      const r = runs.find((x) => x.caseId === c.id && x.temp === temp && !x.error && !x.parseError);
      L.push(`**t=${temp}**`);
      L.push("");
      if (!r) {
        L.push("_모든 반복 호출이 실패했거나 파싱에 실패했다._");
        L.push("");
        continue;
      }
      if (isDeep) {
        L.push("```json");
        L.push(JSON.stringify(r.json, null, 2));
        L.push("```");
      } else {
        const score = scoreOf(r.json);
        L.push(`- 점수: **${score === null ? "—" : score}** · incorrect 세그먼트 ${countIncorrectSegments(r.json)}개`);
        L.push(`- 격려: ${get(r.json, "writingScore", "encouragementMessage") || "—"}`);
        L.push(`- 교정: ${renderSegments(get(r.json, "grammar", "correctedSentence", "segments"))}`);
        L.push(`- 설명: ${get(r.json, "grammar", "explanation") || "—"}`);
        L.push(`- 자연스러운 표현: ${renderSegments(get(r.json, "naturalExpression", "segments"))}`);
        L.push(
          `- 이유: **${get(r.json, "naturalExpression", "reason", "keyword") || "—"}** — ${get(r.json, "naturalExpression", "reason", "description") || "—"}`
        );
      }
      L.push("");
    }
  }

  fs.writeFileSync(file, L.join("\n"), "utf8");
  return file;
}

async function main() {
  const opts = parseArgs(process.argv);
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    console.error(
      "GEMINI_API_KEY is not set.\n" +
        "  GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) npm run eval\n" +
        "Do not write the key to a file."
    );
    process.exit(1);
  }

  const isDeep = opts.task === "feedbackDeep";
  const system = isDeep ? FEEDBACK_DEEP_SYSTEM_PROMPT : FEEDBACK_SYSTEM_PROMPT;
  const schema = isDeep ? FEEDBACK_DEEP_RESPONSE_SCHEMA : FEEDBACK_RESPONSE_SCHEMA;
  const model = modelFor(opts.task);
  const cases = opts.only ? CASES.filter((c) => opts.only.includes(c.id)) : CASES;
  if (cases.length === 0) throw new Error(`--only matched no cases: ${opts.only}`);

  const total = cases.length * opts.temps.length * opts.repeats;
  const runs = [];
  let n = 0;

  for (const temp of opts.temps) {
    for (const c of cases) {
      for (let repeat = 1; repeat <= opts.repeats; repeat++) {
        n++;
        process.stderr.write(`[${n}/${total}] ${c.id} t=${temp} #${repeat}\n`);
        const body = buildGenerateBody(c.payload, system, schema, { temperature: temp });
        try {
          const result = await runCase(apiKey, model, body, isDeep, c.expect);
          runs.push({ caseId: c.id, temp, repeat, ...result });
        } catch (e) {
          runs.push({
            caseId: c.id,
            temp,
            repeat,
            json: null,
            violations: [],
            error: String((e && e.message) || e),
            parseError: null,
            rawText: null,
          });
        }
        // short delay between every call — a zero-backoff ~126-call sweep is an easy way
        // to walk into a quota burst mid-run (Fix 5).
        await sleep(CALL_DELAY_MS);
      }
    }
  }

  const file = writeReport(opts, cases, runs, model);
  const transportFailures = runs.filter((r) => r.error).length;
  const parseFailures = runs.filter((r) => r.parseError).length;
  console.log(`\n리포트: ${file}`);
  console.log(
    `호출 ${runs.length}건 중 전송 실패 ${transportFailures}건 · 파싱 실패 ${parseFailures}건`
  );
  for (const temp of opts.temps) {
    const s = summarise(runs, temp);
    console.log(
      `  t=${temp}: 검증됨 ${s.validated}/${s.total} · 구조 위반 ${s.structuralErrors} · 기대치 불일치 ${s.expectMismatches} · warn ${s.warns} · 점수 스프레드 평균 ${s.meanSpread} / 최대 ${s.maxSpread}`
    );
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
