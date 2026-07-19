"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const fixture = require("./IteratorSemanticsFixture.ts");

const OPERATION_NAMES = Object.freeze([
  "iterator.nextSequence",
  "iterator.self",
  "forOf.collect",
  "forOf.collectTracked",
  "forOf.break",
  "forOf.return",
  "forOf.throw",
  "iterator.return",
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
  "symbol_iterator",
  "iterator_next",
  "done_stability",
  "iterator_self",
  "array_iteration",
  "sparse_array",
  "string_code_points",
  "map_entries",
  "set_values",
  "insertion_order",
  "same_value_zero",
  "for_of",
  "iterator_close",
  "iterator_return",
  "abrupt_completion",
  "custom_iterable_subset",
  "async_iterator",
  "generator_yield",
  "yield_star",
  "mutation_during_iteration",
  "iterator_reentrancy",
  "proxy_trap",
  "invalid_iterator_result",
  "iterator_helpers",
]);
const TERMINAL_OUTCOMES = new Set([
  "replay_confirmed",
  "exact_capability_mismatch",
  "exact_unsupported",
  "needs_dynamic_probe",
  "external_only",
]);
const NUMBER_LITERALS = new Set(["NaN", "Infinity", "-Infinity", "-0"]);

function loadSpec(specPath = path.join(__dirname, "iterator-semantics-v1.json")) {
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
    case "array": {
      const elements = encoded.elements ?? [];
      const result = new Array(elements.length);
      elements.forEach((element, index) => {
        if (element.kind !== "hole") result[index] = decodeValue(element);
      });
      return result;
    }
    case "map": {
      const result = new Map();
      for (const entry of encoded.entries ?? []) {
        result.set(decodeValue(entry.key), decodeValue(entry.value));
      }
      return result;
    }
    case "set": {
      const result = new Set();
      for (const value of encoded.values ?? []) result.add(decodeValue(value));
      return result;
    }
    case "customIterable": return fixture.createTrackedIterable(
      (encoded.values ?? []).map(decodeValue),
      encoded.hasReturn === true,
    );
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
  if (Array.isArray(value)) return { kind: "array", elements: value.map(encodeValue) };
  if (typeof value === "object" && typeof value.done === "boolean" && Object.hasOwn(value, "value")) {
    return { kind: "iteratorResult", value: encodeValue(value.value), done: value.done };
  }
  if (typeof value === "object") {
    return {
      kind: "record",
      fields: Object.fromEntries(Object.entries(value).map(([key, child]) => [key, encodeValue(child)])),
    };
  }
  throw new Error(`result type '${typeof value}' is outside the frozen result subset`);
}

function formatValue(value) {
  const encoded = value?.kind ? value : encodeValue(value);
  switch (encoded.kind) {
    case "undefined": return "undefined";
    case "null": return "null";
    case "boolean": return `boolean:${encoded.value}`;
    case "string": return `string:${JSON.stringify(encoded.value)}`;
    case "number": return `number:${encoded.value}`;
    case "array": return `array:[${encoded.elements.map(formatValue).join(", ")}]`;
    case "iteratorResult": return `{value:${formatValue(encoded.value)}, done:${encoded.done}}`;
    case "record": return `record:{${Object.entries(encoded.fields)
      .map(([key, child]) => `${key}=${formatValue(child)}`).join(", ")}}`;
    default: throw new Error(`cannot format '${encoded.kind}' in a trace`);
  }
}

function formatEvent(event, inputKind) {
  switch (event.type) {
    case "get": return `Symbol.iterator(${inputKind}) -> iterator`;
    case "next": return `next() -> ${formatValue(event.result)}`;
    case "self": return `iterator[Symbol.iterator]() === iterator -> boolean:${event.same}`;
    case "value": return `for-of value -> ${formatValue(event.value)}`;
    case "complete": return "for-of complete";
    case "return": return `return() -> ${formatValue(event.result)}`;
    case "break": return "for-of break";
    case "functionReturn": return "for-of function return";
    case "caught": return `for-of throw -> string:${JSON.stringify(event.message)}`;
    default: throw new Error(`unknown trace event '${event.type}'`);
  }
}

function executeCase(testCase, subject) {
  const count = testCase.arguments?.count;
  switch (testCase.operation) {
    case "iterator.nextSequence": return fixture.nextSequence(subject, count);
    case "iterator.self": return fixture.iteratorSelf(subject);
    case "forOf.collect": return fixture.collectForOf(subject);
    case "forOf.collectTracked": return fixture.collectTracked(subject);
    case "forOf.break": return fixture.breakTracked(subject);
    case "forOf.return": return fixture.returnTracked(subject);
    case "forOf.throw": return fixture.throwTracked(subject);
    case "iterator.return": return fixture.directReturn(subject);
    default: throw new Error(`unknown operation '${testCase.operation}'`);
  }
}

