"use strict";

const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");

const fixtureSource = require("./BuiltinSemanticsFixture.ts");

const OPERATION_NAMES = Object.freeze([
  "array.isArray",
  "object.toStringTag",
  "object.hasOwn",
  "property.in",
  "map.set",
  "map.get",
  "map.has",
  "map.size",
  "truthy",
]);
const OPERATION_SET = new Set(OPERATION_NAMES);
const CAPABILITY_STATUSES = new Set([
  "supported",
  "supported_with_flag",
  "external_only",
  "unsupported",
  "needs_dynamic_probe",
]);
// Must stay byte-for-byte aligned with WP-CAP CapabilityLabel.values.
const CAPABILITY_LABELS = new Set([
  "primitive_arithmetic",
  "module_init",
  "callable",
  "iterator",
  "array_object",
  "map_set",
  "builtin_call",
  "spread_yield",
  "unresolved_pointer_call",
]);
const SEMANTIC_TAGS = new Set([
  "static_builtin_call",
  "primitive_string",
  "nullish",
  "property_membership",
  "missing_vs_undefined",
  "prototype_chain",
  "map_mutation",
  "truthiness",
  "same_value_zero",
]);
const NUMBER_LITERALS = new Set(["NaN", "Infinity", "-Infinity", "-0"]);

function loadSpec(specPath = path.join(__dirname, "builtin-semantics-v1.json")) {
  return JSON.parse(fs.readFileSync(specPath, "utf8"));
}

function decodeNumber(value) {
  if (value === "NaN") return Number.NaN;
  if (value === "Infinity") return Number.POSITIVE_INFINITY;
  if (value === "-Infinity") return Number.NEGATIVE_INFINITY;
  if (value === "-0") return -0;
  const result = Number(value);
  if (!Number.isFinite(result)) throw new Error(`invalid finite number literal '${value}'`);
  return result;
}

function decodeValue(encoded) {
  switch (encoded?.kind) {
    case "undefined": return undefined;
    case "null": return null;
    case "boolean": return Boolean(encoded.value);
    case "string": return String(encoded.value);
    case "number": return decodeNumber(encoded.value);
    case "array": return (encoded.elements ?? []).map(decodeValue);
    case "object": {
      const prototype = encoded.prototype == null ? Object.prototype : decodeValue(encoded.prototype);
      const result = Object.create(prototype);
      for (const [key, value] of Object.entries(encoded.own ?? {})) {
        Object.defineProperty(result, key, {
          configurable: true,
          enumerable: true,
          writable: true,
          value: decodeValue(value),
        });
      }
      return result;
    }
    case "map": {
      const result = new Map();
      for (const entry of encoded.entries ?? []) {
        result.set(decodeValue(entry.key), decodeValue(entry.value));
      }
      return result;
    }
    default: throw new Error(`unknown value kind '${encoded?.kind}'`);
  }
}

function encodeNumber(value) {
  if (Number.isNaN(value)) return { kind: "number", value: "NaN" };
  if (value === Number.POSITIVE_INFINITY) return { kind: "number", value: "Infinity" };
  if (value === Number.NEGATIVE_INFINITY) return { kind: "number", value: "-Infinity" };
  if (Object.is(value, -0)) return { kind: "number", value: "-0" };
  return { kind: "number", value: String(value) };
}

function encodeValue(value) {
  if (value === undefined) return { kind: "undefined" };
  if (value === null) return { kind: "null" };
  if (typeof value === "boolean") return { kind: "boolean", value };
  if (typeof value === "string") return { kind: "string", value };
  if (typeof value === "number") return encodeNumber(value);
  throw new Error(`result type '${typeof value}' is outside the frozen result subset`);
}

function formatValue(encoded) {
  switch (encoded.kind) {
    case "undefined": return "undefined";
    case "null": return "null";
    case "boolean": return `boolean:${encoded.value}`;
    case "string": return `string:${JSON.stringify(encoded.value)}`;
    case "number": return `number:${encoded.value}`;
    case "receiver": return `receiver:${encoded.name}`;
    default: throw new Error(`cannot format '${encoded.kind}' in a trace`);
  }
}

function resolveArgument(argument, environment) {
  if (Object.hasOwn(argument, "ref")) {
    if (!environment.has(argument.ref)) throw new Error(`unknown reference '${argument.ref}'`);
    return environment.get(argument.ref);
  }
  if (Object.hasOwn(argument, "literal")) return decodeValue(argument.literal);
  throw new Error("argument must contain ref or literal");
}

