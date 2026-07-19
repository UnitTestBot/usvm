import fc from "fast-check";
import { arbitraryForMethod } from "./arbitraries.mjs";
import { makeCase } from "./corpus.mjs";

export const DEFAULT_ENTRY_KINDS = Object.freeze(["free", "static"]);

export function selectBatchMethods(manifest, entryKinds = DEFAULT_ENTRY_KINDS) {
  const acceptedKinds = new Set(entryKinds);
  const selected = [];
  const excluded = [];

  for (const method of [...manifest.methods].sort((left, right) => left.methodId.localeCompare(right.methodId))) {
    const reasons = [];
    if (!acceptedKinds.has(method.entryKind)) reasons.push(`entry-kind:${method.entryKind}`);
    if ((method.branches?.length ?? 0) === 0) reasons.push("no-branches");
    if (reasons.length === 0) selected.push(method);
    else excluded.push({ methodId: method.methodId, reasons });
  }

  return { selected, excluded };
}

export function generateBatchCases(methods, { runsPerMethod, seed }) {
  const cases = [];
  for (const method of methods) {
    const methodSeed = stableMethodSeed(seed, method.methodId);
    const samples = fc.sample(arbitraryForMethod(method), {
      seed: methodSeed,
      numRuns: runsPerMethod,
    });
    samples.forEach((args, run) => {
      cases.push(makeCase({
        id: `${method.methodId}:seed-${methodSeed}-run-${run}`,
        methodId: method.methodId,
        args,
        metadata: {
          campaignSeed: seed,
          methodSeed,
          run,
          phase: "batch-sample",
        },
      }));
    });
  }
  return cases;
}

export function summarizeSelection(manifest, selection, options) {
  const branchCount = (methods) => methods.reduce((sum, method) => sum + (method.branches?.length ?? 0), 0);
  const exclusionsByReason = {};
  for (const exclusion of selection.excluded) {
    for (const reason of exclusion.reasons) {
      exclusionsByReason[reason] = (exclusionsByReason[reason] ?? 0) + 1;
    }
  }
  return {
    schemaVersion: 1,
    policy: {
      entryKinds: options.entryKinds,
      requireBranches: true,
      order: "methodId ascending",
      runsPerMethod: options.runsPerMethod,
      seed: options.seed,
    },
    total: {
      methods: manifest.methods.length,
      branches: branchCount(manifest.methods),
    },
    selected: {
      methods: selection.selected.length,
      branches: branchCount(selection.selected),
      methodIds: selection.selected.map((method) => method.methodId),
    },
    excluded: {
      methods: selection.excluded.length,
      byReason: exclusionsByReason,
      entries: selection.excluded,
    },
  };
}

export function stableMethodSeed(campaignSeed, methodId) {
  let hash = 0x811c9dc5;
  for (let index = 0; index < methodId.length; index += 1) {
    hash ^= methodId.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash ^ campaignSeed) | 0;
}
