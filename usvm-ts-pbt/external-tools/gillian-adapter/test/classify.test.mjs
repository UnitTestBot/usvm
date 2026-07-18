import assert from "node:assert/strict";
import test from "node:test";
import { classifyManifest } from "../src/classify.mjs";

test("primitive free/static entries are separable from custom heap harnesses", () => {
  const report = classifyManifest({ schemaVersion: 1, methods: [
    { methodId: "primitive", entryKind: "free", parameters: [
      { index: 0, type: "number", optional: false, rest: false },
      { index: 1, type: "string", optional: false, rest: false },
    ] },
    { methodId: "heap", entryKind: "instance", parameters: [
      { index: 0, type: "number[]", optional: false, rest: false },
    ] },
  ] });
  assert.deepEqual(report.summary, { methods: 2, automaticSymbolDeclarations: 1, customHarness: 1 });
  assert.deepEqual(report.methods[0].symbolicDeclarations, ["symb_number()", "symb_string()"]);
  assert.match(report.methods[1].reasons.join(" "), /receiver construction/);
});
