export function summarizeCampaign({ cases, marginPoints = 2 }) {
  const methods = cases.map(({ label, project, externalReplay, usvm, exposeRaw }) => {
    const external = singleMethod(externalReplay, `${label} external replay`);
    const internal = singleMethod(usvm, `${label} USVM`);
    if (external.method !== internal.method) {
      throw new Error(`${label} method mismatch: '${external.method}' vs '${internal.method}'`);
    }
    if (external.totalBranches !== internal.totalBranches) {
      throw new Error(`${label} branch-total mismatch: ${external.totalBranches} vs ${internal.totalBranches}`);
    }
    if (external.pbt?.generatedExecutions !== 0) {
      throw new Error(`${label} external replay must have generatedExecutions=0`);
    }
    const targets = internal.symbolic?.targets ?? [];
    return {
      label,
      project,
      method: internal.method,
      totalBranches: internal.totalBranches,
      exposeReplayCovered: external.coveredBranches,
      exposePaths: exposeRaw?.done?.length ?? null,
      exposePathErrors: exposeRaw ? exposeRaw.done.filter((path) => (path.errors?.length ?? 0) > 0).length : null,
      exposeElapsedMs: exposeRaw ? Number(exposeRaw.end) - Number(exposeRaw.start) : null,
      usvmReplayCovered: internal.coveredBranches,
      usvmTargets: targets.length,
      usvmReached: targets.filter((target) => target.reached).length,
      usvmReplayConfirmed: targets.filter((target) => target.replayConfirmed).length,
      usvmWallMs: internal.totalWallMs,
    };
  });
  const totalBranches = sum(methods, "totalBranches");
  const exposeCovered = sum(methods, "exposeReplayCovered");
  const usvmCovered = sum(methods, "usvmReplayCovered");
  const exposeCoveragePct = percent(exposeCovered, totalBranches);
  const usvmCoveragePct = percent(usvmCovered, totalBranches);
  const differencePoints = usvmCoveragePct - exposeCoveragePct;
  return {
    schemaVersion: 1,
    metric: "replay-confirmed EtsIR branch-edge coverage",
    timingCaveat: "Engine timers have different process and frontend boundaries and are diagnostic only.",
    nonInferiority: { marginPoints, differencePoints, passed: differencePoints >= -marginPoints },
    totals: {
      projects: new Set(methods.map((method) => method.project)).size,
      methods: methods.length,
      totalBranches,
      exposeCovered,
      exposeCoveragePct,
      exposePaths: nullableSum(methods.map((method) => method.exposePaths)),
      exposePathErrors: nullableSum(methods.map((method) => method.exposePathErrors)),
      exposeElapsedMs: nullableSum(methods.map((method) => method.exposeElapsedMs)),
      usvmCovered,
      usvmCoveragePct,
      usvmTargets: sum(methods, "usvmTargets"),
      usvmReached: sum(methods, "usvmReached"),
      usvmReplayConfirmed: sum(methods, "usvmReplayConfirmed"),
      usvmWallMs: nullableSum(methods.map((method) => method.usvmWallMs)),
    },
    methods,
  };
}

function singleMethod(report, name) {
  if (!report || !Array.isArray(report.methods) || report.methods.length !== 1) {
    throw new Error(`${name} must contain exactly one method`);
  }
  return report.methods[0];
}

function sum(values, key) {
  return values.reduce((total, value) => total + value[key], 0);
}

function percent(covered, total) {
  return total === 0 ? 0 : covered * 100 / total;
}

function nullableSum(values) {
  return values.every((value) => value == null) ? null : values.reduce((sum, value) => sum + (value ?? 0), 0);
}
