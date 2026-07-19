export function selectMethods(manifest, methodIds) {
  const methodsById = new Map(manifest.methods.map((method) => [method.methodId, method]));
  return methodIds.map((methodId) => methodsById.get(methodId));
}

export function stableMethodSeed(campaignSeed, methodId) {
  let hash = 0x811c9dc5;
  for (let index = 0; index < methodId.length; index += 1) {
    hash ^= methodId.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash ^ campaignSeed) | 0;
}

export function summarizeDenominator(methods, sourceTargets) {
  const selected = new Set(methods.map((method) => method.methodId));
  const records = sourceTargets.filter((record) => selected.has(record.methodId));
  const mappingStatuses = {};
  for (const record of records) {
    mappingStatuses[record.mappingStatus] = (mappingStatuses[record.mappingStatus] ?? 0) + 1;
  }
  return {
    methods: methods.length,
    branches: records.length,
    entryKinds: countBy(methods, (method) => method.entryKind),
    mappingStatuses,
  };
}

function countBy(values, key) {
  const result = {};
  for (const value of values) {
    const item = key(value);
    result[item] = (result[item] ?? 0) + 1;
  }
  return result;
}
