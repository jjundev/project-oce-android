import { isTask, responseModeFor, TASKS } from "../src/llm/dispatch";

describe("task dispatch", () => {
  it("recognizes exactly the known tasks", () => {
    expect([...TASKS].sort()).toEqual(
      ["dialogue", "feedback", "feedbackDeep", "speaking", "summary", "tts"].sort()
    );
    for (const t of TASKS) {
      expect(isTask(t)).toBe(true);
    }
  });

  it("rejects unknown / malformed tasks", () => {
    expect(isTask("bogus")).toBe(false);
    expect(isTask(undefined)).toBe(false);
    expect(isTask(42)).toBe(false);
  });

  it("maps SSE tasks to sse", () => {
    expect(responseModeFor("dialogue")).toBe("sse");
    expect(responseModeFor("feedback")).toBe("sse");
    expect(responseModeFor("feedbackDeep")).toBe("sse");
    expect(responseModeFor("summary")).toBe("sse");
  });

  it("maps single-shot tasks to json", () => {
    expect(responseModeFor("speaking")).toBe("json");
    expect(responseModeFor("tts")).toBe("json");
  });
});
