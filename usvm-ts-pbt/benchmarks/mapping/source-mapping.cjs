"use strict";

const { posix } = require("node:path");

const MAPPING_STATUSES = Object.freeze(["exact", "oneToMany", "ambiguous", "unmapped", "synthetic"]);

const MappingStatus = Object.freeze(Object.fromEntries(MAPPING_STATUSES.map((status) => [status, status])));

/**
 * Stable identity of a callable in TypeScript source, independent of the
 * anonymous method names chosen by an EtsIR frontend.
 */
class SourceCallableId {
  static create({ modulePath, callableKind, qualifiedName, arity }) {
    if (!Number.isInteger(arity) || arity < 0) throw new Error(`invalid source callable arity '${arity}'`);
    const module = normalizeModulePath(modulePath);
    const kind = nonBlank(callableKind, "callable kind");
    const name = nonBlank(qualifiedName, "qualified callable name");
    return `ts:${escapeId(module)}::${escapeId(kind)}:${escapeId(name)}/${arity}`;
  }
}

/** A decoded, immutable index over a canonical v3 source map. */
class SourceMapIndex {
  constructor({ sourceMap, sourceText, generatedText, sourceFile, generatedFile }) {
    if (sourceMap?.version !== 3 || typeof sourceMap.mappings !== "string") {
      throw new Error("expected a source-map v3 document");
    }
    this.sourceFile = normalizePath(sourceFile);
    this.generatedFile = normalizePath(generatedFile || sourceMap.file || this.sourceFile.replace(/\.tsx?$/, ".js"));
    this.sourceText = String(sourceText);
    this.generatedText = String(generatedText);
    this.sourceLineStarts = lineStarts(this.sourceText);
    this.generatedLineStarts = lineStarts(this.generatedText);

    const sourceMatches = (sourceMap.sources ?? [])
      .map((source, index) => ({ index, score: fileMatchScore(this.sourceFile, source) }))
      .filter(({ score }) => score > 0);
    const bestScore = sourceMatches.length === 0 ? 0 : Math.max(...sourceMatches.map(({ score }) => score));
    this.sourceIndices = new Set(sourceMatches.filter(({ score }) => score === bestScore).map(({ index }) => index));
    this.sourceAmbiguous = this.sourceIndices.size > 1;
    this.entries = decodeMappings(sourceMap.mappings)
      .filter((entry) => this.sourceIndices.has(entry.sourceIndex))
      .map((entry) => ({
        ...entry,
        originalOffset: offsetAt(this.sourceLineStarts, entry.originalLine, entry.originalColumn, this.sourceText.length),
        generatedOffset: offsetAt(this.generatedLineStarts, entry.generatedLine, entry.generatedColumn, this.generatedText.length),
      }))
      .filter((entry) => entry.originalOffset != null && entry.generatedOffset != null)
      .sort((left, right) => left.generatedOffset - right.generatedOffset || left.originalOffset - right.originalOffset);
  }

  /**
   * Return generated ranges backed by source-map segments inside [range]. No
   * nearest-neighbour promotion is used: a span without its own segment stays
   * explicitly unmapped.
   */
  resolve(range) {
    if (this.sourceAmbiguous) return { status: MappingStatus.ambiguous, ranges: [], reason: "ambiguous-source-map-source" };
    if (this.sourceIndices.size === 0) return { status: MappingStatus.unmapped, ranges: [], reason: "source-map-source-unresolved" };
    if (!validRange(range)) return { status: MappingStatus.unmapped, ranges: [], reason: "invalid-ts-source-range" };

    const selected = this.entries
      .map((entry, index) => ({ entry, index }))
      .filter(({ entry }) => entry.originalOffset >= range.startOffset && entry.originalOffset < range.endOffset);
    if (selected.length === 0 && range.startOffset === range.endOffset) {
      selected.push(...this.entries
        .map((entry, index) => ({ entry, index }))
        .filter(({ entry }) => entry.originalOffset === range.startOffset));
    }
    if (selected.length === 0) return { status: MappingStatus.unmapped, ranges: [], reason: "no-source-map-segment-in-range" };

    const runs = [];
    for (const item of selected) {
      const previous = runs[runs.length - 1];
      if (!previous || item.index !== previous.lastIndex + 1) {
        runs.push({ firstIndex: item.index, lastIndex: item.index });
      } else {
        previous.lastIndex = item.index;
      }
    }
    const ranges = runs.map(({ firstIndex, lastIndex }) => {
      const start = this.entries[firstIndex].generatedOffset;
      const next = this.entries[lastIndex + 1]?.generatedOffset;
      const end = Math.max(start, next == null ? endOfGeneratedLine(this.generatedText, start) : next);
      return rangeFromOffsets(this.generatedFile, this.generatedText, start, end);
    });
    return {
      status: ranges.length === 1 ? MappingStatus.exact : MappingStatus.oneToMany,
      ranges,
      reason: ranges.length === 1 ? "source-map-exact" : "source-map-one-to-many",
    };
  }
}

