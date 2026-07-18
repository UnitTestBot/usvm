import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { GREATEST_LOWER_BOUND, LEAST_UPPER_BOUND, TraceMap, originalPositionFor } from "@jridgewell/trace-mapping";

export async function remapExpoSeCoverage(coverage, sourceMapPaths, { generatedLineOffset = 0 } = {}) {
  if (sourceMapPaths.length === 0) return coverage;
  const maps = await Promise.all(sourceMapPaths.map(loadSourceMap));
  return {
    ...coverage,
    finalCoverage: (coverage.finalCoverage ?? []).map((fileCoverage) => {
      const sourceMap = bestSourceMap(fileCoverage.file, maps);
      if (!sourceMap) return fileCoverage;
      const remappedSmap = {};
      for (const [iid, location] of Object.entries(fileCoverage.smap ?? {})) {
        const start = originalPositionFor(sourceMap.trace, {
          line: Number(location[0]) + generatedLineOffset, column: Math.max(0, Number(location[1]) - 1), bias: GREATEST_LOWER_BOUND,
        });
        let end = originalPositionFor(sourceMap.trace, {
          line: Number(location[2]) + generatedLineOffset, column: Math.max(0, Number(location[3]) - 1), bias: LEAST_UPPER_BOUND,
        });
        if (end.line == null) {
          end = originalPositionFor(sourceMap.trace, {
            line: Number(location[2]) + generatedLineOffset, column: Math.max(0, Number(location[3]) - 2), bias: GREATEST_LOWER_BOUND,
          });
        }
        if (start.line == null || start.column == null || !start.source || end.line == null || end.column == null) continue;
        const startFile = resolveSource(sourceMap.path, start.source);
        const endFile = end.source ? resolveSource(sourceMap.path, end.source) : startFile;
        if (startFile !== endFile) continue;
        remappedSmap[iid] = {
          fileName: startFile,
          startLine: start.line - 1,
          startColumn: start.column,
          endLine: end.line - 1,
          endColumn: end.column,
        };
      }
      return { ...fileCoverage, remappedSmap };
    }),
  };
}

async function loadSourceMap(path) {
  const json = JSON.parse(await readFile(path, "utf8"));
  const generated = resolve(dirname(path), json.file ?? path.replace(/\.map$/, "").split("/").pop());
  return { path: resolve(path), generated, trace: new TraceMap(json, resolve(path)) };
}

function bestSourceMap(generatedFile, maps) {
  const normalized = normalize(generatedFile);
  const exact = maps.filter((item) => normalize(item.generated) === normalized);
  if (exact.length === 1) return exact[0];
  const suffix = maps.filter((item) => normalized.endsWith(`/${normalize(item.generated).split("/").pop()}`));
  return suffix.length === 1 ? suffix[0] : null;
}

function resolveSource(mapPath, source) {
  if (source.startsWith("file://")) return decodeURIComponent(new URL(source).pathname);
  return resolve(dirname(mapPath), source);
}

function normalize(path) {
  return String(path).replaceAll("\\", "/");
}
