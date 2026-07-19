import { createHash } from "node:crypto";
import { RESULT_STATUSES, SEARCH_ALGORITHM } from "./constants.mjs";
import { assertEncodedValues, encodeConcreteValues } from "./value-codec.mjs";

const STATUSES = new Set(RESULT_STATUSES);

export function extractMethodResult({ methodId, result, runConfig, methodOrdinal }) {
  if (result === null || typeof result !== "object" || Array.isArray(result)) {
    throw new Error(`runner result for '${methodId}' must be an object`);
  }
  if (!STATUSES.has(result.status)) throw new Error(`runner result for '${methodId}' has unknown status '${result.status}'`);
  const rawCases = Array.isArray(result.cases) ? result.cases : [];
  const cases = [];
  const rejections = [];
  rawCases.forEach((rawCase, caseOrdinal) => {
    try {
      const values = normalizeValues(rawCase);
      const generatedAtMs = integerOr(rawCase.generatedAtMs, 0);
      const seed = stringOr(rawCase.seed, String(runConfig.seed));
      const path = stringOr(rawCase.path, `syntest:${methodOrdinal}:${caseOrdinal}`);
      const rawId = stringOr(rawCase.id, `case-${caseOrdinal}`);
      const identity = createHash("sha256")
        .update(methodId)
        .update("\0")
        .update(rawId)
        .update("\0")
        .update(JSON.stringify(values))
        .digest("hex")
        .slice(0, 24);
      const entry = {
        id: `syntest-${identity}`,
        methodId,
        generatedAtMs,
        seed,
        path,
        arguments: values.arguments,
        metadata: {
          engine: SEARCH_ALGORITHM,
          rawCaseId: rawId,
          source: "syntest-concrete-test-extraction",
        },
      };
      if (Object.hasOwn(values, "receiver")) entry.receiver = values.receiver;
      cases.push(entry);
    } catch (error) {
      rejections.push({
        methodId,
        rawCaseId: stringOr(rawCase?.id, `case-${caseOrdinal}`),
        reason: error instanceof Error ? error.message : String(error),
      });
    }
  });

  const claims = [];
  const objectives = Array.isArray(result.objectives) ? result.objectives : [];
  objectives.forEach((objective, index) => {
    if (objective === null || typeof objective !== "object" || Array.isArray(objective)) {
      throw new Error(`runner objective ${index} for '${methodId}' must be an object`);
    }
    const nativeTargetId = stringOr(objective.nativeTargetId, objective.id);
    if (nativeTargetId === null) throw new Error(`runner objective ${index} for '${methodId}' has no nativeTargetId`);
    if (typeof objective.claimedCovered !== "boolean" && typeof objective.covered !== "boolean") {
      throw new Error(`runner objective '${nativeTargetId}' for '${methodId}' has no boolean covered flag`);
    }
    const claim = {
      methodId,
      nativeTargetId,
      claimedCovered: objective.claimedCovered ?? objective.covered,
    };
    const discoveredAtMs = optionalInteger(objective.discoveredAtMs);
    if (discoveredAtMs !== undefined) claim.discoveredAtMs = discoveredAtMs;
    claims.push(claim);
  });

  return {
    status: result.status,
    started: result.started !== false,
    rawCaseCount: rawCases.length,
    cases,
    rejections,
    claims,
    stderr: typeof result.stderr === "string" ? result.stderr : "",
    diagnostics: result.diagnostics ?? {},
  };
}

function normalizeValues(rawCase) {
  if (rawCase === null || typeof rawCase !== "object" || Array.isArray(rawCase)) {
    throw new Error("raw case must be an object");
  }
  if (Object.hasOwn(rawCase, "encodedArguments")) {
    const values = { arguments: rawCase.encodedArguments };
    if (Object.hasOwn(rawCase, "encodedReceiver")) values.receiver = rawCase.encodedReceiver;
    assertEncodedValues(values);
    return structuredClone(values);
  }
  if (!Array.isArray(rawCase.arguments)) throw new Error("raw case requires arguments or encodedArguments");
  return encodeConcreteValues({
    receiverPresent: Object.hasOwn(rawCase, "receiver"),
    receiver: rawCase.receiver,
    arguments: rawCase.arguments,
  });
}

function integerOr(value, fallback) {
  if (value === undefined) return fallback;
  if (!Number.isInteger(value) || value < 0) throw new Error("generatedAtMs must be a non-negative integer");
  return value;
}

function optionalInteger(value) {
  if (value === undefined) return undefined;
  if (!Number.isInteger(value) || value < 0) throw new Error("discoveredAtMs must be a non-negative integer");
  return value;
}

function stringOr(value, fallback) {
  if (typeof value === "string" && value.length > 0) return value;
  return fallback ?? null;
}
