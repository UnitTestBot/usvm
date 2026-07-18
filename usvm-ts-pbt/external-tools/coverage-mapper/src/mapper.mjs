import { basename } from "node:path";

export function mapIstanbulCoverage(manifest, coverage) {
  validateManifest(manifest);
  const sourceBranches = [];
  for (const [coverageKey, fileCoverage] of Object.entries(coverage)) {
    const fileName = fileCoverage.path ?? coverageKey;
    for (const [branchKey, branch] of Object.entries(fileCoverage.branchMap ?? {})) {
      const counts = fileCoverage.b?.[branchKey] ?? [];
      (branch.locations ?? []).forEach((location, armIndex) => {
        sourceBranches.push({
          toolBranchId: `istanbul:${normalizePath(fileName)}:${branchKey}:${armIndex}`,
          fileName,
          branchKey,
          branchType: branch.type ?? "unknown",
          armIndex,
          count: Number(counts[armIndex] ?? 0),
          origin: istanbulLocation(location),
          conditionOrigin: istanbulLocation(branch.loc),
        });
      });
    }
  }
  return buildReport("istanbul", manifest, sourceBranches, ({ edge, method, candidates }) => {
    const successorMatches = candidates.filter((candidate) => originsOverlap(edge.successorOrigin, candidate.origin));
    const mostSpecific = uniqueMostSpecific(successorMatches);
    if (mostSpecific.length > 0) return mostSpecific;

    // Istanbul orders if/ternary locations as true then false. Use this only
    // when source-arm matching failed and the condition itself is unambiguous.
    const siblingCount = method.branches.filter((item) => item.ifStmtIndex === edge.ifStmtIndex).length;
    return candidates.filter((candidate) =>
      (candidate.branchType === "if" || candidate.branchType === "cond-expr") &&
      candidate.armIndex === edge.successorOrdinal &&
      siblingCount === candidates.filter((item) => item.branchKey === candidate.branchKey).length &&
      originsOverlap(edge.conditionOrigin, candidate.conditionOrigin));
  });
}

export function mapV8Coverage(manifest, coverage) {
  validateManifest(manifest);
  const scripts = Array.isArray(coverage) ? coverage.flatMap((entry) => entry.result ?? []) : coverage.result ?? [];
  const ranges = [];
  scripts.forEach((script) => {
    (script.functions ?? []).forEach((fn, functionIndex) => {
      if (fn.isBlockCoverage === false) return;
      (fn.ranges ?? []).forEach((range, rangeIndex) => {
        ranges.push({
          toolBranchId: `v8:${normalizePath(script.url)}:${functionIndex}:${rangeIndex}`,
          fileName: stripFileUrl(script.url),
          functionName: fn.functionName ?? "",
          count: Number(range.count ?? 0),
          origin: {
            fileName: stripFileUrl(script.url),
            startOffset: Number(range.startOffset),
            endOffset: Number(range.endOffset),
          },
        });
      });
    });
  });
  return buildReport("v8-precise", manifest, ranges, ({ edge, candidates }) => {
    const containing = candidates.filter((candidate) => originContains(candidate.origin, edge.successorOrigin));
    return uniqueMostSpecific(containing);
  }, {
    note: "Raw V8 offsets are mapped only when script URL resolves to the same source file as the manifest; compiled JS requires c8/Istanbul source-map remapping first.",
  });
}

