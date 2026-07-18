export function summarizeComparison({ externalReplay, usvm, exposeRaw = {}, mappings = {}, marginPoints = 2 }) {
  const externalByName = byMethodName(externalReplay.methods ?? []);
  const usvmByName = byMethodName(usvm.methods ?? []);
  const names = [...new Set([...externalByName.keys(), ...usvmByName.keys()])].sort();
  const methods = names.map((name) => {
    const external = externalByName.get(name);
    const internal = usvmByName.get(name);
    const raw = exposeRaw[name];
    const mapping = mappings[name];
    const targets = internal?.symbolic?.targets ?? [];
    return {
      method: name,
      totalBranches: external?.totalBranches ?? internal?.totalBranches ?? 0,
      exposeReplayCovered: external?.coveredBranches ?? 0,
      exposePaths: raw?.done?.length ?? null,
      exposePathErrors: raw ? raw.done.filter((path) => (path.errors?.length ?? 0) > 0).length : null,
      exposeElapsedMs: raw ? Number(raw.end) - Number(raw.start) : null,
      exposeMappedCredited: mapping?.summary?.creditedCovered ?? null,
      exposeMappingStatuses: mapping?.summary?.statuses ?? null,
      usvmReplayCovered: internal?.coveredBranches ?? 0,
      usvmTargets: targets.length,
      usvmReached: targets.filter((target) => target.reached).length,
      usvmReplayConfirmed: targets.filter((target) => target.replayConfirmed).length,
      usvmWallMs: internal?.totalWallMs ?? null,
    };
  });
  const totalBranches = methods.reduce((sum, method) => sum + method.totalBranches, 0);
  const exposeCovered = methods.reduce((sum, method) => sum + method.exposeReplayCovered, 0);
  const usvmCovered = methods.reduce((sum, method) => sum + method.usvmReplayCovered, 0);
  const exposeCoveragePct = percent(exposeCovered, totalBranches);
  const usvmCoveragePct = percent(usvmCovered, totalBranches);
  const differencePoints = usvmCoveragePct - exposeCoveragePct;
  return {
    schemaVersion: 1,
    metric: "replay-confirmed EtsIR branch-edge coverage",
    timingCaveat: "ExpoSE raw elapsed time and USVM method analysis wall time exclude different startup/frontend costs; coverage is the pilot decision metric.",
    nonInferiority: {
      marginPoints,
      differencePoints,
      passed: differencePoints >= -marginPoints,
    },
    totals: {
      methods: methods.length,
      totalBranches,
      exposeCovered,
      exposeCoveragePct,
      exposePaths: nullableSum(methods.map((method) => method.exposePaths)),
      exposePathErrors: nullableSum(methods.map((method) => method.exposePathErrors)),
      exposeElapsedMs: nullableSum(methods.map((method) => method.exposeElapsedMs)),
      usvmCovered,
      usvmCoveragePct,
      usvmTargets: methods.reduce((sum, method) => sum + method.usvmTargets, 0),
      usvmReached: methods.reduce((sum, method) => sum + method.usvmReached, 0),
      usvmReplayConfirmed: methods.reduce((sum, method) => sum + method.usvmReplayConfirmed, 0),
      usvmWallMs: nullableSum(methods.map((method) => method.usvmWallMs)),
    },
    methods,
  };
}

function byMethodName(methods) {
  const result = new Map();
  for (const method of methods) result.set(methodName(method.method), method);
  return result;
}

function methodName(signature) {
  const match = /::([^:(]+)\(/.exec(String(signature));
  if (!match) throw new Error(`cannot extract method name from '${signature}'`);
  return match[1];
}

function percent(covered, total) {
  return total === 0 ? 0 : covered * 100 / total;
}

function nullableSum(values) {
  return values.every((value) => value == null) ? null : values.reduce((sum, value) => sum + (value ?? 0), 0);
}
