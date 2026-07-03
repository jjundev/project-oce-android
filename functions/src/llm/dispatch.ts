/**
 * task dispatch — validates the task and resolves its response transport.
 * Handler bodies are stubs in the scaffold (real behavior in M1+).
 * SoT: backend-functions.md:46 (tasks), :50-52 (SSE vs JSON per task).
 */
import { ResponseMode, Task } from "../types/protocol";

export const TASKS: readonly Task[] = [
  "dialogue",
  "speaking",
  "feedback",
  "summary",
  "tts",
];

const RESPONSE_MODE: Record<Task, ResponseMode> = {
  dialogue: "sse",
  feedback: "sse",
  summary: "sse",
  speaking: "json",
  tts: "json",
};

/** type guard — is the value one of the five known tasks? */
export function isTask(value: unknown): value is Task {
  return (
    typeof value === "string" && (TASKS as readonly string[]).includes(value)
  );
}

/** transport for a (validated) task */
export function responseModeFor(task: Task): ResponseMode {
  return RESPONSE_MODE[task];
}
