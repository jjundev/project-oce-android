/**
 * Cache key derivation for prompt caching — backend-functions.md §6 (:78).
 *
 * Named `cacheKey.ts` (NOT `config/cache.ts`) to avoid colliding with the SoT's
 * `config/cache` Firestore document, which holds live `cachedContents` handles
 * (backend-functions.md:127). This module is a pure helper; the Firestore-backed
 * explicit-cache layer is deferred.
 *
 * Key = (task, promptVersion, modelId). For summary the "task" is the sub-task string
 * `summary.{section}` — the SoT explicitly names the three sub-tasks as the cache tasks
 * (backend-functions.md:116), so keying by sub-task is sanctioned, not a deviation.
 */

/**
 * Minimum prompt size (tokens) for an explicit `cachedContents` entry to be worthwhile
 * (~1024 for 2.5 Flash — prompt-system.md:14). Below this, callers use the inline
 * system-instruction path instead (decision #9).
 */
export const MIN_CACHE_TOKENS_FLOOR = 1024;

/** Stable cache handle key for (task, promptVersion, modelId). */
export function cacheKey(
  task: string,
  promptVersion: string,
  modelId: string
): string {
  return `${task}::${promptVersion}::${modelId}`;
}

/**
 * Whether a prompt is too small for explicit `cachedContents` and should ride the
 * inline system-instruction path instead. Summary prompts fall here (decision #9).
 */
export function useInlineFallback(estimatedTokens: number): boolean {
  return estimatedTokens < MIN_CACHE_TOKENS_FLOOR;
}
