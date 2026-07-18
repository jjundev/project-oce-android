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
 * This SPENDS REAL QUOTA: cases × temps × repeats calls (default 13 × 3 × 3 = 117).
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

async function callVertex(apiKey, model, body) {
  const res = await fetch(`${BASE_URL}/models/${model}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 300)}`);
  return extractJson(text);
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

function summarise(runs, temp) {
  const at = runs.filter((r) => r.temp === temp);
  const failed = at.filter((r) => r.error).length;
  let errors = 0;
  let warns = 0;
  for (const r of at) {
    for (const v of r.violations) {
      if (v.severity === "error") errors++;
      else warns++;
    }
  }
  // score spread per case: how far apart repeats of the SAME input land
  const spreads = [];
  const byCase = new Map();
  for (const r of at) {
    if (r.error) continue;
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
  return { failed, errors, warns, meanSpread, maxSpread, total: at.length };
}

function writeReport(opts, cases, runs, model) {
  const outDir = path.join(__dirname, "out");
  fs.mkdirSync(outDir, { recursive: true });
  const stamp = new Date().toISOString().replace(/[:.]/g, "-");
  const file = path.join(outDir, `${opts.task}-${stamp}.md`);
  const isDeep = opts.task === "feedbackDeep";
  const L = [];

  L.push(`# ${opts.task} eval — ${stamp}`);
  L.push("");
  L.push(
    `model \`${model}\` · cases ${cases.length} · temps ${opts.temps.join(", ")} · repeats ${opts.repeats} · ${runs.length} calls`
  );
  L.push("");
  L.push("## 요약");
  L.push("");
  L.push("| temperature | 호출 | 실패 | error 위반 | warn 위반 | 점수 스프레드(평균) | 점수 스프레드(최대) |");
  L.push("| --- | --- | --- | --- | --- | --- | --- |");
  for (const temp of opts.temps) {
    const s = summarise(runs, temp);
    L.push(
      `| ${temp} | ${s.total} | ${s.failed} | ${s.errors} | ${s.warns} | ${s.meanSpread} | ${s.maxSpread} |`
    );
  }
  L.push("");
  L.push(
    "점수 스프레드 = 같은 입력을 반복 호출했을 때 나온 점수의 최대-최소 차이. 피드백은 채점이므로 이 값이 작을수록 좋다."
  );
  L.push("");

  if (!isDeep) {
    L.push("## 케이스별 점수");
    L.push("");
    L.push(`| case | category | ${opts.temps.map((t) => `t=${t}`).join(" | ")} |`);
    L.push(`| --- | --- | ${opts.temps.map(() => "---").join(" | ")} |`);
    for (const c of cases) {
      const cells = opts.temps.map((temp) => {
        const scores = runs
          .filter((r) => r.caseId === c.id && r.temp === temp && !r.error)
          .map((r) => scoreOf(r.json))
          .filter((s) => s !== null);
        return scores.length ? scores.join(" / ") : "—";
      });
      L.push(`| \`${c.id}\` | ${c.category} | ${cells.join(" | ")} |`);
    }
    L.push("");
  }

  L.push("## 위반 목록");
  L.push("");
  const withViolations = runs.filter((r) => r.violations.length > 0 || r.error);
  if (withViolations.length === 0) {
    L.push("_구조 위반 없음._");
  } else {
    L.push("| case | t | #  | severity | check | detail |");
    L.push("| --- | --- | --- | --- | --- | --- |");
    for (const r of withViolations) {
      if (r.error) {
        L.push(`| \`${r.caseId}\` | ${r.temp} | ${r.repeat} | error | call-failed | ${r.error} |`);
      }
      for (const v of r.violations) {
        L.push(
          `| \`${r.caseId}\` | ${r.temp} | ${r.repeat} | ${v.severity} | ${v.check} | ${String(v.detail).replace(/\|/g, "\\|")} |`
        );
      }
    }
  }
  L.push("");

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
      const r = runs.find((x) => x.caseId === c.id && x.temp === temp && !x.error);
      L.push(`**t=${temp}**`);
      L.push("");
      if (!r) {
        L.push("_모든 반복 호출이 실패했다._");
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
        try {
          const body = buildGenerateBody(c.payload, system, schema, { temperature: temp });
          const json = await callVertex(apiKey, model, body);
          const violations = isDeep ? validateDeep(json) : validateSlim(json, c.expect);
          runs.push({ caseId: c.id, temp, repeat, json, violations, error: null });
        } catch (e) {
          runs.push({
            caseId: c.id,
            temp,
            repeat,
            json: null,
            violations: [],
            error: String((e && e.message) || e),
          });
        }
      }
    }
  }

  const file = writeReport(opts, cases, runs, model);
  const failures = runs.filter((r) => r.error).length;
  console.log(`\n리포트: ${file}`);
  console.log(`호출 ${runs.length}건 중 실패 ${failures}건`);
  for (const temp of opts.temps) {
    const s = summarise(runs, temp);
    console.log(
      `  t=${temp}: error ${s.errors} · warn ${s.warns} · 점수 스프레드 평균 ${s.meanSpread} / 최대 ${s.maxSpread}`
    );
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