function operationResult(operation, receiver, args) {
  switch (operation) {
    case "array.isArray": return fixtureSource.arrayIsArray(receiver);
    case "object.toStringTag": return fixtureSource.objectToStringTag(receiver);
    case "object.hasOwn": return fixtureSource.objectHasOwn(receiver, args[0]);
    case "property.in": return fixtureSource.propertyIn(receiver, args[0]);
    case "map.set": return fixtureSource.mapSet(receiver, args[0], args[1]);
    case "map.get": return fixtureSource.mapGet(receiver, args[0]);
    case "map.has": return fixtureSource.mapHas(receiver, args[0]);
    case "map.size": return fixtureSource.mapSize(receiver);
    case "truthy": return fixtureSource.truthy(receiver);
    default: throw new Error(`unknown operation '${operation}'`);
  }
}

function canonicalOperationResult(operation, receiverName, result) {
  if (operation === "map.set") return { kind: "receiver", name: receiverName };
  return encodeValue(result);
}

function runCase(testCase) {
  const environment = new Map(
    Object.entries(testCase.environment ?? {}).map(([name, encoded]) => [name, decodeValue(encoded)]),
  );
  const trace = [];

  for (const step of testCase.steps) {
    const receiver = environment.get(step.receiver);
    if (!environment.has(step.receiver)) throw new Error(`${testCase.id}: unknown receiver '${step.receiver}'`);
    const args = (step.arguments ?? []).map((argument) => resolveArgument(argument, environment));
    const result = operationResult(step.operation, receiver, args);
    environment.set(step.saveAs, result);

    const canonicalResult = canonicalOperationResult(step.operation, step.receiver, result);
    const formattedArgs = (step.arguments ?? []).map((argument, index) => {
      if (Object.hasOwn(argument, "ref")) return `ref:${argument.ref}`;
      return formatValue(encodeValue(args[index]));
    });
    trace.push(
      `${step.operation}(${[step.receiver, ...formattedArgs].join(", ")}) -> ${formatValue(canonicalResult)}`,
    );
  }

  if (!environment.has(testCase.return)) throw new Error(`${testCase.id}: missing return '${testCase.return}'`);
  const result = environment.get(testCase.return);
  return {
    result: encodeValue(result),
    truthy: Boolean(result),
    trace,
  };
}

function validateEncodedValue(value, location, errors) {
  const kinds = new Set(["undefined", "null", "boolean", "string", "number", "array", "object", "map"]);
  if (value == null || typeof value !== "object" || !kinds.has(value.kind)) {
    errors.push(`${location}: unknown or missing value kind`);
    return;
  }
  if (value.kind === "number") {
    if (typeof value.value !== "string" || (!NUMBER_LITERALS.has(value.value) && !Number.isFinite(Number(value.value)))) {
      errors.push(`${location}: invalid number literal '${value.value}'`);
    }
  }
  if (value.kind === "array") {
    (value.elements ?? []).forEach((element, index) => validateEncodedValue(element, `${location}.elements[${index}]`, errors));
  }
  if (value.kind === "object") {
    if (value.prototype != null) validateEncodedValue(value.prototype, `${location}.prototype`, errors);
    for (const [key, child] of Object.entries(value.own ?? {})) validateEncodedValue(child, `${location}.own.${key}`, errors);
  }
  if (value.kind === "map") {
    (value.entries ?? []).forEach((entry, index) => {
      validateEncodedValue(entry.key, `${location}.entries[${index}].key`, errors);
      validateEncodedValue(entry.value, `${location}.entries[${index}].value`, errors);
    });
  }
}

function findRepoRoot(start = __dirname) {
  let current = path.resolve(start);
  while (true) {
    if (fs.existsSync(path.join(current, "settings.gradle.kts"))) return current;
    const parent = path.dirname(current);
    if (parent === current) return null;
    current = parent;
  }
}