function runCase(testCase) {
  const subject = decodeValue(testCase.iterable);
  const execution = executeCase(testCase, subject);
  return {
    result: encodeValue(execution.result),
    trace: execution.events.map((event) => formatEvent(event, testCase.iterable.kind)),
  };
}

function validateEncodedValue(value, location, errors) {
  const kinds = new Set([
    "undefined", "null", "boolean", "string", "number", "hole", "array",
    "map", "set", "customIterable", "iteratorResult", "record",
  ]);
  if (value == null || typeof value !== "object" || !kinds.has(value.kind)) {
    errors.push(`${location}: unknown or missing value kind`);
    return;
  }
  if (value.kind === "number") {
    const valid = typeof value.value === "string" &&
      (NUMBER_LITERALS.has(value.value) || Number.isFinite(Number(value.value)));
    if (!valid) errors.push(`${location}: invalid number literal '${value.value}'`);
  }
  if (value.kind === "array") {
    (value.elements ?? []).forEach((child, index) => validateEncodedValue(child, `${location}.elements[${index}]`, errors));
  }
  if (value.kind === "map") {
    (value.entries ?? []).forEach((entry, index) => {
      validateEncodedValue(entry.key, `${location}.entries[${index}].key`, errors);
      validateEncodedValue(entry.value, `${location}.entries[${index}].value`, errors);
    });
  }
  if (value.kind === "set" || value.kind === "customIterable") {
    (value.values ?? []).forEach((child, index) => validateEncodedValue(child, `${location}.values[${index}]`, errors));
  }
  if (value.kind === "iteratorResult") validateEncodedValue(value.value, `${location}.value`, errors);
  if (value.kind === "record") {
    for (const [key, child] of Object.entries(value.fields ?? {})) {
      validateEncodedValue(child, `${location}.fields.${key}`, errors);
    }
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

function sha256(filePath) {
  return crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function targetKey(target) {
  return `${target.projectId}\t${target.etsIr.branchId}`;
}

function validateCapability(owner, errors) {
  const capability = owner.capability ?? owner;
  if (!(capability.labels?.length > 0)) errors.push(`${owner.id}: capability labels must be non-empty`);
  for (const label of capability.labels ?? []) {
    if (!CAPABILITY_LABELS.has(label)) errors.push(`${owner.id}: unknown closed capability label '${label}'`);
  }
  for (const tag of capability.semanticTags ?? []) {
    if (!SEMANTIC_TAGS.has(tag)) errors.push(`${owner.id}: unknown semantic tag '${tag}'`);
    if (CAPABILITY_LABELS.has(tag)) errors.push(`${owner.id}: capability label '${tag}' leaked into semanticTags`);
  }
  if (!CAPABILITY_STATUSES.has(capability.expectedStatus)) {
    errors.push(`${owner.id}: unknown capability status '${capability.expectedStatus}'`);
  }
}

function findTargetObservation(reports, target, scenario) {
  return reports
    .filter((report) => report.projectId === target.projectId && report.scenario === scenario)
    .flatMap((report) => report.methods ?? [])
    .filter((method) => method.methodId === target.etsIr.methodId)
    .flatMap((method) => method.symbolic?.targets ?? [])
    .filter((candidate) => candidate.branchId === target.etsIr.branchId);
}

function validateRealEvidence(spec, errors, repoRoot) {
  if (!repoRoot) {
    errors.push("repository root not found; frozen iterator evidence cannot be checked");
    return;
  }
  const baseline = spec.baselineEvidence;
  const observationsPath = path.join(repoRoot, baseline.observationsPath);
  if (!fs.existsSync(observationsPath)) {
    errors.push(`missing observations '${observationsPath}'`);
    return;
  }
  if (sha256(observationsPath) !== baseline.observationsSha256) {
    errors.push("frozen observations SHA-256 differs");
  }
  const reports = JSON.parse(fs.readFileSync(observationsPath, "utf8")).reports;
  const broadHybrid = reports.filter((report) => report.scenario === "internal-pbt-usvm");
  const allReachedNotReplayed = broadHybrid
    .flatMap((report) => report.methods ?? [])
    .flatMap((method) => (method.symbolic?.targets ?? []).map((target) => ({ method, target })))
    .filter(({ target }) => target.reached === true && target.replayConfirmed === false);
  if (allReachedNotReplayed.length !== baseline.broadReachedNotReplayed) {
    errors.push(`expected ${baseline.broadReachedNotReplayed} broad reached-not-replayed targets, got ${allReachedNotReplayed.length}`);
  }

  const iteratorMethods = new Set(baseline.iteratorMethodIds);
  const observedIteratorKeys = new Set();
  broadHybrid.forEach((report) => {
    (report.methods ?? []).filter((method) => iteratorMethods.has(method.methodId)).forEach((method) => {
      (method.symbolic?.targets ?? [])
        .filter((target) => target.reached === true && target.replayConfirmed === false)
        .forEach((target) => observedIteratorKeys.add(`${report.projectId}\t${target.branchId}`));
    });
  });
  const frozenKeys = new Set(spec.realTargets.map(targetKey));
  if (observedIteratorKeys.size !== 9 || frozenKeys.size !== 9) {
    errors.push(`iterator target inventory must be exactly 9/9, got ${observedIteratorKeys.size}/${frozenKeys.size}`);
  }
  for (const key of observedIteratorKeys) {
    if (!frozenKeys.has(key)) errors.push(`silent iterator target drop '${key}'`);
  }
  for (const key of frozenKeys) {
    if (!observedIteratorKeys.has(key)) errors.push(`fixture target absent from frozen observations '${key}'`);
  }

  const manifestCache = new Map();
  for (const target of spec.realTargets) {
    validateCapability(target, errors);
    const manifestEvidence = baseline.targetManifests[target.projectId];
    if (!manifestEvidence) {
      errors.push(`${target.id}: no target manifest evidence for project`);
      continue;
    }
    if (!manifestCache.has(target.projectId)) {
      const manifestPath = path.join(repoRoot, manifestEvidence.path);
      if (sha256(manifestPath) !== manifestEvidence.sha256) {
        errors.push(`${target.id}: target manifest SHA-256 differs`);
      }
      manifestCache.set(target.projectId, JSON.parse(fs.readFileSync(manifestPath, "utf8")));
    }
    const manifest = manifestCache.get(target.projectId);
    const method = manifest.methods.find((candidate) => candidate.methodId === target.etsIr.methodId);
    const branch = method?.branches?.find((candidate) => candidate.branchId === target.etsIr.branchId);
    if (!branch) errors.push(`${target.id}: branch absent from frozen target manifest`);
    else {
      for (const field of ["ifStmtIndex", "successorOrdinal", "successorStmtIndex"]) {
        if (branch[field] !== target.etsIr[field]) errors.push(`${target.id}: ${field} differs from target manifest`);
      }
      if (JSON.stringify(branch.conditionOrigin) !== JSON.stringify(target.source.conditionOrigin)) {
        errors.push(`${target.id}: condition source origin differs from target manifest`);
      }
    }
    for (const scenario of target.observationScenarios) {
      const observations = findTargetObservation(reports, target, scenario);
      if (observations.length !== 1) errors.push(`${target.id}: expected one '${scenario}' observation, got ${observations.length}`);
      if (observations.some((item) => item.reached !== true || item.replayConfirmed !== false)) {
        errors.push(`${target.id}: '${scenario}' is not reached=true/replayConfirmed=false`);
      }
    }
    if (target.frozenOutcome.status !== "reached_not_replayed" || target.frozenOutcome.replayConfirmed !== false) {
      errors.push(`${target.id}: legacy target status is not frozen exactly`);
    }
    if (!TERMINAL_OUTCOMES.has(target.expectedTerminalOutcome.status)) {
      errors.push(`${target.id}: unknown expected terminal outcome`);
    }
    if (target.expectedTerminalOutcome.status === "exact_capability_mismatch") {
      const missing = target.expectedTerminalOutcome.missingCapabilityLabels ?? [];
      if (!missing.length || missing.some((label) => !CAPABILITY_LABELS.has(label) || label === "iterator")) {
        errors.push(`${target.id}: malformed exact capability mismatch`);
      }
    }
  }
  const terminalCounts = spec.realTargets.reduce((counts, target) => {
    const status = target.expectedTerminalOutcome.status;
    counts[status] = (counts[status] ?? 0) + 1;
    return counts;
  }, {});
  if (terminalCounts.replay_confirmed !== 7 || terminalCounts.exact_capability_mismatch !== 2) {
    errors.push(`expected 7 replay-confirmed and 2 exact capability mismatches, got ${JSON.stringify(terminalCounts)}`);
  }

  for (const event of spec.collectionUnsupportedEvidence.runs) {
    const report = reports.find((candidate) => candidate.projectId === event.projectId && candidate.scenario === event.scenario);
    const method = report?.methods?.find((candidate) => candidate.methodId === event.methodId);
    const reasonCount = method?.pbt?.unsupportedReasons?.[event.reason] ?? 0;
    if (report?.sourceReport !== event.sourceReport || report?.sourceReportSha256 !== event.sourceReportSha256) {
      errors.push(`${event.id}: source report provenance differs`);
    }
    if (method?.pbt?.executions !== event.executions || method?.pbt?.unsupported !== event.unsupported ||
        reasonCount !== event.reasonEvents) {
      errors.push(`${event.id}: frozen 25/25 event counts differ`);
    }
    if (event.countUnit !== "pbt_execution_outcome" || event.isUniqueTargetCount !== false) {
      errors.push(`${event.id}: 25/25 must remain an execution-event count, not a target count`);
    }
  }
}

function validateSpec(spec, { repoRoot = findRepoRoot() } = {}) {
  const errors = [];
  if (spec.schemaVersion !== 1) errors.push(`schemaVersion must be 1, got '${spec.schemaVersion}'`);
  if (spec.contractId !== "usvm-ts-pbt.iterator.exact.v1") errors.push("unexpected contractId");
  if (JSON.stringify(spec.requiredOperations) !== JSON.stringify(OPERATION_NAMES)) {
    errors.push("requiredOperations differs from the executable operation set");
  }
  const caseIds = new Set();
  for (const testCase of spec.cases ?? []) {
    if (!testCase.id || caseIds.has(testCase.id)) errors.push(`duplicate or blank case id '${testCase.id}'`);
    caseIds.add(testCase.id);
    if (!testCase.sourceCallableId?.startsWith("ts:semantic/iterator/IteratorSemanticsFixture.ts::free:")) {
      errors.push(`${testCase.id}: unstable or missing sourceCallableId`);
    }
    if (!OPERATION_SET.has(testCase.operation)) errors.push(`${testCase.id}: unknown operation '${testCase.operation}'`);
    validateEncodedValue(testCase.iterable, `${testCase.id}.iterable`, errors);
    validateEncodedValue(testCase.expected?.result, `${testCase.id}.expected.result`, errors);
    validateCapability(testCase, errors);
    if (testCase.capability?.expectedOutcome !== "exact") errors.push(`${testCase.id}: expected outcome must be exact`);
    try {
      const actual = runCase(testCase);
      if (JSON.stringify(actual) !== JSON.stringify(testCase.expected)) {
        errors.push(`${testCase.id}: Node result differs\nactual=${JSON.stringify(actual)}`);
      }
    } catch (error) {
      errors.push(`${testCase.id}: ${error.stack ?? error}`);
    }
  }
  if (caseIds.size !== 26) errors.push(`expected 26 exact cases, got ${caseIds.size}`);
  if ((spec.realTargets ?? []).length !== 9) errors.push("exactly nine real iterator targets must be frozen");
  if ((spec.collectionUnsupportedEvidence?.runs ?? []).length !== 2) {
    errors.push("both PBT-only and hybrid 25/25 provenance rows must be frozen");
  }
  if (!(spec.unsupported ?? []).length) errors.push("unsupported iterator boundary must be non-empty");
  for (const item of spec.unsupported ?? []) {
    validateCapability(item, errors);
    if (!item.reason || !TERMINAL_OUTCOMES.has(item.terminalOutcome)) {
      errors.push(`${item.id}: malformed unsupported boundary entry`);
    }
  }
  const yieldItems = (spec.unsupported ?? []).filter((item) =>
    item.capability.semanticTags.includes("generator_yield") || item.capability.semanticTags.includes("yield_star"));
  if (yieldItems.length !== 2 || yieldItems.some((item) =>
    !item.capability.labels.includes("spread_yield") || item.capability.expectedStatus === "supported")) {
    errors.push("generator/yield boundaries must stay separate from the supported iterator subset");
  }
  validateRealEvidence(spec, errors, repoRoot);
  return {
    valid: errors.length === 0,
    errors,
    cases: caseIds.size,
    operations: OPERATION_NAMES.length,
    realTargets: spec.realTargets?.length ?? 0,
    expectedReplayConfirmed: spec.realTargets?.filter(
      (target) => target.expectedTerminalOutcome.status === "replay_confirmed",
    ).length ?? 0,
    expectedCapabilityMismatches: spec.realTargets?.filter(
      (target) => target.expectedTerminalOutcome.status === "exact_capability_mismatch",
    ).length ?? 0,
    frozenUnsupportedEventsPerRun: 25,
  };
}

function main(args = process.argv.slice(2)) {
  if (args.length === 1 && args[0] === "--validate") {
    const report = validateSpec(loadSpec());
    process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
    return report.valid ? 0 : 1;
  }
  if (args.length === 2 && args[0] === "--actual") {
    const testCase = loadSpec().cases.find((candidate) => candidate.id === args[1]);
    if (!testCase) throw new Error(`unknown case '${args[1]}'`);
    process.stdout.write(`${JSON.stringify(runCase(testCase), null, 2)}\n`);
    return 0;
  }
  process.stderr.write("usage: node iterator-spec-runner.cjs --validate | --actual <case-id>\n");
  return 64;
}

if (require.main === module) process.exitCode = main();

module.exports = {
  OPERATION_NAMES,
  findRepoRoot,
  loadSpec,
  runCase,
  validateSpec,
};
