/**
 * Firestore 보안규칙 단위테스트 — Firestore 에뮬레이터 필요(오프라인 아님).
 * `firebase emulators:exec --only firestore "npm test"` 로 실행되며,
 * emulators:exec 가 FIRESTORE_EMULATOR_HOST 를 자식 프로세스에 주입한다.
 */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>"],
  testMatch: ["**/*.test.ts"],
  moduleFileExtensions: ["ts", "js", "json"],
  // 규칙 평가는 네트워크 왕복이라 기본 5s 로는 빠듯할 수 있다.
  testTimeout: 20000,
};