/** Collects terminal status counts and every non-exact edge diagnostic. */
class MappingReport {
  constructor({ manifest, sourceCallableIdAlgorithm = "ts:<module>::<kind>:<qualified-name>/<arity>" }) {
    this.manifest = manifest;
    this.sourceCallableIdAlgorithm = sourceCallableIdAlgorithm;
    this.expectedEdges = (manifest.methods ?? []).reduce((sum, method) => sum + (method.branches?.length ?? 0), 0);
    this.records = [];
    this.diagnostics = [];
  }

  add(record, diagnostic = {}) {
    if (!MAPPING_STATUSES.includes(record.mappingStatus)) throw new Error(`unknown mapping status '${record.mappingStatus}'`);
    this.records.push(record);
    if (record.mappingStatus !== MappingStatus.exact) {
      this.diagnostics.push({
        methodId: record.methodId,
        branchId: record.branchId,
        mappingStatus: record.mappingStatus,
        reasons: uniqueSorted(diagnostic.reasons ?? []),
        candidates: diagnostic.candidates ?? [],
      });
    }
  }

  finish() {
    const identities = new Set(this.records.map((record) => `${record.methodId}\0${record.branchId}`));
    const counts = Object.fromEntries(MAPPING_STATUSES.map((status) => [
      status,
      this.records.filter((record) => record.mappingStatus === status).length,
    ]));
    return {
      schemaVersion: 2,
      inputManifestSchemaVersion: this.manifest.schemaVersion,
      sourceCallableIdAlgorithm: this.sourceCallableIdAlgorithm,
      expectedEdges: this.expectedEdges,
      writtenEdges: this.records.length,
      uniqueEdges: identities.size,
      silentDrops: this.expectedEdges - this.records.length,
      duplicateEdges: this.records.length - identities.size,
      mappingStatus: counts,
      ambiguities: this.diagnostics.filter(({ mappingStatus }) => mappingStatus === MappingStatus.ambiguous),
      nonExact: this.diagnostics,
    };
  }
}

/**
 * Build one schema-v2 JSONL record for every manifest edge. The caller supplies
 * only evidence obtained from the source AST and source map; absent evidence is
 * terminal `unmapped`/`synthetic`, never guessed into `exact`.
 */
function mapManifestEdges(manifest, methodContexts) {
  if (![1, 2].includes(manifest?.schemaVersion) || !Array.isArray(manifest.methods)) {
    throw new Error("expected a target manifest schemaVersion 1 or 2");
  }
  const provisional = [];
  for (const method of manifest.methods) {
    const context = methodContexts.get(method.methodId) ?? unresolvedContext(method);
    for (const edge of method.branches ?? []) provisional.push(mapEdge(method, edge, context));
  }

  // A single source decision lowered into several distinct EtsIR branch
  // statements is one-to-many. The two successor arms of one statement remain
  // exact because successorOrdinal makes them distinct source branch arms.
  const reverse = new Map();
  for (const item of provisional) {
    if (item.record.mappingStatus !== MappingStatus.exact) continue;
    const range = item.record.tsSourceRange;
    const key = `${item.record.sourceOrigin.sourceCallableId}\0${range.fileName}:${range.startOffset}:${range.endOffset}`;
    const statements = reverse.get(key) ?? new Set();
    statements.add(`${item.record.methodId}\0${item.record.stmtIndex}`);
    reverse.set(key, statements);
    item.reverseKey = key;
  }
  for (const item of provisional) {
    if (item.reverseKey && reverse.get(item.reverseKey).size > 1) {
      item.record.mappingStatus = MappingStatus.oneToMany;
      item.diagnostic.reasons.push("source-decision-lowered-to-multiple-etsir-statements");
    }
  }

  const report = new MappingReport({ manifest });
  for (const item of provisional) report.add(item.record, item.diagnostic);
  const summary = report.finish();
  if (summary.silentDrops !== 0 || summary.duplicateEdges !== 0) {
    throw new Error(`mapping completeness failure: silentDrops=${summary.silentDrops}, duplicateEdges=${summary.duplicateEdges}`);
  }
  return { records: report.records, report: summary };
}

