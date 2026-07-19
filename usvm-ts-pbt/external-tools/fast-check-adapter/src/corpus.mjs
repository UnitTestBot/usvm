import { encodeInput, findUnrepresentable } from "./value-codec.mjs";
import { PRODUCER_LABEL, SCHEMA_VERSION } from "./constants.mjs";

export { SCHEMA_VERSION };

export function makeCase({
  id,
  methodId,
  generatedAtMs,
  seed = null,
  path = null,
  receiver = undefined,
  args,
  receiverPlan = null,
  metadata = {},
}) {
  if (typeof id !== "string" || id.length === 0) throw new TypeError("case id must be non-empty");
  if (typeof methodId !== "string" || methodId.length === 0) throw new TypeError("methodId must be non-empty");
  if (!Number.isSafeInteger(generatedAtMs) || generatedAtMs < 0) {
    throw new TypeError("generatedAtMs must be a non-negative integer");
  }
  if ((seed === null || seed === "") && (path === null || path === "")) {
    throw new TypeError("case needs a non-empty seed or path");
  }
  if (!Array.isArray(args)) throw new TypeError("case arguments must be an array");
  const encoded = encodeInput(receiver, args, { receiverPlan });
  const rejection = findUnrepresentable(encoded);
  return {
    testCase: compact({
      id,
      methodId,
      generatedAtMs,
      seed: seed === null ? undefined : String(seed),
      path: path === null || path === "" ? undefined : String(path),
      receiver: encoded.receiver,
      arguments: encoded.arguments,
      metadata: stringifyMetadata(metadata),
    }),
    rejection,
  };
}

export function makeRejectedCase({ id, methodId, generatedAtMs, seed, path = null, reason, phase }) {
  const testCase = {
    id,
    methodId,
    generatedAtMs,
    seed: String(seed),
    receiver: {
      kind: "unrepresentable",
      reason,
      unrepresentableKind: "other",
    },
    arguments: [],
    metadata: stringifyMetadata({ phase, disposition: "rejected", rejection: reason, path }),
  };
  return { testCase, rejection: [{ path: "$receiver", kind: "other", reason }] };
}

export function encodeCorpus(cases, producer = PRODUCER_LABEL) {
  const header = JSON.stringify({ schemaVersion: SCHEMA_VERSION, producer });
  return `${[header, ...cases.map((testCase) => JSON.stringify(testCase))].join("\n")}\n`;
}

export function parseInitialCorpus(text, contract, sourceName = "initial ETC") {
  const trimmed = text.trim();
  if (trimmed.length === 0) throw new Error(`${sourceName} is empty`);
  const completeDocument = tryParseCompleteJson(trimmed);
  if (Array.isArray(completeDocument) || hasOwn(completeDocument, "cases")) {
    throw new Error(
      `${sourceName} is a legacy JSON document; convert it with shared `
      + "artifact-contract convert-v1-etc before invoking the adapter",
    );
  }
  const records = trimmed.split(/\r?\n/u).map((line, index) => {
    try {
      return JSON.parse(line);
    } catch (error) {
      throw new Error(`${sourceName} line ${index + 1} is not valid JSON: ${firstLine(error)}`);
    }
  });
  const [header, ...cases] = records;
  if (header === null || typeof header !== "object" || Array.isArray(header)) {
    throw new Error(`${sourceName} header must be an object`);
  }
  if (header.schemaVersion !== SCHEMA_VERSION) {
    throw new Error(
      `${sourceName} must use schemaVersion ${SCHEMA_VERSION}; convert v1 ETC with shared `
      + "artifact-contract convert-v1-etc before invoking the adapter",
    );
  }
  if (typeof header.producer !== "string" || !contract.producerPattern.test(header.producer)) {
    throw new Error(`${sourceName} producer must use canonical name@version form`);
  }
  cases.forEach((testCase, index) => validateInitialCase(testCase, index + 2, contract, sourceName));
  return { producer: header.producer, cases };
}

