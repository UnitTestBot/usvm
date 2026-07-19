"use strict";

const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const fixtureSource = require("./CallableSemanticsFixture.ts");
const librarySource = require("./CallableSemanticsLibrary.ts");

const CAPABILITY_STATUSES = new Set([
  "supported",
  "supported_with_flag",
  "external_only",
  "unsupported",
  "needs_dynamic_probe",
]);
// Byte-for-byte WP-CAP v1 labels. Fine-grained concepts belong in semanticTags.
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
  "etc_v2_callable_reference",
  "etc_v2_constructor_plan",
  "direct_dispatch",
  "top_level_arrow",
  "imported_callable",
  "function_field",
  "receiver_binding",
  "instance_method",
  "static_method",
  "explicit_call",
  "recursion",
  "callback_arity",
  "extra_arguments",
  "missing_arguments",
  "stable_reject",
  "captured_environment",
  "closure",
  "bound_callable",
  "proxy_callable",
  "async_callable",
  "generator_callable",
  "dynamic_callable",
  "shared_module_callable",
  "materialized_function_value",
  "callable_dispatch",
]);
const REFERENCE_KINDS = new Set(["function", "class", "staticMethod", "instanceMethod", "arrow"]);
const DISPATCH_KINDS = new Set(["direct", "field", "imported", "call"]);
const RECEIVER_BINDINGS = new Set(["none", "property", "constructor_instance", "explicit_call"]);
const REQUIRED_FLAGS = new Set(["callableValueModel", "moduleRuntimeModel"]);
const REJECTION_CODES = new Set([
  "captured_mutable_environment_not_exact",
  "lexical_this_environment_not_exact",
  "bound_callable_plan_missing",
  "proxy_callable_requires_dynamic_probe",
  "async_callable_not_exact",
  "generator_callable_not_exact",
  "unresolved_callable_reference",
]);
const FROZEN_BRANCH_IDS = new Set([
  "arrays.ts::%dflt::indexOf/3#s9:0->10",
  "arrays.ts::%dflt::indexOf/3#s9:1->18",
  "arrays.ts::%dflt::lastIndexOf/3#s9:0->10",
  "arrays.ts::%dflt::lastIndexOf/3#s9:1->18",
  "arrays.ts::%dflt::remove/3#s6:0->7",
  "arrays.ts::%dflt::frequency/3#s10:0->11",
  "arrays.ts::%dflt::frequency/3#s10:1->20",
  "arrays.ts::%dflt::equals/3#s9:0->10",
  "arrays.ts::%dflt::equals/3#s9:1->12",
  "arrays.ts::%dflt::equals/3#s15:0->16",
  "arrays.ts::%dflt::equals/3#s15:1->26",
]);
const EXACT_CASE_IDS = new Set([
  "direct-function",
  "top-level-arrow",
  "imported-arrow-direct",
  "imported-function-direct",
  "function-in-field",
  "field-receiver-binding",
  "constructor-instance-method",
  "static-method",
  "explicit-call-receiver",
  "direct-recursion",
  "callback-extra-arguments",
  "callback-missing-argument",
  "imported-arrow-in-field",
]);

function referenceKey(reference) {
  return `${reference.modulePath}#${reference.exportName}#${reference.callableKind}`;
}

const REFERENCES = new Map();

function register(reference, value, stableCallableId) {
  const key = referenceKey(reference);
  if (REFERENCES.has(key)) throw new Error(`duplicate callable registry key '${key}'`);
  REFERENCES.set(key, Object.freeze({
    reference: Object.freeze({ ...reference }),
    stableCallableId,
    value,
    declaredArity: value.length,
  }));
}