function mapEdge(method, edge, context) {
  assertEdge(method, edge);
  const reasons = [...(context.reasons ?? [])];
  const binding = context.binding ?? fallbackBinding(method, context.modulePath);
  const rawOrigin = edge.conditionOrigin ?? edge.successorOrigin ?? binding.declarationRange;
  const rawOriginIsValid = validArtifactRange(rawOrigin);
  const tsRange = rawOrigin
    ? copyRange(rawOrigin, context.modulePath ?? rawOrigin.fileName ?? method.fileName)
    : zeroRange(context.modulePath ?? method.fileName);

  let status;
  let emittedRanges = [];
  if (!edge.conditionOrigin && !edge.successorOrigin) {
    status = MappingStatus.synthetic;
    reasons.push("etsir-edge-has-no-source-origin");
  } else if (context.sourceResolution === "ambiguous" || context.bindingResolution === "ambiguous") {
    status = MappingStatus.ambiguous;
    reasons.push(context.sourceResolution === "ambiguous" ? "ambiguous-source-file" : "ambiguous-callable-binding");
  } else if (context.sourceResolution !== "exact" || context.bindingResolution !== "exact") {
    status = MappingStatus.unmapped;
    reasons.push(context.sourceResolution !== "exact" ? "source-file-unresolved" : "callable-binding-unresolved");
  } else if (!rawOriginIsValid || !validRange(tsRange)) {
    status = MappingStatus.unmapped;
    reasons.push("invalid-ts-source-range");
  } else if (!context.sourceMapIndex) {
    status = MappingStatus.unmapped;
    reasons.push("emitted-js-source-map-unavailable");
  } else {
    const resolution = context.sourceMapIndex.resolve(tsRange);
    status = resolution.status;
    emittedRanges = resolution.ranges;
    reasons.push(resolution.reason);
  }

  const sourceCallableId = binding.sourceCallableId ?? SourceCallableId.create({
    modulePath: context.modulePath ?? method.fileName,
    callableKind: binding.callableKind,
    qualifiedName: binding.qualifiedName,
    arity: Number.isInteger(method.arity) ? method.arity : (method.parameters?.length ?? method.parameterTypes?.length ?? 0),
  });
  const sourceOrigin = {
    modulePath: normalizeModulePath(context.modulePath ?? method.fileName),
    callableName: binding.runtimeName ?? binding.qualifiedName,
    callableKind: binding.callableKind,
    sourceCallableId,
    moduleOrigin: context.moduleOrigin ?? null,
    importOrigins: context.importOrigins ?? [],
    fileInitOrigin: context.fileInitOrigin ?? null,
    callableBinding: {
      localName: binding.localName ?? null,
      qualifiedName: binding.qualifiedName,
      exportName: binding.exportName ?? null,
      runtimeName: binding.runtimeName ?? binding.qualifiedName,
      bindingKind: binding.bindingKind ?? "unresolved",
      declarationRange: binding.declarationRange ?? null,
      bindingRange: binding.bindingRange ?? null,
    },
  };
  const record = {
    schemaVersion: 2,
    methodId: method.methodId,
    branchId: edge.branchId,
    stmtIndex: edge.ifStmtIndex,
    successorStmtIndex: edge.successorStmtIndex,
    successorOrdinal: edge.successorOrdinal,
    tsSourceRange: tsRange,
    emittedJsRange: emittedRanges.length === 0 ? undefined : envelope(emittedRanges),
    sourceOrigin,
    mappingStatus: status,
    tsNodeKind: rawOrigin?.nodeKind ?? null,
    emittedJsRanges: emittedRanges,
    successorTsSourceRange: edge.successorOrigin ? copyRange(edge.successorOrigin, context.modulePath ?? method.fileName) : null,
  };
  if (record.emittedJsRange === undefined) delete record.emittedJsRange;
  return {
    record,
    diagnostic: { reasons, candidates: context.candidates ?? [] },
  };
}

