import {
  IncrementalDialogueParser,
  InvalidDialoguePayloadError,
  parseDialoguePayload,
} from "../src/llm/dialogue";

describe("parseDialoguePayload", () => {
  it("accepts a valid non-first-session payload", () => {
    expect(
      parseDialoguePayload({
        level: "normal",
        topic: "여행 계획",
        length: 10,
        firstSession: false,
      })
    ).toEqual({ level: "normal", topic: "여행 계획", length: 10, firstSession: false });
  });

  it("coerces firstSession → easy/5 regardless of supplied level/length", () => {
    expect(
      parseDialoguePayload({
        level: "hard",
        topic: "커피 주문",
        length: 10,
        firstSession: true,
      })
    ).toEqual({ level: "easy", topic: "커피 주문", length: 5, firstSession: true });
  });

  it("rejects a missing topic", () => {
    expect(() =>
      parseDialoguePayload({ level: "easy", topic: "  ", length: 5, firstSession: false })
    ).toThrow(InvalidDialoguePayloadError);
  });

  it("rejects an out-of-range level (non-first-session)", () => {
    expect(() =>
      parseDialoguePayload({ level: "legendary", topic: "t", length: 6, firstSession: false })
    ).toThrow(InvalidDialoguePayloadError);
  });

  it("rejects an out-of-range length (non-first-session)", () => {
    expect(() =>
      parseDialoguePayload({ level: "easy", topic: "t", length: 7, firstSession: false })
    ).toThrow(InvalidDialoguePayloadError);
  });
});

/** canonical generated object: metadata fields first (propertyOrdering), then script. */
const FULL_JSON =
  '{"topic":"커피 주문","opponentName":"Barista","opponentGender":"female",' +
  '"opponentRole":"Barista","script":[' +
  '{"ko":"안녕하세요, 뭐 드릴까요?","en":"Hi, what can I get you?","role":"model"},' +
  '{"ko":"아메리카노 한 잔 주세요.","en":"An americano, please.","role":"user"}' +
  "]}";

describe("IncrementalDialogueParser — full object", () => {
  it("emits metadata once (all four fields) and both turns as typed objects", () => {
    const parser = new IncrementalDialogueParser();
    const update = parser.addChunk(FULL_JSON);
    expect(update.meta).toEqual({
      topic: "커피 주문",
      opponentName: "Barista",
      opponentGender: "female",
      opponentRole: "Barista",
    });
    expect(update.turns).toEqual([
      { ko: "안녕하세요, 뭐 드릴까요?", en: "Hi, what can I get you?", role: "model" },
      { ko: "아메리카노 한 잔 주세요.", en: "An americano, please.", role: "user" },
    ]);
  });

  it("does not re-emit metadata on a subsequent chunk", () => {
    const parser = new IncrementalDialogueParser();
    parser.addChunk(FULL_JSON);
    const again = parser.addChunk("");
    expect(again.meta).toBeUndefined();
    expect(again.turns).toEqual([]);
  });
});

describe("IncrementalDialogueParser — streaming boundaries", () => {
  it("withholds metadata until opponentRole (the 4th field) has fully arrived", () => {
    const parser = new IncrementalDialogueParser();
    // topic + name + gender present, opponentRole value still streaming → no meta yet.
    const partial = parser.addChunk(
      '{"topic":"커피 주문","opponentName":"Barista","opponentGender":"female","opponentRole":"Bari'
    );
    expect(partial.meta).toBeUndefined();
    // finish opponentRole + open the script array.
    const done = parser.addChunk('sta","script":[');
    expect(done.meta).toEqual({
      topic: "커피 주문",
      opponentName: "Barista",
      opponentGender: "female",
      opponentRole: "Barista",
    });
  });

  it("does not emit a partial turn until its closing brace arrives (no partial JSON leak)", () => {
    const parser = new IncrementalDialogueParser();
    const head = FULL_JSON.slice(0, FULL_JSON.indexOf("role\":\"model") + 5); // mid first turn
    const first = parser.addChunk(head);
    expect(first.turns).toEqual([]); // first turn not yet closed

    const rest = FULL_JSON.slice(head.length);
    const second = parser.addChunk(rest);
    expect(second.turns).toEqual([
      { ko: "안녕하세요, 뭐 드릴까요?", en: "Hi, what can I get you?", role: "model" },
      { ko: "아메리카노 한 잔 주세요.", en: "An americano, please.", role: "user" },
    ]);
  });

  it("emits each turn exactly once across many small chunks, in order", () => {
    const parser = new IncrementalDialogueParser();
    const collected: string[] = [];
    let meta = 0;
    for (const ch of FULL_JSON) {
      const u = parser.addChunk(ch);
      if (u.meta) meta++;
      for (const t of u.turns) collected.push(t.en);
    }
    expect(meta).toBe(1);
    expect(collected).toEqual(["Hi, what can I get you?", "An americano, please."]);
  });
});

describe("IncrementalDialogueParser — brace/quote safety", () => {
  it("does not miscount braces or quotes inside string values", () => {
    const json =
      '{"topic":"주제","opponentName":"N","opponentGender":"male","opponentRole":"R","script":[' +
      '{"ko":"중괄호 {test} 포함","en":"He said \\"hi\\" and used {curly}","role":"model"}]}';
    const parser = new IncrementalDialogueParser();
    const update = parser.addChunk(json);
    expect(update.turns).toEqual([
      { ko: "중괄호 {test} 포함", en: 'He said "hi" and used {curly}', role: "model" },
    ]);
  });

  it("tolerates a truncated stream: only completed turns, no throw", () => {
    const truncated = FULL_JSON.slice(0, FULL_JSON.length - 20); // drop the tail
    const parser = new IncrementalDialogueParser();
    const update = parser.addChunk(truncated);
    // first turn completed, second cut off → only the first is emitted.
    expect(update.turns).toEqual([
      { ko: "안녕하세요, 뭐 드릴까요?", en: "Hi, what can I get you?", role: "model" },
    ]);
  });
});
