# Functions Cost Optimization (MinInstances 0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change Cloud Functions `minInstances` configuration from 1 to 0 (`Scale-to-Zero`) to eliminate 24/7 idle server warming costs for single-user environment.

**Architecture:** Update default `LLM_MIN_INSTANCES_DEFAULT` in `options.ts` and runtime env `.env.oce-v1` from 1 to 0, update corresponding JSDoc comments in `options.ts` & `handler.ts`, update unit tests in `options.test.ts`, and update documentation in `functions/README.md`.

**Tech Stack:** Firebase Functions v2, TypeScript, Jest

## Global Constraints

- Keep existing function signatures intact.
- Ensure all unit tests in `functions/` pass clean (`npm --prefix functions test`).

---

### Task 1: Update Cloud Functions min-instances default & env configuration to 0

**Files:**
- Modify: `functions/src/llm/options.ts:18-22`
- Modify: `functions/src/llm/handler.ts:23-30`
- Modify: `functions/.env.oce-v1:1`
- Test: `functions/test/options.test.ts:18-20`

**Interfaces:**
- Consumes: None
- Produces: `LLM_MIN_INSTANCES_DEFAULT = 0` exported from `functions/src/llm/options.ts`

- [ ] **Step 1: Write the failing unit test update**

Update `functions/test/options.test.ts` to assert `LLM_MIN_INSTANCES_DEFAULT` is 0:

```typescript
it("defaults min-instances to 0 for cost optimization (Scale-to-Zero)", () => {
  expect(LLM_MIN_INSTANCES_DEFAULT).toBe(0);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm --prefix functions test`
Expected: FAIL with `expect(received).toBe(expected) // Expected: 0, Received: 1`

- [ ] **Step 3: Update options.ts, handler.ts JSDoc, and .env.oce-v1 to 0**

In `functions/src/llm/options.ts`:
```typescript
/**
 * Production min-instances default. Set to 0 for single-user scale-to-zero
 * cost optimization.
 */
export const LLM_MIN_INSTANCES_DEFAULT = 0;
```

In `functions/src/llm/handler.ts`:
```typescript
/**
 * Warming instances for /llm. Set to 0 default for scale-to-zero cost optimization.
 */
export const LLM_MIN_INSTANCES = defineInt(LLM_MIN_INSTANCES_PARAM, {
  default: LLM_MIN_INSTANCES_DEFAULT,
});
```

In `functions/.env.oce-v1`:
```env
LLM_MIN_INSTANCES=0
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm --prefix functions test`
Expected: PASS all tests in `functions/`

- [ ] **Step 5: Commit changes**

```bash
git add functions/src/llm/options.ts functions/src/llm/handler.ts functions/.env.oce-v1 functions/test/options.test.ts
git commit -m "fix(functions): default minInstances to 0 for cost optimization"
```

---

### Task 2: Update documentation and verify build

**Files:**
- Modify: `functions/README.md`

**Interfaces:**
- Consumes: Updated `LLM_MIN_INSTANCES_DEFAULT` = 0
- Produces: Documentation reflecting scale-to-zero cost optimization mode

- [ ] **Step 1: Update functions/README.md**

Update the min-instances section in `functions/README.md` to note that `LLM_MIN_INSTANCES` is defaulted to 0 for cost-optimized single-user deployment.

- [ ] **Step 2: Run build to verify clean compilation**

Run: `npm --prefix functions run build`
Expected: PASS with zero TypeScript errors.

- [ ] **Step 3: Commit documentation and build check**

```bash
git add functions/README.md
git commit -m "docs(functions): document minInstances 0 scale-to-zero cost optimization"
```
