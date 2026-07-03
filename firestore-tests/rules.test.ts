/**
 * M0-08 — Firestore 보안규칙 단위테스트.
 *
 * 검증 대상: 루트 `../firestore.rules`(firestore-schema.md §6 verbatim).
 * AC(issues/M0-08): 사용자별 격리 + usage/config/sessions/idempotency 클라 쓰기 불가(carve-out).
 *
 * 실행: `firebase emulators:exec --only firestore "npm --prefix firestore-tests test"`.
 * emulators:exec 가 FIRESTORE_EMULATOR_HOST 를 주입한다. 없으면 127.0.0.1:8080(firebase.json #2)로 폴백.
 */
import { readFileSync } from "fs";
import { join } from "path";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  serverTimestamp,
  Timestamp,
  Firestore,
  setLogLevel,
} from "firebase/firestore";

let testEnv: RulesTestEnvironment;

const [host, portStr] = (process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080").split(":");

const ALICE = "alice";
const BOB = "bob";

// 규칙 우회 시딩 헬퍼 — 클라 규칙이 막는 초기 상태를 심을 때 사용.
async function seed(path: string, data: Record<string, unknown>): Promise<void> {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore() as unknown as Firestore, path), data);
  });
}

function aliceDb(): Firestore {
  return testEnv.authenticatedContext(ALICE).firestore() as unknown as Firestore;
}
function unauthDb(): Firestore {
  return testEnv.unauthenticatedContext().firestore() as unknown as Firestore;
}

beforeAll(async () => {
  setLogLevel("error"); // 규칙 거부 로그 소음 억제
  testEnv = await initializeTestEnvironment({
    projectId: "oce-v1",
    firestore: {
      host,
      port: Number(portStr),
      rules: readFileSync(join(__dirname, "../firestore.rules"), "utf8"),
    },
  });
});

