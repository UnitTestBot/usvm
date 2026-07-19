import { readFile, writeFile } from "node:fs/promises";

const args = process.argv.slice(2);
const mode = value("--mode") ?? "success";
const request = JSON.parse(await readFile(value("--request"), "utf8"));
const resultPath = value("--result");
await writeFile(resultPath, `${JSON.stringify({
  status: "success",
  cases: [{
    id: "checkpoint-case",
    generatedAtMs: 1,
    encodedArguments: [],
  }],
  objectives: [{
    nativeTargetId: request.harness.objectiveRequests[0]?.expectedNativeObjectiveKey ?? "native-empty",
    covered: true,
    discoveredAtMs: 1,
  }],
  diagnostics: {
    receivedInitialCorpus: Array.isArray(request.initialCorpus),
  },
})}\n`, "utf8");

if (mode === "failure") process.exitCode = 7;
if (mode === "timeout") setInterval(() => {}, 1000);

function value(name) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : null;
}
