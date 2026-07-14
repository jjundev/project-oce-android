import { LEVEL_TOKENS, CEFR_BAND, isEven6to20 } from "../src/config/levels";

describe("levels SoT", () => {
  it("lists 5 tokens easiest→hardest", () => {
    expect([...LEVEL_TOKENS]).toEqual(["starter", "easy", "normal", "hard", "expert"]);
  });
  it("maps every token to a CEFR band", () => {
    expect(CEFR_BAND).toEqual({
      starter: "A1", easy: "A2", normal: "B1", hard: "B2", expert: "C1",
    });
  });
  it("accepts even 6..20 only", () => {
    expect(isEven6to20(6)).toBe(true);
    expect(isEven6to20(20)).toBe(true);
    expect(isEven6to20(10)).toBe(true);
    expect(isEven6to20(5)).toBe(false);   // odd
    expect(isEven6to20(7)).toBe(false);   // odd
    expect(isEven6to20(4)).toBe(false);   // below floor
    expect(isEven6to20(22)).toBe(false);  // above ceiling
    expect(isEven6to20(10.5)).toBe(false);
  });
});