export function mapExpoSeCoverage(manifest, coverage) {
  validateManifest(manifest);
  const sourceBranches = [];
  for (const fileCoverage of coverage.finalCoverage ?? []) {
    for (const [iid, rawLocation] of Object.entries(fileCoverage.smap ?? {})) {
      const numericIid = Number(iid);
      if (!Number.isInteger(numericIid) || numericIid % 4 !== 0 || !Array.isArray(rawLocation)) continue;
      const flags = Number(fileCoverage.branches?.[iid] ?? 0);
      const remapped = fileCoverage.remappedSmap?.[iid];
      const fileName = remapped?.fileName ?? fileCoverage.file;
      const origin = remapped ?? {
          startLine: Number(rawLocation[0]) - 1,
          startColumn: Number(rawLocation[1]) - 1,
          endLine: Number(rawLocation[2]) - 1,
          endColumn: Number(rawLocation[3]) - 1,
        };
      [
        { armIndex: 0, count: flags & 0x2 ? 1 : 0 },
        { armIndex: 1, count: flags & 0x4 ? 1 : 0 },
      ].forEach(({ armIndex, count }) => sourceBranches.push({
        toolBranchId: `expose:${normalizePath(fileName)}:${iid}:${armIndex}`,
        fileName,
        branchKey: iid,
        branchType: "if",
        armIndex,
        count,
        origin,
        conditionOrigin: origin,
      }));
    }
  }
  return buildReport("expose-jalangi", manifest, sourceBranches, ({ edge, method, candidates }) => {
    const siblingCount = method.branches.filter((item) => item.ifStmtIndex === edge.ifStmtIndex).length;
    const sameCondition = candidates.filter((candidate) =>
      candidate.armIndex === edge.successorOrdinal &&
      siblingCount === candidates.filter((item) => item.branchKey === candidate.branchKey).length &&
      originsOverlap(edge.conditionOrigin, candidate.conditionOrigin));
    const conditionSpecific = uniqueMostSpecific(sameCondition);
    if (conditionSpecific.length > 0) return conditionSpecific;
    return uniqueMostSpecific(candidates.filter((candidate) => originsOverlap(edge.successorOrigin, candidate.origin)));
  }, {
    note: "ExpoSE conditional IIDs use Jalangi flag bits: 0x2=true and 0x4=false. Jalangi locations are converted from one-based line/column coordinates.",
  });
}

function buildReport(tool, manifest, sourceArms, match, extra = {}) {
  const provisional = [];
  for (const method of manifest.methods) {
    for (const edge of method.branches ?? []) {
      if (!edge.conditionOrigin && !edge.successorOrigin) {
        provisional.push({ method, edge, matches: [], forcedStatus: "synthetic" });
        continue;
      }
      const matchingFiles = bestFileMatches(method.fileName, sourceArms.map((arm) => arm.fileName));
      if (matchingFiles.ambiguous) {
        provisional.push({ method, edge, matches: [], forcedStatus: "ambiguous-file" });
        continue;
      }
      const candidates = sourceArms.filter((arm) => matchingFiles.files.has(arm.fileName));
      provisional.push({ method, edge, matches: match({ method, edge, candidates }) });
    }
  }

  const reverse = new Map();
  provisional.forEach((item) => item.matches.forEach((source) => {
    const edges = reverse.get(source.toolBranchId) ?? [];
    edges.push(item.edge.branchId);
    reverse.set(source.toolBranchId, edges);
  }));

  const mappings = provisional.map(({ method, edge, matches, forcedStatus }) => {
    let status = forcedStatus;
    if (!status) {
      if (matches.length === 0) status = "unmapped";
      else if (matches.length > 1) status = "ambiguous";
      else if ((reverse.get(matches[0].toolBranchId) ?? []).length > 1) status = "one-to-many";
      else status = "one-to-one";
    }
    const claimedCovered = matches.length === 1 ? matches[0].count > 0 : null;
    return {
      methodId: method.methodId,
      branchId: edge.branchId,
      ifStmtIndex: edge.ifStmtIndex,
      successorOrdinal: edge.successorOrdinal,
      status,
      claimedCovered,
      creditedCovered: status === "one-to-one" && claimedCovered === true,
      sourceMatches: matches.map((source) => ({
        toolBranchId: source.toolBranchId,
        count: source.count,
        fileName: source.fileName,
        origin: source.origin,
      })),
    };
  });
  const statuses = Object.fromEntries([...new Set(mappings.map((item) => item.status))].sort().map((status) => [
    status,
    mappings.filter((item) => item.status === status).length,
  ]));
  return {
    schemaVersion: 1,
    tool,
    policy: "Only one-to-one mappings with a positive source count are credited; ETC concrete replay remains ground truth.",
    ...extra,
    summary: {
      sourceArms: sourceArms.length,
      etsIrEdges: mappings.length,
      creditedCovered: mappings.filter((item) => item.creditedCovered).length,
      statuses,
    },
    mappings,
  };
}