const FIXTURE_MODULE = "semantics/callable/CallableSemanticsFixture.ts";
const LIBRARY_MODULE = "semantics/callable/CallableSemanticsLibrary.ts";
register(
  { modulePath: FIXTURE_MODULE, exportName: "directAdd", callableKind: "function" },
  fixtureSource.directAdd,
  "ts:semantic/callable/CallableSemanticsFixture.ts::free:directAdd/2",
);
register(
  { modulePath: FIXTURE_MODULE, exportName: "topLevelArrow", callableKind: "arrow" },
  fixtureSource.topLevelArrow,
  "ts:semantic/callable/CallableSemanticsFixture.ts::arrow:topLevelArrow/1",
);
register(
  { modulePath: LIBRARY_MODULE, exportName: "importedArrow", callableKind: "arrow" },
  librarySource.importedArrow,
  "ts:semantic/callable/CallableSemanticsLibrary.ts::arrow:importedArrow/2",
);
register(
  { modulePath: LIBRARY_MODULE, exportName: "importedOffset", callableKind: "function" },
  librarySource.importedOffset,
  "ts:semantic/callable/CallableSemanticsLibrary.ts::free:importedOffset/1",
);
register(
  { modulePath: FIXTURE_MODULE, exportName: "fieldMultiply", callableKind: "function" },
  fixtureSource.fieldMultiply,
  "ts:semantic/callable/CallableSemanticsFixture.ts::free:fieldMultiply/2",
);
register(
  { modulePath: FIXTURE_MODULE, exportName: "readBase", callableKind: "function" },
  fixtureSource.readBase,
  "ts:semantic/callable/CallableSemanticsFixture.ts::free:readBase/1",
);
register(
  { modulePath: FIXTURE_MODULE, exportName: "ReceiverBox", callableKind: "class" },
  fixtureSource.ReceiverBox,
  "ts:semantic/callable/CallableSemanticsFixture.ts::class:ReceiverBox/1",
);
register(
  { modulePath: FIXTURE_MODULE, exportName: "ReceiverBox.prototype.add", callableKind: "instanceMethod" },
  fixtureSource.ReceiverBox.prototype.add,
  "ts:semantic/callable/CallableSemanticsFixture.ts::instance:ReceiverBox.add/1",
);
register(
  { modulePath: FIXTURE_MODULE, exportName: "ReceiverBox.staticSum", callableKind: "staticMethod" },
  fixtureSource.ReceiverBox.staticSum,
  "ts:semantic/callable/CallableSemanticsFixture.ts::static:ReceiverBox.staticSum/2",
);
register(
  { modulePath: FIXTURE_MODULE, exportName: "recursiveFactorial", callableKind: "function" },
  fixtureSource.recursiveFactorial,
  "ts:semantic/callable/CallableSemanticsFixture.ts::free:recursiveFactorial/1",
);
register(
  { modulePath: FIXTURE_MODULE, exportName: "arityPair", callableKind: "function" },
  fixtureSource.arityPair,
  "ts:semantic/callable/CallableSemanticsFixture.ts::free:arityPair/2",
);

function loadSpec(specPath = path.join(__dirname, "callable-semantics-v1.json")) {
  return JSON.parse(fs.readFileSync(specPath, "utf8"));
}

function resolveReference(reference) {
  const entry = REFERENCES.get(referenceKey(reference));
  if (!entry) {
    throw new Error(`callable reference '${referenceKey(reference)}' is outside the exact registry`);
  }
  if (entry.value === undefined || typeof entry.value !== "function") {
    throw new Error(`callable reference '${referenceKey(reference)}' resolved to a non-callable value`);
  }
  return entry;
}

function decodeNumber(raw) {
  if (raw === "NaN") return Number.NaN;
  if (raw === "Infinity") return Number.POSITIVE_INFINITY;
  if (raw === "-Infinity") return Number.NEGATIVE_INFINITY;
  if (raw === "-0") return -0;
  const value = Number(raw);
  if (!Number.isFinite(value)) throw new Error(`invalid ETC number '${raw}'`);
  return value;
}