afterAll(async () => {
  await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

describe("사용자별 데이터 격리", () => {
  it("자기 프로필은 읽고 쓸 수 있다", async () => {
    const db = aliceDb();
    await assertSucceeds(
      setDoc(doc(db, `users/${ALICE}`), { nickname: "A", createdAt: serverTimestamp() })
    );
    await assertSucceeds(getDoc(doc(db, `users/${ALICE}`)));
  });

  it("타인 프로필은 읽지 못한다", async () => {
    await seed(`users/${BOB}`, { nickname: "B", createdAt: Timestamp.fromMillis(1000) });
    await assertFails(getDoc(doc(aliceDb(), `users/${BOB}`)));
  });

  it("타인 프로필에 쓰지 못한다", async () => {
    await assertFails(setDoc(doc(aliceDb(), `users/${BOB}`), { nickname: "hijack" }));
  });

  it("타인 서브컬렉션(saved_cards)에 접근하지 못한다", async () => {
    await assertFails(getDoc(doc(aliceDb(), `users/${BOB}/saved_cards/c1`)));
    await assertFails(
      setDoc(doc(aliceDb(), `users/${BOB}/saved_cards/c1`), {
        cardType: "WORD",
        deletedAt: null,
      })
    );
  });
});

describe("users 루트 문서", () => {
  it("createdAt 을 바꾸지 않는 update 는 허용", async () => {
    const ts = Timestamp.fromMillis(1000);
    await seed(`users/${ALICE}`, { nickname: "A", createdAt: ts });
    await assertSucceeds(updateDoc(doc(aliceDb(), `users/${ALICE}`), { nickname: "A2" }));
  });

  it("createdAt 을 바꾸는 update 는 거부(불변)", async () => {
    await seed(`users/${ALICE}`, { nickname: "A", createdAt: Timestamp.fromMillis(1000) });
    await assertFails(
      updateDoc(doc(aliceDb(), `users/${ALICE}`), { createdAt: Timestamp.fromMillis(2000) })
    );
  });

  it("delete 는 거부", async () => {
    await seed(`users/${ALICE}`, { nickname: "A", createdAt: Timestamp.fromMillis(1000) });
    await assertFails(deleteDoc(doc(aliceDb(), `users/${ALICE}`)));
  });
});

describe("saved_cards (client RW, 톰스톤)", () => {
  const path = `users/${ALICE}/saved_cards/c1`;

  it("deletedAt==null + 유효 cardType 로 생성 허용", async () => {
    await assertSucceeds(
      setDoc(doc(aliceDb(), path), {
        cardType: "WORD",
        english: "hi",
        deletedAt: null,
        createdAt: serverTimestamp(),
      })
    );
  });

  it("deletedAt!=null 로 생성하면 거부(쿼리 일관성)", async () => {
    await assertFails(
      setDoc(doc(aliceDb(), path), {
        cardType: "WORD",
        deletedAt: Timestamp.fromMillis(1000),
        createdAt: serverTimestamp(),
      })
    );
  });

  it("잘못된 cardType 은 거부", async () => {
    await assertFails(
      setDoc(doc(aliceDb(), path), { cardType: "FOO", deletedAt: null, createdAt: serverTimestamp() })
    );
  });

  it("톰스톤(deletedAt set) update 는 허용", async () => {
    await seed(path, { cardType: "WORD", deletedAt: null, createdAt: Timestamp.fromMillis(1) });
    await assertSucceeds(updateDoc(doc(aliceDb(), path), { deletedAt: serverTimestamp() }));
  });

  it("하드 삭제(delete)는 거부", async () => {
    await seed(path, { cardType: "WORD", deletedAt: null, createdAt: Timestamp.fromMillis(1) });
    await assertFails(deleteDoc(doc(aliceDb(), path)));
  });
});

describe("point_ledger (create-only 불변, 서버시각 강제)", () => {
  const path = `users/${ALICE}/point_ledger/s1`;

  it("serverTimestamp + 유효 difficulty 로 생성 허용", async () => {
    await assertSucceeds(
      setDoc(doc(aliceDb(), path), {
        difficulty: "normal",
        modeId: "m1",
        awardedAt: serverTimestamp(),
      })
    );
  });

  it("클라 리터럴 timestamp(스푸핑)는 거부", async () => {
    await assertFails(
      setDoc(doc(aliceDb(), path), {
        difficulty: "normal",
        modeId: "m1",
        awardedAt: Timestamp.fromMillis(1000),
      })
    );
  });

  it("잘못된 difficulty 는 거부", async () => {
    await assertFails(
      setDoc(doc(aliceDb(), path), {
        difficulty: "impossible",
        modeId: "m1",
        awardedAt: serverTimestamp(),
      })
    );
  });

  it("update 는 거부(불변)", async () => {
    await seed(path, { difficulty: "easy", modeId: "m1", awardedAt: Timestamp.fromMillis(1) });
    await assertFails(updateDoc(doc(aliceDb(), path), { difficulty: "hard" }));
  });

  it("delete 는 거부(불변)", async () => {
    await seed(path, { difficulty: "easy", modeId: "m1", awardedAt: Timestamp.fromMillis(1) });
    await assertFails(deleteDoc(doc(aliceDb(), path)));
  });
});

describe("gamification/studytime (client RW, 단조 증가)", () => {
  const path = `users/${ALICE}/gamification/studytime`;

  it("생성 허용", async () => {
    await assertSucceeds(
      setDoc(doc(aliceDb(), path), { totalSeconds: 0, today: {}, updatedAt: serverTimestamp() })
    );
  });

  it("totalSeconds 증가 update 허용", async () => {
    await seed(path, { totalSeconds: 100 });
    await assertSucceeds(updateDoc(doc(aliceDb(), path), { totalSeconds: 200 }));
  });

  it("totalSeconds 감소 update 거부", async () => {
    await seed(path, { totalSeconds: 100 });
    await assertFails(updateDoc(doc(aliceDb(), path), { totalSeconds: 50 }));
  });
});

describe("carve-out — Functions 전용(읽기 허용, 클라 쓰기 거부)", () => {
  it("usage/{day}: 읽기 허용, 쓰기 거부", async () => {
    await seed(`users/${ALICE}/usage/20260703`, { sessionCount: 1 });
    await assertSucceeds(getDoc(doc(aliceDb(), `users/${ALICE}/usage/20260703`)));
    await assertFails(setDoc(doc(aliceDb(), `users/${ALICE}/usage/20260703`), { sessionCount: 0 }));
  });

  it("gamification/progress: 읽기 허용, 쓰기 거부", async () => {
    await seed(`users/${ALICE}/gamification/progress`, { xp: 10 });
    await assertSucceeds(getDoc(doc(aliceDb(), `users/${ALICE}/gamification/progress`)));
    await assertFails(setDoc(doc(aliceDb(), `users/${ALICE}/gamification/progress`), { xp: 9999 }));
  });

  it("progress_marks/{s}: 읽기 허용, 쓰기 거부", async () => {
    await seed(`users/${ALICE}/progress_marks/s1`, { processedAt: Timestamp.fromMillis(1) });
    await assertSucceeds(getDoc(doc(aliceDb(), `users/${ALICE}/progress_marks/s1`)));
    await assertFails(setDoc(doc(aliceDb(), `users/${ALICE}/progress_marks/s1`), {}));
  });
});

describe("carve-out — 서버 전용 루트 컬렉션(클라 default-deny)", () => {
  it("sessions/{s}: 읽기·쓰기 모두 거부", async () => {
    await seed("sessions/s1", { uid: ALICE });
    await assertFails(getDoc(doc(aliceDb(), "sessions/s1")));
    await assertFails(setDoc(doc(aliceDb(), "sessions/s1"), { uid: ALICE }));
  });

  it("idempotency/{k}: 읽기·쓰기 모두 거부", async () => {
    await seed("idempotency/k1", { sessionId: "s1" });
    await assertFails(getDoc(doc(aliceDb(), "idempotency/k1")));
    await assertFails(setDoc(doc(aliceDb(), "idempotency/k1"), { sessionId: "x" }));
  });

  it.each(["limits", "prompts", "models", "cache"])(
    "config/%s: 읽기·쓰기 모두 거부",
    async (name) => {
      await seed(`config/${name}`, { v: 1 });
      await assertFails(getDoc(doc(aliceDb(), `config/${name}`)));
      await assertFails(setDoc(doc(aliceDb(), `config/${name}`), { v: 2 }));
    }
  );
});

describe("config/topics (client READ-only)", () => {
  beforeEach(async () => {
    await seed("config/topics", { id: "cafe-order" });
  });

  it("미인증 읽기는 거부", async () => {
    await assertFails(getDoc(doc(unauthDb(), "config/topics")));
  });

  it("인증 읽기는 허용", async () => {
    await assertSucceeds(getDoc(doc(aliceDb(), "config/topics")));
  });

  it("인증 쓰기는 거부", async () => {
    await assertFails(setDoc(doc(aliceDb(), "config/topics"), { id: "hacked" }));
  });
});