function validateManifest(manifest) {
  if (manifest?.schemaVersion !== 1 || !Array.isArray(manifest.methods)) {
    throw new Error("expected target manifest schemaVersion 1");
  }
}

function bestFileMatches(manifestFile, coverageFiles) {
  const unique = [...new Set(coverageFiles)];
  const scored = unique.map((file) => ({ file, score: fileMatchScore(manifestFile, file) })).filter((item) => item.score > 0);
  if (scored.length === 0) return { files: new Set(), ambiguous: false };
  const best = Math.max(...scored.map((item) => item.score));
  const files = scored.filter((item) => item.score === best).map((item) => item.file);
  return { files: new Set(files), ambiguous: files.length > 1 };
}

function fileMatchScore(leftRaw, rightRaw) {
  const left = normalizePath(leftRaw);
  const right = normalizePath(stripFileUrl(rightRaw));
  if (left === right) return 3;
  if (left.endsWith(`/${right}`) || right.endsWith(`/${left}`)) return 2;
  if (basename(left) === basename(right)) return 1;
  return 0;
}

function istanbulLocation(location) {
  if (!location?.start || !location?.end) return null;
  return {
    startLine: Number(location.start.line) - 1,
    startColumn: Number(location.start.column),
    endLine: Number(location.end.line) - 1,
    endColumn: Number(location.end.column),
  };
}

function originsOverlap(left, right) {
  if (!left || !right) return false;
  if (hasOffsets(left) && hasOffsets(right)) {
    return left.startOffset < right.endOffset && right.startOffset < left.endOffset;
  }
  if (!hasLines(left) || !hasLines(right)) return false;
  return comparePosition(left.startLine, left.startColumn, right.endLine, right.endColumn) < 0 &&
    comparePosition(right.startLine, right.startColumn, left.endLine, left.endColumn) < 0;
}

function originContains(container, inner) {
  if (!container || !inner) return false;
  if (hasOffsets(container) && hasOffsets(inner)) {
    return container.startOffset <= inner.startOffset && container.endOffset >= inner.endOffset;
  }
  if (!hasLines(container) || !hasLines(inner)) return false;
  return comparePosition(container.startLine, container.startColumn, inner.startLine, inner.startColumn) <= 0 &&
    comparePosition(container.endLine, container.endColumn, inner.endLine, inner.endColumn) >= 0;
}

function uniqueMostSpecific(candidates) {
  if (candidates.length <= 1) return candidates;
  const lengths = candidates.map((candidate) => originLength(candidate.origin));
  const minimum = Math.min(...lengths);
  return candidates.filter((_, index) => lengths[index] === minimum);
}

function originLength(origin) {
  if (!origin) return Number.POSITIVE_INFINITY;
  if (hasOffsets(origin)) return Math.max(0, origin.endOffset - origin.startOffset);
  if (hasLines(origin)) return Math.max(0, scalar(origin.endLine, origin.endColumn) - scalar(origin.startLine, origin.startColumn));
  return Number.POSITIVE_INFINITY;
}

function hasOffsets(origin) {
  return Number.isFinite(origin?.startOffset) && Number.isFinite(origin?.endOffset);
}

function hasLines(origin) {
  return Number.isFinite(origin?.startLine) && Number.isFinite(origin?.startColumn) &&
    Number.isFinite(origin?.endLine) && Number.isFinite(origin?.endColumn);
}

function scalar(line, column) {
  return line * 1_000_000 + column;
}

function comparePosition(leftLine, leftColumn, rightLine, rightColumn) {
  return scalar(leftLine, leftColumn) - scalar(rightLine, rightColumn);
}

function normalizePath(path) {
  return String(path ?? "").replaceAll("\\", "/").replace(/\/$/, "");
}

function stripFileUrl(url) {
  if (!String(url).startsWith("file://")) return String(url);
  try {
    return decodeURIComponent(new URL(url).pathname);
  } catch {
    return String(url).slice("file://".length);
  }
}
