import { readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { encodeValue } from "./value-codec.mjs";
import { defaultArguments, symbolName } from "./target.mjs";

export async function exportExpoSeCorpus({ rawPath, outPath, method, methodId, harnessPath, producer }) {
  const raw = JSON.parse(await readFile(rawPath, "utf8"));
  const harness = await loadCommonJs(harnessPath);
  const initial = defaultArguments(method);
  const cases = [];
  const rejections = [];
  for (const path of raw.done ?? []) {
    try {
      const args = initial.map((fallback, index) => Object.hasOwn(path.input ?? {}, symbolName(index))
        ? path.input[symbolName(index)]
        : fallback);
      const mapped = typeof harness.toCorpusCase === "function"
        ? await harness.toCorpusCase(args)
        : { receiver: undefined, arguments: args };
      if (!mapped || !Array.isArray(mapped.arguments)) {
        throw new Error("toCorpusCase(args) must return { receiver?, arguments: [...] }");
      }
      const seen = new WeakSet();
      cases.push({
        id: `path-${path.id}`,
        methodId,
        receiver: encodeValue(mapped.receiver, seen, "$receiver"),
        arguments: mapped.arguments.map((value, index) => encodeValue(value, seen, `$arguments[${index}]`)),
        metadata: {
          pathId: String(path.id),
          bound: String(path.input?._bound ?? ""),
          pathTimeMs: String(path.time ?? ""),
          alternatives: String(path.alternatives ?? ""),
          errors: String(path.errors?.length ?? 0),
        },
      });
    } catch (error) {
      rejections.push({ id: path.id, reason: error instanceof Error ? error.message : String(error) });
    }
  }
  const corpus = { schemaVersion: 1, producer, cases };
  await writeFile(outPath, `${JSON.stringify(corpus, null, 2)}\n`, "utf8");
  return {
    exploredPaths: raw.done?.length ?? 0,
    exportedCases: cases.length,
    pathErrors: (raw.done ?? []).filter((path) => (path.errors?.length ?? 0) > 0).length,
    elapsedMs: Number(raw.end ?? 0) - Number(raw.start ?? 0),
    rejections,
  };
}

async function loadCommonJs(path) {
  const { createRequire } = await import("node:module");
  return createRequire(import.meta.url)(resolve(path));
}
