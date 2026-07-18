export function summarizeReplayMatrix({ entries }) {
  if (!Array.isArray(entries) || entries.length === 0) throw new Error("matrix requires at least one report");

  const tools = [...new Set(entries.map((entry) => entry.tool))];
  const labels = [...new Set(entries.map((entry) => entry.label))];
  const byKey = new Map();
  for (const entry of entries) {
    const key = `${entry.tool}\u0000${entry.label}`;
    if (byKey.has(key)) throw new Error(`duplicate matrix report for ${entry.tool}/${entry.label}`);
    byKey.set(key, normalizeEntry(entry));
  }

  for (const tool of tools) {
    const kinds = new Set(labels.map((label) => byKey.get(`${tool}\u0000${label}`)?.kind).filter(Boolean));
    if (kinds.size !== 1) throw new Error(`${tool} must use one report kind across all methods`);
  }

  const methods = labels.map((label) => {
    const rows = tools.map((tool) => {
      const row = byKey.get(`${tool}\u0000${label}`);
      if (!row) throw new Error(`missing matrix report for ${tool}/${label}`);
      return row;
    });
    const reference = rows[0];
    for (const row of rows.slice(1)) {
      if (row.project !== reference.project) {
        throw new Error(`${label} project mismatch: '${reference.project}' vs '${row.project}'`);
      }
      if (row.method !== reference.method) {
        throw new Error(`${label} method mismatch: '${reference.method}' vs '${row.method}'`);
      }
      if (row.totalBranches !== reference.totalBranches) {
        throw new Error(`${label} branch-total mismatch: ${reference.totalBranches} vs ${row.totalBranches}`);
      }
    }
    return {
      label,
      project: reference.project,
      method: reference.method,
      totalBranches: reference.totalBranches,
      tools: Object.fromEntries(rows.map((row) => [row.tool, row.metrics])),
    };
  });

  const totalBranches = methods.reduce((total, method) => total + method.totalBranches, 0);
  const toolSummaries = Object.fromEntries(tools.map((tool) => {
    const rows = labels.map((label) => byKey.get(`${tool}\u0000${label}`));
    const coveredBranches = rows.reduce((total, row) => total + row.metrics.coveredBranches, 0);
    return [tool, {
      kind: rows[0].kind,
      projects: new Set(rows.map((row) => row.project)).size,
      methods: rows.length,
      totalBranches,
      coveredBranches,
      coveragePct: percent(coveredBranches, totalBranches),
      executions: nullableSum(rows.map((row) => row.metrics.executions)),
      generatedExecutions: nullableSum(rows.map((row) => row.metrics.generatedExecutions)),
      returned: nullableSum(rows.map((row) => row.metrics.returned)),
      threw: nullableSum(rows.map((row) => row.metrics.threw)),
      diverged: nullableSum(rows.map((row) => row.metrics.diverged)),
      unsupported: nullableSum(rows.map((row) => row.metrics.unsupported)),
      externalImported: nullableSum(rows.map((row) => row.metrics.externalImported)),
      externalExecuted: nullableSum(rows.map((row) => row.metrics.externalExecuted)),
      symbolicTargets: nullableSum(rows.map((row) => row.metrics.symbolicTargets)),
      symbolicReached: nullableSum(rows.map((row) => row.metrics.symbolicReached)),
      symbolicReplayConfirmed: nullableSum(rows.map((row) => row.metrics.symbolicReplayConfirmed)),
      wallMs: nullableSum(rows.map((row) => row.metrics.wallMs)),
    }];
  }));

  return {
    schemaVersion: 1,
    metric: "replay-confirmed EtsIR branch-edge coverage",
    timingCaveat: "Tool timers have different process and generation boundaries and are diagnostic only.",
    totals: {
      tools: tools.length,
      projects: new Set(methods.map((method) => method.project)).size,
      methods: methods.length,
      totalBranches,
    },
    tools: toolSummaries,
    methods,
  };
}

function normalizeEntry(entry) {
  if (!entry.tool || !entry.kind || !entry.label || !entry.project) throw new Error("matrix entry metadata is incomplete");
  if (!entry.report || !Array.isArray(entry.report.methods) || entry.report.methods.length !== 1) {
    throw new Error(`${entry.tool}/${entry.label} must contain exactly one method`);
  }
  const method = entry.report.methods[0];
  const pbt = method.pbt ?? null;
  const symbolic = method.symbolic ?? null;
  const generatedExecutions = pbt ? (pbt.generatedExecutions ?? pbt.executions) : null;
  const externalImported = pbt ? (pbt.externalImported ?? 0) : null;
  const externalExecuted = pbt ? (pbt.externalExecuted ?? 0) : null;
  if (entry.kind === "external") {
    if (!pbt || generatedExecutions !== 0) {
      throw new Error(`${entry.tool}/${entry.label} external replay must have generatedExecutions=0`);
    }
  } else if (entry.kind === "internal") {
    if (!pbt || !(generatedExecutions > 0) || externalExecuted !== 0) {
      throw new Error(`${entry.tool}/${entry.label} internal PBT must contain generated executions only`);
    }
  } else if (entry.kind === "symbolic") {
    if (!symbolic || pbt !== null) throw new Error(`${entry.tool}/${entry.label} must be a pure symbolic report`);
  } else {
    throw new Error(`unknown matrix report kind '${entry.kind}'`);
  }

  const targets = symbolic?.targets ?? [];
  return {
    tool: entry.tool,
    kind: entry.kind,
    label: entry.label,
    project: entry.project,
    method: method.method,
    totalBranches: method.totalBranches,
    metrics: {
      coveredBranches: method.coveredBranches,
      coveragePct: percent(method.coveredBranches, method.totalBranches),
      executions: pbt?.executions ?? null,
      generatedExecutions,
      returned: pbt?.returned ?? null,
      threw: pbt?.threw ?? null,
      diverged: pbt?.diverged ?? null,
      unsupported: pbt?.unsupported ?? null,
      externalImported,
      externalExecuted,
      symbolicTargets: symbolic ? targets.length : null,
      symbolicReached: symbolic ? targets.filter((target) => target.reached).length : null,
      symbolicReplayConfirmed: symbolic ? targets.filter((target) => target.replayConfirmed).length : null,
      wallMs: method.totalWallMs ?? null,
    },
  };
}

function percent(covered, total) {
  return total === 0 ? 0 : covered * 100 / total;
}

function nullableSum(values) {
  return values.every((value) => value == null) ? null : values.reduce((sum, value) => sum + (value ?? 0), 0);
}