function unresolvedContext(method) {
  return {
    modulePath: normalizeModulePath(method.fileName),
    sourceResolution: "unmapped",
    bindingResolution: "unmapped",
    reasons: ["method-context-absent"],
  };
}

function fallbackBinding(method, modulePath = method.fileName) {
  const callableKind = ["free", "static", "instance", "constructor"].includes(method.entryKind)
    ? method.entryKind
    : "synthetic";
  const owner = method.className && method.className !== "%dflt" ? `${method.className}.` : "";
  const qualifiedName = `${owner}${method.methodName || "<unknown>"}`;
  return {
    callableKind,
    qualifiedName,
    runtimeName: qualifiedName,
    sourceCallableId: SourceCallableId.create({
      modulePath,
      callableKind,
      qualifiedName,
      arity: Number.isInteger(method.arity) ? method.arity : (method.parameters?.length ?? method.parameterTypes?.length ?? 0),
    }),
  };
}

function assertEdge(method, edge) {
  for (const [name, value] of [
    ["ifStmtIndex", edge.ifStmtIndex],
    ["successorStmtIndex", edge.successorStmtIndex],
    ["successorOrdinal", edge.successorOrdinal],
  ]) {
    if (!Number.isInteger(value) || value < 0) throw new Error(`${method.methodId}/${edge.branchId}: invalid ${name}`);
  }
  if (typeof edge.branchId !== "string" || edge.branchId.length === 0) {
    throw new Error(`${method.methodId}: edge has no branchId`);
  }
}

function decodeMappings(encoded) {
  const result = [];
  let sourceIndex = 0, originalLine = 0, originalColumn = 0, nameIndex = 0;
  const lines = encoded.split(";");
  for (let generatedLine = 0; generatedLine < lines.length; generatedLine += 1) {
    let generatedColumn = 0;
    for (const rawSegment of lines[generatedLine].split(",")) {
      if (!rawSegment) continue;
      const values = decodeVlq(rawSegment);
      generatedColumn += values[0];
      if (values.length === 1) continue;
      if (values.length !== 4 && values.length !== 5) throw new Error(`invalid source-map segment '${rawSegment}'`);
      sourceIndex += values[1];
      originalLine += values[2];
      originalColumn += values[3];
      if (values.length === 5) nameIndex += values[4];
      result.push({ generatedLine, generatedColumn, sourceIndex, originalLine, originalColumn, nameIndex });
    }
  }
  return result;
}

function decodeVlq(segment) {
  const result = [];
  let value = 0, shift = 0;
  for (const char of segment) {
    const digit = BASE64.indexOf(char);
    if (digit < 0) throw new Error(`invalid base64 VLQ character '${char}'`);
    value += (digit & 31) << shift;
    if ((digit & 32) !== 0) {
      shift += 5;
      continue;
    }
    const negative = (value & 1) !== 0;
    result.push(negative ? -(value >> 1) : value >> 1);
    value = 0;
    shift = 0;
  }
  if (shift !== 0) throw new Error(`unterminated base64 VLQ segment '${segment}'`);
  return result;
}

const BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

function lineStarts(text) {
  const starts = [0];
  for (let index = 0; index < text.length; index += 1) if (text[index] === "\n") starts.push(index + 1);
  return starts;
}

function offsetAt(starts, line, column, length) {
  if (!Number.isInteger(line) || !Number.isInteger(column) || line < 0 || column < 0 || line >= starts.length) return null;
  const offset = starts[line] + column;
  const lineEnd = line + 1 < starts.length ? starts[line + 1] - 1 : length;
  return offset <= lineEnd ? offset : null;
}

