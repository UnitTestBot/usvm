import { CLASSIFICATION_REASONS } from "./constants.mjs";

const CLOSED_REASONS = new Set(CLASSIFICATION_REASONS);
const PRIMITIVE_ATOM = /^(?:number|string|boolean|null|undefined|void|true|false|-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')$/;

export function classifyMethods({ manifest, sourceTargets, methodIds }) {
  const methodsById = new Map(manifest.methods.map((method) => [method.methodId, method]));
  const recordsByMethod = new Map();
  for (const record of sourceTargets) {
    const bucket = recordsByMethod.get(record.methodId) ?? [];
    bucket.push(record);
    recordsByMethod.set(record.methodId, bucket);
  }

  const classifications = methodIds.map((methodId) => {
    const method = methodsById.get(methodId);
    if (!method) return finish({ methodId, method: null, reasons: ["method-not-in-manifest"], mappings: [] });

    const reasons = [];
    if (method.branches.length === 0) reasons.push("no-branches");
    if (method.entryKind !== "free" && method.entryKind !== "static") reasons.push("unsupported-entry-kind");
    if (method.parameters.some((parameter) => parameter.rest)) reasons.push("unsupported-rest-parameter");
    if (method.parameterTypes.some((type) => !isSupportedPrimitiveType(type))) reasons.push("unsupported-parameter-type");

    const records = recordsByMethod.get(methodId) ?? [];
    const byBranch = new Map(records.map((record) => [record.branchId, record]));
    const mappings = method.branches.map((branch) => byBranch.get(branch.branchId)).filter(Boolean);
    if (mappings.length !== method.branches.length) reasons.push("source-mapping-missing");
    if (mappings.some((record) => record.mappingStatus !== "exact")) reasons.push("source-mapping-not-exact");

    const origins = unique(mappings.map((record) => JSON.stringify(record.sourceOrigin)));
    if (origins.length > 1) reasons.push("source-origin-ambiguous");
    const origin = mappings[0]?.sourceOrigin;
    if (origin && !originMatchesEntry(origin.callableKind, method.entryKind)) reasons.push("callable-origin-mismatch");

    return finish({ methodId, method, reasons, mappings });
  });

  return {
    reasonVocabulary: [...CLASSIFICATION_REASONS],
    classifications,
    selectedMethods: methodIds.length,
    selectedEdges: classifications.reduce((sum, item) => sum + item.branchCount, 0),
    eligibleMethods: classifications.filter((item) => item.status === "eligible").length,
    eligibleEdges: classifications.filter((item) => item.status === "eligible").reduce((sum, item) => sum + item.branchCount, 0),
  };
}

export function isSupportedPrimitiveType(rawType) {
  if (typeof rawType !== "string" || rawType.trim().length === 0) return false;
  return splitTopLevelUnion(rawType).every((atom) => PRIMITIVE_ATOM.test(atom.replaceAll(" ", "")));
}

function finish({ methodId, method, reasons, mappings }) {
  const normalizedReasons = unique(reasons).sort();
  for (const reason of normalizedReasons) {
    if (!CLOSED_REASONS.has(reason)) throw new Error(`classifier emitted non-closed reason '${reason}'`);
  }
  const sourceOrigin = unique(mappings.map((record) => JSON.stringify(record.sourceOrigin))).length === 1
    ? mappings[0]?.sourceOrigin
    : undefined;
  return {
    methodId,
    status: normalizedReasons.length === 0 ? "eligible" : "ineligible",
    reasons: normalizedReasons,
    branchCount: method?.branches?.length ?? 0,
    entryKind: method?.entryKind ?? null,
    parameterTypes: method?.parameterTypes ?? [],
    sourceOrigin: sourceOrigin ?? null,
  };
}

function originMatchesEntry(callableKind, entryKind) {
  if (entryKind === "free") return callableKind === "free" || callableKind === "arrow";
  if (entryKind === "static") return callableKind === "static";
  return false;
}

function splitTopLevelUnion(raw) {
  const parts = [];
  let quote = null;
  let escaped = false;
  let start = 0;
  for (let index = 0; index < raw.length; index += 1) {
    const char = raw[index];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (char === "\\") {
      escaped = true;
      continue;
    }
    if (quote !== null) {
      if (char === quote) quote = null;
      continue;
    }
    if (char === "'" || char === '"') {
      quote = char;
      continue;
    }
    if (char === "|") {
      parts.push(raw.slice(start, index).trim());
      start = index + 1;
    }
  }
  parts.push(raw.slice(start).trim());
  return parts;
}

function unique(values) {
  return [...new Set(values)];
}