function validateResidualEvidence(spec, errors, repoRoot) {
  if (!repoRoot) {
    errors.push("repository root not found; frozen residual evidence cannot be checked");
    return;
  }
  const observationsPath = path.join(repoRoot, spec.baselineEvidence.observationsPath);
  if (!fs.existsSync(observationsPath)) {
    errors.push(`missing observations '${observationsPath}'`);
    return;
  }
  const observationsSha256 = crypto.createHash("sha256")
    .update(fs.readFileSync(observationsPath))
    .digest("hex");
  if (observationsSha256 !== spec.baselineEvidence.observationsSha256) {
    errors.push(
      `frozen observations hash ${observationsSha256} != ${spec.baselineEvidence.observationsSha256}`,
    );
  }
  const observations = JSON.parse(fs.readFileSync(observationsPath, "utf8"));
  const targetsByProject = new Map();

  for (const blocker of spec.residualBlockers) {
    if (!targetsByProject.has(blocker.projectId)) {
      const targetPath = path.join(repoRoot, blocker.targetManifestPath);
      if (!fs.existsSync(targetPath)) {
        errors.push(`${blocker.id}: missing target manifest '${targetPath}'`);
        continue;
      }
      targetsByProject.set(blocker.projectId, JSON.parse(fs.readFileSync(targetPath, "utf8")));
    }
    const manifest = targetsByProject.get(blocker.projectId);
    const method = manifest?.methods?.find((candidate) => candidate.methodId === blocker.etsIr.methodId);
    const branch = method?.branches?.find((candidate) => candidate.branchId === blocker.etsIr.branchId);
    if (!branch) errors.push(`${blocker.id}: branch absent from frozen target manifest`);
    else if (branch.ifStmtIndex !== blocker.etsIr.ifStmtIndex) {
      errors.push(`${blocker.id}: stmt index ${branch.ifStmtIndex} != ${blocker.etsIr.ifStmtIndex}`);
    }

    const observedTargets = observations.reports
      .filter((report) => report.projectId === blocker.projectId && blocker.observationScenarios.includes(report.scenario))
      .flatMap((report) => report.methods ?? [])
      .filter((candidate) => candidate.methodId === blocker.etsIr.methodId)
      .flatMap((candidate) => candidate.symbolic?.targets ?? [])
      .filter((target) => target.branchId === blocker.etsIr.branchId);
    if (observedTargets.length !== blocker.observationScenarios.length) {
      errors.push(`${blocker.id}: expected ${blocker.observationScenarios.length} observations, got ${observedTargets.length}`);
    }
    if (observedTargets.some((target) => target.reached !== true || target.replayConfirmed !== false)) {
      errors.push(`${blocker.id}: frozen observation is not reached=true/replayConfirmed=false`);
    }
  }
}

