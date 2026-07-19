import { createHash } from "node:crypto";

export function buildHarnessPlans({ manifest, sourceTargets, classification }) {
  const methods = new Map(manifest.methods.map((method) => [method.methodId, method]));
  const records = new Map(sourceTargets.map((record) => [`${record.methodId}\0${record.branchId}`, record]));
  return classification.classifications
    .filter((item) => item.status === "eligible")
    .map((item) => buildHarnessPlan(methods.get(item.methodId), records));
}

export function buildHarnessPlan(method, records) {
  const objectiveRequests = method.branches.map((branch) => {
    const mapping = records.get(`${method.methodId}\0${branch.branchId}`);
    if (!mapping || mapping.mappingStatus !== "exact") {
      throw new Error(`eligible method '${method.methodId}' lost exact mapping for '${branch.branchId}'`);
    }
    return {
      etsIrBranchId: branch.branchId,
      sourceRange: mapping.tsSourceRange,
      sourceOrigin: mapping.sourceOrigin,
      expectedNativeObjectiveKey: nativeObjectiveKey(mapping, branch),
    };
  });
  const origin = objectiveRequests[0]?.sourceOrigin;
  if (!origin) throw new Error(`eligible method '${method.methodId}' has no source origin`);
  const callableSegments = origin.callableName.split(".").filter((segment) => segment.length > 0);
  if (callableSegments.length === 0) throw new Error(`method '${method.methodId}' has an empty callable path`);
  const fileStem = createHash("sha256").update(method.methodId).digest("hex").slice(0, 16);
  return {
    schemaVersion: 1,
    methodId: method.methodId,
    modulePath: origin.modulePath,
    callableName: origin.callableName,
    callableKind: origin.callableKind,
    entryKind: method.entryKind,
    parameterTypes: method.parameterTypes,
    objectiveRequests,
    harnessFileName: `usvm-syntest-${fileStem}.mjs`,
    source: renderHarness({ method, origin, callableSegments, objectiveRequests }),
  };
}

function renderHarness({ method, origin, callableSegments, objectiveRequests }) {
  const metadata = JSON.stringify({
    methodId: method.methodId,
    sourceOrigin: origin,
    objectiveRequests,
  });
  const receiver = method.entryKind === "static" ? "__owner" : "undefined";
  return [
    `import * as __subject from ${JSON.stringify(origin.modulePath)};`,
    `export const __usvmSynTestMetadata = ${metadata};`,
    `const __segments = ${JSON.stringify(callableSegments)};`,
    "let __owner = __subject;",
    "for (const __segment of __segments.slice(0, -1)) __owner = __owner?.[__segment];",
    "const __callable = __owner?.[__segments.at(-1)];",
    `if (typeof __callable !== "function") throw new TypeError(${JSON.stringify(`SynTest harness cannot resolve ${origin.callableName}`)});`,
    `export default (...__args) => Reflect.apply(__callable, ${receiver}, __args);`,
    "",
  ].join("\n");
}

function nativeObjectiveKey(mapping, branch) {
  const range = mapping.tsSourceRange;
  return `${range.fileName}:${range.startOffset}-${range.endOffset}:successor-${branch.successorOrdinal}`;
}