function decodeValue(encoded) {
  switch (encoded?.kind) {
    case "undefined": return undefined;
    case "null": return null;
    case "number": return decodeNumber(encoded.value);
    case "boolean": return encoded.value === "true";
    case "string": return encoded.value;
    case "array": return (encoded.elements ?? []).map(decodeValue);
    case "callable": return resolveReference(encoded.callableReference).value;
    case "object": {
      let result;
      if (encoded.constructorPlan) {
        const constructorEntry = resolveReference(encoded.constructorPlan.callable);
        if (constructorEntry.reference.callableKind !== "class") {
          throw new Error("constructorPlan callable must have callableKind=class");
        }
        result = Reflect.construct(
          constructorEntry.value,
          encoded.constructorPlan.arguments.map(decodeValue),
        );
        if (encoded.className && result.constructor.name !== encoded.className) {
          throw new Error(`constructor produced '${result.constructor.name}', expected '${encoded.className}'`);
        }
      } else {
        result = {};
      }
      for (const property of encoded.properties ?? []) {
        if (Object.hasOwn(result, property.key)) {
          throw new Error(`duplicate or constructor-owned property '${property.key}'`);
        }
        Object.defineProperty(result, property.key, {
          configurable: true,
          enumerable: true,
          writable: true,
          value: decodeValue(property.value),
        });
      }
      return result;
    }
    case "unrepresentable": {
      throw new Error(`unrepresentable/${encoded.unrepresentableKind}: ${encoded.reason}`);
    }
    default: throw new Error(`ETC kind '${encoded?.kind}' is outside the callable fixture subset`);
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
  if (typeof value === "number") return encodeNumber(value);
  if (typeof value === "boolean") return { kind: "boolean", value: String(value) };
  if (typeof value === "string") return { kind: "string", value };
  if (typeof value === "function") {
    throw new Error("callable result has no ETC materialization plan; refusing function-to-undefined fallback");
  }
  throw new Error(`result type '${typeof value}' is outside the exact callable result subset`);
}

function formatEncoded(encoded) {
  switch (encoded.kind) {
    case "undefined": return "undefined";
    case "null": return "null";
    case "number": return `number:${encoded.value}`;
    case "boolean": return `boolean:${encoded.value}`;
    case "string": return `string:${JSON.stringify(encoded.value)}`;
    default: throw new Error(`cannot format ETC kind '${encoded.kind}' in a trace`);
  }
}

function jsonPointer(root, pointer) {
  if (pointer === "") return root;
  if (typeof pointer !== "string" || !pointer.startsWith("/")) {
    throw new Error(`invalid JSON pointer '${pointer}'`);
  }
  return pointer.slice(1).split("/").reduce((current, token) => {
    const key = token.replaceAll("~1", "/").replaceAll("~0", "~");
    if (current == null || !Object.hasOwn(current, key)) {
      throw new Error(`JSON pointer '${pointer}' does not resolve at '${key}'`);
    }
    return current[key];
  }, root);
}

function receiverLabel(receiver, encoded) {
  if (receiver === undefined) return "undefined";
  if (encoded?.className) return `object:${encoded.className}`;
  return "object";
}

function materializeTrace(plan, encodedCallable, entry) {
  const reference = encodedCallable.callableReference;
  return `materialize callable ${plan.callablePath} ${reference.modulePath}#${reference.exportName} ` +
    `kind=${reference.callableKind} arity=${entry.declaredArity} -> ${entry.stableCallableId}`;
}

function constructorTrace(plan, encodedReceiver) {
  if (!encodedReceiver?.constructorPlan) return null;
  const constructorPlan = encodedReceiver.constructorPlan;
  const args = constructorPlan.arguments.map((value) => formatEncoded(value)).join(",");
  return `materialize constructor ${plan.receiverPath} ${constructorPlan.callable.modulePath}#` +
    `${constructorPlan.callable.exportName} args=[${args}] -> object:${encodedReceiver.className}`;
}

function runCase(testCase) {
  const plan = testCase.materialization;
  if (plan.outcome === "rejected") {
    const value = jsonPointer(testCase.etcCase, plan.valuePath);
    if (value.kind !== "unrepresentable" || value.unrepresentableKind !== "function") {
      throw new Error(`${testCase.id}: stable callable rejection must point to unrepresentable/function`);
    }
    return {
      outcome: "rejected",
      reasonCode: plan.reasonCode,
      trace: [`reject callable ${plan.valuePath} kind=function -> ${plan.reasonCode}`],
    };
  }

  const encodedCallable = jsonPointer(testCase.etcCase, plan.callablePath);
  if (encodedCallable.kind !== "callable") {
    throw new Error(`${testCase.id}: callablePath does not identify an ETC callable`);
  }
  const entry = resolveReference(encodedCallable.callableReference);
  if (entry.stableCallableId !== plan.stableCallableId) {
    throw new Error(`${testCase.id}: ${entry.stableCallableId} != ${plan.stableCallableId}`);
  }
  if (entry.declaredArity !== plan.declaredArity) {
    throw new Error(`${testCase.id}: source arity ${entry.declaredArity} != plan arity ${plan.declaredArity}`);
  }

  const encodedReceiver = plan.receiverPath ? jsonPointer(testCase.etcCase, plan.receiverPath) : { kind: "undefined" };
  const receiver = plan.receiverPath ? decodeValue(encodedReceiver) : undefined;
  const args = plan.argumentPaths.map((pointer) => decodeValue(jsonPointer(testCase.etcCase, pointer)));
  let result;
  switch (plan.dispatchKind) {
    case "direct":
    case "imported":
      result = fixtureSource.invokeDirect(entry.value, args);
      break;
    case "field":
      if (receiver?.[plan.fieldName] !== entry.value) {
        throw new Error(`${testCase.id}: field '${plan.fieldName}' is not the planned callable identity`);
      }
      result = fixtureSource.invokeField(receiver, plan.fieldName, args);
      break;
    case "call":
      result = fixtureSource.invokeWithCall(entry.value, receiver, args);
      break;
    default: throw new Error(`${testCase.id}: unknown dispatch kind '${plan.dispatchKind}'`);
  }

  const encodedResult = encodeValue(result);
  const traces = [materializeTrace(plan, encodedCallable, entry)];
  const receiverMaterialization = constructorTrace(plan, encodedReceiver);
  if (receiverMaterialization) traces.push(receiverMaterialization);
  const field = plan.dispatchKind === "field" ? ` field=${plan.fieldName}` : "";
  traces.push(
    `dispatch ${plan.dispatchKind} ${entry.stableCallableId} ` +
    `receiver=${receiverLabel(receiver, encodedReceiver)}${field} ` +
    `args=[${args.map((value) => formatEncoded(encodeValue(value))).join(",")}] -> ${formatEncoded(encodedResult)}`,
  );
  return { outcome: "materialized", result: encodedResult, trace: traces };
}

function validateCallableReference(reference, location, errors, { mustResolve }) {
  if (!reference || typeof reference !== "object") {
    errors.push(`${location}: callable reference is missing`);
    return;
  }
  if (!reference.modulePath || !reference.exportName || !REFERENCE_KINDS.has(reference.callableKind)) {
    errors.push(`${location}: malformed ETC v2 callable reference`);
    return;
  }
  if (mustResolve && !REFERENCES.has(referenceKey(reference))) {
    errors.push(`${location}: callable reference is not in the exact registry`);
  }
}

function validateEtcValue(value, location, errors, { mustResolve }) {
  if (!value || typeof value !== "object" || typeof value.kind !== "string") {
    errors.push(`${location}: ETC value is missing kind`);
    return;
  }
  switch (value.kind) {
    case "undefined":
    case "null":
      break;
    case "number":
      try { decodeNumber(value.value); } catch (error) { errors.push(`${location}: ${error.message}`); }
      break;
    case "boolean":
      if (value.value !== "true" && value.value !== "false") errors.push(`${location}: invalid ETC boolean`);
      break;
    case "string":
      if (typeof value.value !== "string") errors.push(`${location}: invalid ETC string`);
      break;
    case "array":
      (value.elements ?? []).forEach((child, index) =>
        validateEtcValue(child, `${location}.elements[${index}]`, errors, { mustResolve }));
      break;
    case "object": {
      if (!Array.isArray(value.properties)) errors.push(`${location}: ETC object properties must be an array`);
      const keys = new Set();
      for (const [index, property] of (value.properties ?? []).entries()) {
        if (keys.has(property.key)) errors.push(`${location}: duplicate property '${property.key}'`);
        keys.add(property.key);
        validateEtcValue(property.value, `${location}.properties[${index}].value`, errors, { mustResolve });
      }
      if (value.constructorPlan) {
        validateCallableReference(value.constructorPlan.callable, `${location}.constructorPlan.callable`, errors, { mustResolve });
        if (value.constructorPlan.callable?.callableKind !== "class") {
          errors.push(`${location}: constructorPlan reference must have callableKind=class`);
        }
        for (const [index, argument] of (value.constructorPlan.arguments ?? []).entries()) {
          validateEtcValue(argument, `${location}.constructorPlan.arguments[${index}]`, errors, { mustResolve });
        }
      }
      break;
    }
    case "callable":
      validateCallableReference(value.callableReference, `${location}.callableReference`, errors, { mustResolve });
      break;
    case "unrepresentable":
      if (!value.reason || value.unrepresentableKind !== "function") {
        errors.push(`${location}: callable boundary must be explicit unrepresentable/function`);
      }
      break;
    default: errors.push(`${location}: ETC kind '${value.kind}' is outside this exact fixture`);
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

function validateResidualEvidence(spec, errors, repoRoot) {
  if (!repoRoot) {
    errors.push("repository root not found; frozen callable evidence cannot be checked");
    return;
  }
  const evidence = spec.baselineEvidence;
  const observationsPath = path.join(repoRoot, evidence.observationsPath);
  const manifestPath = path.join(repoRoot, evidence.targetManifestPath);
  if (!fs.existsSync(observationsPath) || !fs.existsSync(manifestPath)) {
    errors.push("frozen callable observations or target manifest is missing");
    return;
  }
  if (sha256(observationsPath) !== evidence.observationsSha256) errors.push("frozen observations SHA-256 drifted");
  if (sha256(manifestPath) !== evidence.targetManifestSha256) errors.push("frozen target manifest SHA-256 drifted");

  const observations = JSON.parse(fs.readFileSync(observationsPath, "utf8"));
  const reports = observations.reports.filter((report) =>
    report.projectId === evidence.projectId &&
    report.scenario === evidence.scenario &&
    report.denominatorScope === evidence.denominatorScope &&
    report.sourceReport === evidence.sourceReport &&
    report.sourceReportSha256 === evidence.sourceReportSha256);
  if (reports.length !== 1) {
    errors.push(`expected one exact frozen callable report, got ${reports.length}`);
    return;
  }
  const report = reports[0];
  const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8"));

  for (const blocker of spec.residualBlockers) {
    const etsIr = blocker.etsIr;
    const method = manifest.methods.find((candidate) => candidate.methodId === etsIr.methodId);
    const branch = method?.branches?.find((candidate) => candidate.branchId === etsIr.branchId);
    if (!branch) {
      errors.push(`${blocker.id}: target absent from frozen manifest`);
      continue;
    }
    for (const field of ["ifStmtIndex", "successorOrdinal", "successorStmtIndex"]) {
      if (branch[field] !== etsIr[field]) errors.push(`${blocker.id}: ${field} drifted`);
    }
    if (JSON.stringify(branch.conditionOrigin) !== JSON.stringify(etsIr.conditionOrigin)) {
      errors.push(`${blocker.id}: condition source origin drifted`);
    }
    if (JSON.stringify(branch.successorOrigin ?? null) !== JSON.stringify(etsIr.successorOrigin ?? null)) {
      errors.push(`${blocker.id}: successor source origin drifted`);
    }
    const methodReport = report.methods.find((candidate) => candidate.methodId === etsIr.methodId);
    const target = methodReport?.symbolic?.targets?.find((candidate) => candidate.branchId === etsIr.branchId);
    if (!target || target.reached !== true || target.replayConfirmed !== false) {
      errors.push(`${blocker.id}: observation is not reached=true/replayConfirmed=false`);
    }
    const observedFailure = methodReport?.pbt?.failures?.[0]?.description;
    if (observedFailure !== evidence.observedFailure) errors.push(`${blocker.id}: concrete failure class drifted`);
  }
}

function validateSpec(spec, { repoRoot = findRepoRoot() } = {}) {
  const errors = [];
  if (spec.schemaVersion !== 1) errors.push("schemaVersion must be 1");
  if (spec.contractId !== "usvm-ts-pbt.callable.exact.v1") errors.push("unexpected contractId");
  if (spec.etcSchemaVersion !== 2) errors.push("ETC schema version must be exactly 2");
  if (spec.etcSchemaPath !== "usvm-ts-pbt/artifact-contract/v2/external-test-corpus-record.schema.json") {
    errors.push("unexpected ETC v2 schema path");
  }

  const caseIds = new Set();
  const dispatchKinds = new Set();
  let materialized = 0;
  let rejected = 0;
  for (const testCase of spec.cases ?? []) {
    if (!testCase.id || caseIds.has(testCase.id)) errors.push(`duplicate or blank case id '${testCase.id}'`);
    caseIds.add(testCase.id);
    if (!testCase.sourceCallableId?.startsWith("ts:semantic/callable/")) {
      errors.push(`${testCase.id}: unstable sourceCallableId`);
    }
    const etcCase = testCase.etcCase;
    if (!etcCase || etcCase.id !== `etc-${testCase.id}` || etcCase.methodId !== testCase.sourceCallableId ||
        etcCase.generatedAtMs !== 0 || etcCase.path !== `callable-contract:${testCase.id}`) {
      errors.push(`${testCase.id}: malformed stable ETC v2 case origin`);
    }
    if (!Array.isArray(etcCase?.arguments) || etcCase?.metadata?.callableContract !== spec.contractId) {
      errors.push(`${testCase.id}: malformed ETC v2 arguments or metadata`);
    }
    const plan = testCase.materialization;
    const mustResolve = plan?.outcome === "materialized";
    validateEtcValue(etcCase?.receiver, `${testCase.id}.etcCase.receiver`, errors, { mustResolve });
    (etcCase?.arguments ?? []).forEach((argument, index) =>
      validateEtcValue(argument, `${testCase.id}.etcCase.arguments[${index}]`, errors, { mustResolve }));

    if (plan?.outcome === "materialized") {
      materialized += 1;
      dispatchKinds.add(plan.dispatchKind);
      if (!DISPATCH_KINDS.has(plan.dispatchKind) || !RECEIVER_BINDINGS.has(plan.receiverBinding)) {
        errors.push(`${testCase.id}: invalid dispatch or receiver binding`);
      }
      if (!Number.isInteger(plan.declaredArity) || plan.declaredArity < 0 || !Array.isArray(plan.argumentPaths)) {
        errors.push(`${testCase.id}: invalid callable arity or argument paths`);
      }
      if (plan.dispatchKind === "field" && (!plan.fieldName || !plan.receiverPath)) {
        errors.push(`${testCase.id}: field dispatch requires receiverPath and fieldName`);
      }
      if (plan.dispatchKind === "call" && plan.receiverBinding !== "explicit_call") {
        errors.push(`${testCase.id}: .call must use explicit_call receiver binding`);
      }
    } else if (plan?.outcome === "rejected") {
      rejected += 1;
      if (!REJECTION_CODES.has(plan.reasonCode)) errors.push(`${testCase.id}: unknown stable rejection code`);
      if ("callablePath" in plan || "dispatchKind" in plan) {
        errors.push(`${testCase.id}: rejected plan must not contain a speculative dispatch`);
      }
    } else {
      errors.push(`${testCase.id}: every ETC case must materialize or reject exactly`);
    }

    const capability = testCase.capability;
    if (!CAPABILITY_STATUSES.has(capability?.expectedStatus) || !(capability?.labels?.length > 0)) {
      errors.push(`${testCase.id}: malformed capability outcome`);
    }
    for (const label of capability?.labels ?? []) {
      if (!CAPABILITY_LABELS.has(label)) errors.push(`${testCase.id}: unknown closed capability label '${label}'`);
    }
    for (const tag of capability?.semanticTags ?? []) {
      if (!SEMANTIC_TAGS.has(tag)) errors.push(`${testCase.id}: unknown semantic tag '${tag}'`);
      if (CAPABILITY_LABELS.has(tag)) errors.push(`${testCase.id}: capability label leaked into semanticTags`);
    }
    for (const flag of capability?.requiredFlags ?? []) {
      if (!REQUIRED_FLAGS.has(flag)) errors.push(`${testCase.id}: unknown required flag '${flag}'`);
    }
    const expectedOutcome = plan?.outcome === "materialized" ? "exact" : "stable_reject";
    if (capability?.expectedOutcome !== expectedOutcome) errors.push(`${testCase.id}: capability outcome disagrees with plan`);

    try {
      const actual = runCase(testCase);
      if (JSON.stringify(actual) !== JSON.stringify(testCase.expected)) {
        errors.push(`${testCase.id}: Node result differs from frozen expected\nactual=${JSON.stringify(actual)}`);
      }
    } catch (error) {
      errors.push(`${testCase.id}: ${error.stack ?? error}`);
    }
  }

  if (caseIds.size !== 20 || materialized !== 13 || rejected !== 7) {
    errors.push(`expected 20 cases (13 materialized, 7 rejected), got ${caseIds.size} (${materialized}, ${rejected})`);
  }
  if ([...EXACT_CASE_IDS].some((id) => !caseIds.has(id))) errors.push("exact callable feature cases are incomplete");
  if ([...DISPATCH_KINDS].some((kind) => !dispatchKinds.has(kind))) errors.push("direct/field/imported/.call traces are incomplete");
  const requiredDispatchKinds = new Set(spec.requiredDispatchKinds ?? []);
  if (requiredDispatchKinds.size !== DISPATCH_KINDS.size ||
      [...DISPATCH_KINDS].some((kind) => !requiredDispatchKinds.has(kind))) {
    errors.push("requiredDispatchKinds does not match the closed dispatch set");
  }

  const unsupportedByCase = new Map((spec.unsupported ?? []).map((item) => [item.caseId, item]));
  if (unsupportedByCase.size !== 7) errors.push("expected seven explicit unsupported boundaries");
  for (const testCase of spec.cases.filter((candidate) => candidate.materialization.outcome === "rejected")) {
    const boundary = unsupportedByCase.get(testCase.id);
    if (!boundary || boundary.reasonCode !== testCase.materialization.reasonCode ||
        boundary.expectedStatus !== testCase.capability.expectedStatus || !boundary.reason) {
      errors.push(`${testCase.id}: rejected case and unsupported boundary disagree`);
    }
  }

  const blockerIds = new Set();
  const branchIds = new Set();
  for (const blocker of spec.residualBlockers ?? []) {
    if (!blocker.id || blockerIds.has(blocker.id)) errors.push(`duplicate residual id '${blocker.id}'`);
    blockerIds.add(blocker.id);
    const branchId = blocker.etsIr?.branchId;
    if (!branchId || branchIds.has(branchId)) errors.push(`${blocker.id}: duplicate or blank union branchId`);
    branchIds.add(branchId);
    if (blocker.provenanceScope !== "shared_module_callable" ||
        blocker.semanticClass !== "module_bound_callable_dispatch") {
      errors.push(`${blocker.id}: shared module/callable provenance is missing`);
    }
    if (blocker.ownershipClaim !== "materialized_function_value_callable_dispatch" ||
        blocker.sharedWith !== "WP-SEM-MODULE" || blocker.unionKey !== "branchId" ||
        blocker.etsIrOriginId !== branchId || blocker.sourceBindingStatus !== "exact" ||
        blocker.etsIrMappingStatus !== "unmapped" || !blocker.mappingEvidence) {
      errors.push(`${blocker.id}: callable ownership or honest v1 origin mapping is missing`);
    }
    if (!caseIds.has(blocker.witnessCaseId)) errors.push(`${blocker.id}: witness case is missing`);
    if (blocker.frozenOutcome?.status !== "semantic_mismatch" || blocker.frozenOutcome?.reached !== true ||
        blocker.frozenOutcome?.replayConfirmed !== false) {
      errors.push(`${blocker.id}: residual must stay reached-not-replayed semantic_mismatch`);
    }
    for (const label of blocker.capability?.labels ?? []) {
      if (!CAPABILITY_LABELS.has(label)) errors.push(`${blocker.id}: unknown closed capability label '${label}'`);
    }
    for (const tag of blocker.capability?.semanticTags ?? []) {
      if (!SEMANTIC_TAGS.has(tag)) errors.push(`${blocker.id}: unknown semantic tag '${tag}'`);
    }
  }
  if (blockerIds.size !== 11 || branchIds.size !== 11 ||
      [...FROZEN_BRANCH_IDS].some((branchId) => !branchIds.has(branchId))) {
    errors.push("callable residual partition must contain the exact 11 unique arrays.ts branch IDs");
  }
  if (spec.baselineEvidence?.crossReference !== "semantics/module/module-semantics-v1.json" ||
      spec.baselineEvidence?.deduplicationKey !== "branchId") {
    errors.push("module/callable cross-reference or union dedup key drifted");
  }

  validateResidualEvidence(spec, errors, repoRoot);
  return {
    valid: errors.length === 0,
    errors,
    cases: caseIds.size,
    materialized,
    rejected,
    residualBlockers: blockerIds.size,
    dispatchKinds: dispatchKinds.size,
  };
}

function main(args = process.argv.slice(2)) {
  if (args.length !== 1 || args[0] !== "--validate") {
    process.stderr.write("usage: node callable-spec-runner.cjs --validate\n");
    return 64;
  }
  const report = validateSpec(loadSpec());
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  return report.valid ? 0 : 1;
}

if (require.main === module) process.exitCode = main();

module.exports = {
  FROZEN_BRANCH_IDS,
  findRepoRoot,
  loadSpec,
  runCase,
  validateSpec,
};
