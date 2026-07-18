# 턴 피드백 eval 하네스

`feedback.slim` / `feedback.deep`의 출력을 골든 케이스 세트로 반복 측정한다.
실제 Vertex AI를 호출하므로 **쿼터를 소모한다** — jest 스위트(`npm test`)와는 완전히 분리되어 있다.

## 구성

| 위치 | 역할 |
| --- | --- |
| `src/eval/cases.ts` | 골든 케이스 13개(6 카테고리). 순수 데이터 |
| `src/eval/validate.ts` | 구조 검증기. 순수 함수, `npm test`로 오프라인 검증됨 |
| `eval/run.js` | 실 호출 러너. 마크다운 리포트를 `eval/out/`에 쓴다(gitignore) |

## 실행

```bash
GEMINI_API_KEY=$(firebase functions:secrets:access GEMINI_API_KEY) \
  npm run eval -- --temps=0,0.3,0.7 --repeats=3 --task=feedback
```

키를 파일에 쓰거나 커밋하지 말 것 — 환경변수로만 전달한다.

### 플래그

| 플래그 | 기본값 | 설명 |
| --- | --- | --- |
| `--temps` | `0,0.3,0.7` | 스윕할 temperature 목록 |
| `--repeats` | `3` | 케이스·온도당 반복 호출 수. 점수 분산 측정에 쓰인다 |
| `--task` | `feedback` | `feedback` 또는 `feedbackDeep` |
| `--only` | (전체) | 케이스 id를 콤마로. 예: `--only=ag-coffee-please,tg-past-tense` |

호출 수 = 케이스 × 온도 × 반복. 기본값이면 13 × 3 × 3 = 117회.

## 리포트 읽는 법

1. **요약 표** — 온도별 구조 위반 수와 점수 스프레드. 피드백은 채점이므로 스프레드가 작을수록 좋다.
2. **케이스별 점수** — 같은 입력의 반복 점수가 나란히 찍힌다. 흔들리는 케이스가 바로 보인다.
3. **위반 목록** — 기계가 잡은 것 전부.
4. **사람이 읽을 출력** — 구조 검증이 닿지 못하는 것, 즉 *이 교정이 학습자에게 실제로 옳은가*를 눈으로 판정하는 구역. 각 케이스의 `note`가 무엇을 봐야 하는지 알려준다.

특히 `already-good` 카테고리를 먼저 볼 것 — 고칠 게 없는 문장에 억지 교정을 만들어내는지가 가장 잘 드러난다.
