import { readFileSync } from "fs";
import { join } from "path";

// Regression guard for backend-functions.md:58 / decision 9: the SSE transport
// rules forbid any compression middleware (it re-batches the stream and voids
// NFR-3). A code comment isn't enough — this fails CI if `compression` is ever
// added as a direct dependency.
describe("no compression middleware", () => {
  const pkg = JSON.parse(
    readFileSync(join(__dirname, "..", "package.json"), "utf8")
  ) as {
    dependencies?: Record<string, string>;
    devDependencies?: Record<string, string>;
  };

  it("is absent from dependencies", () => {
    expect(pkg.dependencies ?? {}).not.toHaveProperty("compression");
  });

  it("is absent from devDependencies", () => {
    expect(pkg.devDependencies ?? {}).not.toHaveProperty("compression");
  });
});