export function makeInitialPrefix(initial, selectedMethodIds) {
  const selected = new Set(selectedMethodIds);
  const prefix = [];
  let outsideDenominator = 0;
  for (const [index, testCase] of initial.cases.entries()) {
    if (!selected.has(testCase.methodId)) {
      outsideDenominator += 1;
      continue;
    }
    prefix.push(compact({
      ...testCase,
      id: `initial-${String(index).padStart(6, "0")}-${testCase.id}`,
      generatedAtMs: 0,
      receiver: testCase.receiver ?? { kind: "undefined" },
      metadata: stringifyMetadata({
        ...testCase.metadata,
        origin: "example",
        replayPrefix: true,
        mutationSeed: false,
        sourceProducer: initial.producer,
        sourceCaseId: testCase.id,
      }),
    }));
  }
  return { prefix, outsideDenominator };
}

function validateInitialCase(testCase, line, contract, sourceName) {
  if (testCase === null || typeof testCase !== "object" || Array.isArray(testCase)) {
    throw new Error(`${sourceName} line ${line} must be a case object`);
  }
  for (const field of ["id", "methodId"]) {
    if (typeof testCase[field] !== "string" || testCase[field].length === 0) {
      throw new Error(`${sourceName} line ${line} has invalid ${field}`);
    }
  }
  if (!Number.isSafeInteger(testCase.generatedAtMs) || testCase.generatedAtMs < 0) {
    throw new Error(`${sourceName} line ${line} has invalid generatedAtMs`);
  }
  if (
    (typeof testCase.seed !== "string" || testCase.seed.length === 0)
    && (typeof testCase.path !== "string" || testCase.path.length === 0)
  ) {
    throw new Error(`${sourceName} line ${line} needs seed or path`);
  }
  if (!Array.isArray(testCase.arguments)) throw new Error(`${sourceName} line ${line} has no arguments array`);
  if (testCase.metadata !== undefined) {
    if (testCase.metadata === null || typeof testCase.metadata !== "object" || Array.isArray(testCase.metadata)) {
      throw new Error(`${sourceName} line ${line} metadata must be an object`);
    }
    for (const [key, value] of Object.entries(testCase.metadata)) {
      if (typeof value !== "string") throw new Error(`${sourceName} line ${line} metadata.${key} must be a string`);
    }
  }
  validateKnownKinds(testCase.receiver ?? { kind: "undefined" }, contract, `${sourceName} line ${line} receiver`);
  testCase.arguments.forEach((value, index) =>
    validateKnownKinds(value, contract, `${sourceName} line ${line} arguments[${index}]`));
}

function validateKnownKinds(value, contract, path) {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${path} must be a tagged ETC value`);
  }
  if (!contract.valueKinds.has(value.kind)) throw new Error(`${path} has unknown ETC kind '${value.kind}'`);
  value.elements?.forEach((entry, index) => validateKnownKinds(entry, contract, `${path}.elements[${index}]`));
  value.properties?.forEach((entry, index) =>
    validateKnownKinds(entry.value, contract, `${path}.properties[${index}].value`));
  value.entries?.forEach((entry, index) => {
    validateKnownKinds(entry.key, contract, `${path}.entries[${index}].key`);
    validateKnownKinds(entry.value, contract, `${path}.entries[${index}].value`);
  });
  value.constructorPlan?.arguments?.forEach((entry, index) =>
    validateKnownKinds(entry, contract, `${path}.constructorPlan.arguments[${index}]`));
}

function stringifyMetadata(metadata) {
  return Object.fromEntries(
    Object.entries(metadata)
      .filter(([, value]) => value !== undefined && value !== null)
      .map(([key, value]) => [key, String(value)]),
  );
}

function compact(value) {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined));
}

function firstLine(error) {
  return (error instanceof Error ? error.message : String(error)).split("\n", 1)[0];
}

function tryParseCompleteJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function hasOwn(value, key) {
  return value !== null
    && typeof value === "object"
    && !Array.isArray(value)
    && Object.prototype.hasOwnProperty.call(value, key);
}
