import {
  LLM_MIN_INSTANCES_DEFAULT,
  LLM_REGION,
  LLM_SECRET_NAME,
} from "../src/llm/options";

// Guards the onRequest runtime options (region/secret/min-instances) against
// silent typos — handler.ts is otherwise unit-test-blind.
describe("llm runtime options", () => {
  it("pins the Seoul region (Firestore colocation)", () => {
    expect(LLM_REGION).toBe("asia-northeast3");
  });

  it("binds the Gemini secret by name", () => {
    expect(LLM_SECRET_NAME).toBe("GEMINI_API_KEY");
  });

  it("defaults min-instances to 0 for cost optimization (Scale-to-Zero)", () => {
    expect(LLM_MIN_INSTANCES_DEFAULT).toBe(0);
  });
});