function validateSpec(spec, { repoRoot = findRepoRoot() } = {}) {
  const errors = [];
  if (spec.schemaVersion !== 1) errors.push(`schemaVersion must be 1, got '${spec.schemaVersion}'`);
  if (spec.contractId !== "usvm-ts-pbt.builtins.exact.v1") errors.push("unexpected contractId");
  if (!Array.isArray(spec.requiredOperations) || [...OPERATION_SET].some((op) => !spec.requiredOperations.includes(op))) {
    errors.push("requiredOperations does not cover the executable operation set");
  }
  const caseIds = new Set();
  for (const testCase of spec.cases ?? []) {
    if (!testCase.id || caseIds.has(testCase.id)) errors.push(`duplicate or blank case id '${testCase.id}'`);
    caseIds.add(testCase.id);
    if (!testCase.sourceCallableId?.startsWith("ts:semantic/builtins/BuiltinSemanticsFixture.ts::free:")) {
      errors.push(`${testCase.id}: unstable or missing sourceCallableId`);
    }
    if (!CAPABILITY_STATUSES.has(testCase.capability?.expectedStatus)) {
      errors.push(`${testCase.id}: unknown capability status '${testCase.capability?.expectedStatus}'`);
    }
    if (testCase.capability?.expectedOutcome !== "exact" || !(testCase.capability?.labels?.length > 0)) {
      errors.push(`${testCase.id}: expected exact labeled capability outcome`);
    }
    for (const label of testCase.capability?.labels ?? []) {
      if (!CAPABILITY_LABELS.has(label)) errors.push(`${testCase.id}: unknown closed capability label '${label}'`);
    }
    for (const tag of testCase.capability?.semanticTags ?? []) {
      if (!SEMANTIC_TAGS.has(tag)) errors.push(`${testCase.id}: unknown semantic tag '${tag}'`);
      if (CAPABILITY_LABELS.has(tag)) errors.push(`${testCase.id}: closed capability label '${tag}' leaked into semanticTags`);
    }
    for (const [name, encoded] of Object.entries(testCase.environment ?? {})) {
      validateEncodedValue(encoded, `${testCase.id}.environment.${name}`, errors);
    }
    for (const [index, step] of (testCase.steps ?? []).entries()) {
      if (!OPERATION_SET.has(step.operation)) errors.push(`${testCase.id}.steps[${index}]: unknown operation '${step.operation}'`);
      for (const [argumentIndex, argument] of (step.arguments ?? []).entries()) {
        if (Object.hasOwn(argument, "literal")) {
          validateEncodedValue(argument.literal, `${testCase.id}.steps[${index}].arguments[${argumentIndex}]`, errors);
        }
      }
    }
    try {
      const actual = runCase(testCase);
      if (JSON.stringify(actual) !== JSON.stringify(testCase.expected)) {
        errors.push(`${testCase.id}: Node result differs from frozen expected\nactual=${JSON.stringify(actual)}`);
      }
    } catch (error) {
      errors.push(`${testCase.id}: ${error.stack ?? error}`);
    }
  }

  if (caseIds.size !== 33) errors.push(`expected 33 exact cases, got ${caseIds.size}`);
  if ((spec.residualBlockers ?? []).length !== 3) errors.push("exactly three real residual blockers must be frozen");
  const categories = (spec.residualBlockers ?? []).map((blocker) => blocker.semanticClass);
  if (categories.filter((category) => category === "static_runtime_builtin").length !== 2) {
    errors.push("expected exactly two static-runtime residual blockers");
  }
  if (categories.filter((category) => category === "map_membership_truthiness").length !== 1) {
    errors.push("expected exactly one Map/membership/truthiness residual blocker");
  }
  for (const blocker of spec.residualBlockers ?? []) {
    if (!caseIds.has(blocker.witnessCaseId)) errors.push(`${blocker.id}: unknown witnessCaseId '${blocker.witnessCaseId}'`);
    if (blocker.frozenOutcome?.status !== "semantic_mismatch" || blocker.frozenOutcome?.replayConfirmed !== false) {
      errors.push(`${blocker.id}: residual must remain an explicit non-confirmed semantic mismatch`);
    }
    if (blocker.frozenOutcome?.mismatchStmtIndex !== blocker.etsIr?.semanticCallStmtIndex) {
      errors.push(`${blocker.id}: mismatch stmt must identify the frozen semantic call exactly`);
    }
    if (!(blocker.etsIr.semanticCallStmtIndex < blocker.etsIr.conditionStmtIndex &&
        blocker.etsIr.conditionStmtIndex < blocker.etsIr.ifStmtIndex)) {
      errors.push(`${blocker.id}: semantic call, condition and target if stmt order is invalid`);
    }
    for (const label of blocker.capability?.labels ?? []) {
      if (!CAPABILITY_LABELS.has(label)) errors.push(`${blocker.id}: unknown closed capability label '${label}'`);
    }
    for (const tag of blocker.capability?.semanticTags ?? []) {
      if (!SEMANTIC_TAGS.has(tag)) errors.push(`${blocker.id}: unknown semantic tag '${tag}'`);
    }
  }
  if (!(spec.unsupported ?? []).length) errors.push("unsupported exact-subset boundary must be non-empty");
  for (const item of spec.unsupported ?? []) {
    if (!item.id || !CAPABILITY_STATUSES.has(item.expectedStatus) || !item.reason) {
      errors.push(`malformed unsupported entry '${item.id}'`);
    }
  }

  validateResidualEvidence(spec, errors, repoRoot);
  return {
    valid: errors.length === 0,
    errors,
    cases: caseIds.size,
    residualBlockers: spec.residualBlockers?.length ?? 0,
    operations: OPERATION_NAMES.length,
  };
}

function main(args = process.argv.slice(2)) {
  if (args.length !== 1 || args[0] !== "--validate") {
    process.stderr.write("usage: node builtin-spec-runner.cjs --validate\n");
    return 64;
  }
  const report = validateSpec(loadSpec());
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  return report.valid ? 0 : 1;
}

if (require.main === module) process.exitCode = main();

module.exports = {
  OPERATION_NAMES,
  findRepoRoot,
  loadSpec,
  runCase,
  validateSpec,
};