function rangeFromOffsets(fileName, text, startOffset, endOffset) {
  if (!Number.isInteger(startOffset) || !Number.isInteger(endOffset)
      || startOffset < 0 || endOffset < startOffset || endOffset > text.length) {
    throw new Error(`invalid offsets ${startOffset}..${endOffset} for '${fileName}' (${text.length} UTF-16 code units)`);
  }
  const starts = lineStarts(text);
  const start = positionAt(starts, startOffset);
  const end = positionAt(starts, endOffset);
  return {
    fileName: normalizePath(fileName), startOffset, endOffset,
    startLine: start.line, startColumn: start.column, endLine: end.line, endColumn: end.column,
  };
}

function positionAt(starts, offset) {
  let low = 0, high = starts.length;
  while (low + 1 < high) {
    const middle = (low + high) >> 1;
    if (starts[middle] <= offset) low = middle;
    else high = middle;
  }
  return { line: low, column: offset - starts[low] };
}

function endOfGeneratedLine(text, offset) {
  const newline = text.indexOf("\n", offset);
  return newline < 0 ? text.length : newline;
}

function validRange(range) {
  return Number.isInteger(range?.startOffset) && Number.isInteger(range?.endOffset)
    && range.startOffset >= 0 && range.endOffset >= range.startOffset;
}

function copyRange(origin, fileName) {
  if (!validArtifactRange(origin)) return zeroRange(fileName);
  return {
    fileName: normalizePath(fileName),
    startOffset: Number(origin.startOffset), endOffset: Number(origin.endOffset),
    startLine: Number(origin.startLine), startColumn: Number(origin.startColumn),
    endLine: Number(origin.endLine), endColumn: Number(origin.endColumn),
  };
}

function validArtifactRange(range) {
  return validRange(range)
    && [range.startLine, range.startColumn, range.endLine, range.endColumn].every((value) => Number.isInteger(value) && value >= 0)
    && (range.endLine > range.startLine || range.endLine === range.startLine && range.endColumn >= range.startColumn);
}

function zeroRange(fileName) {
  return {
    fileName: normalizePath(fileName || "<synthetic>"), startOffset: 0, endOffset: 0,
    startLine: 0, startColumn: 0, endLine: 0, endColumn: 0,
  };
}

function envelope(ranges) {
  if (ranges.length === 1) return ranges[0];
  const sorted = [...ranges].sort((left, right) => left.startOffset - right.startOffset);
  return {
    ...sorted[0],
    endOffset: sorted.at(-1).endOffset,
    endLine: sorted.at(-1).endLine,
    endColumn: sorted.at(-1).endColumn,
  };
}

function normalizeModulePath(path) {
  const normalized = normalizePath(nonBlank(path, "module path")).replace(/^\.\//, "");
  const collapsed = posix.normalize(normalized);
  return collapsed === "." ? normalized : collapsed;
}

function normalizePath(path) { return String(path).replaceAll("\\", "/"); }

function fileMatchScore(leftRaw, rightRaw) {
  const left = normalizePath(leftRaw).replace(/^\.\//, "");
  const right = normalizePath(rightRaw).replace(/^\.\//, "");
  if (left === right) return 3;
  if (left.endsWith(`/${right}`) || right.endsWith(`/${left}`)) return 2;
  if (left.split("/").at(-1) === right.split("/").at(-1)) return 1;
  return 0;
}

function escapeId(value) { return encodeURIComponent(value).replaceAll("%2F", "/"); }

function nonBlank(value, label) {
  if (typeof value !== "string" || value.trim().length === 0) throw new Error(`${label} must be non-blank`);
  return value.trim();
}

function uniqueSorted(values) { return [...new Set(values.filter(Boolean))].sort(); }

module.exports = {
  MAPPING_STATUSES,
  MappingStatus,
  MappingReport,
  SourceCallableId,
  SourceMapIndex,
  mapManifestEdges,
  normalizeModulePath,
  rangeFromOffsets,
};
