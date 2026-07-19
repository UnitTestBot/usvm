#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const STABLE_REASON_CODES = [
  "timeout_no_progress",
  "global_safety_timeout",
  "unreachable_pruned",
  "solver_reached",
  "model_extraction_failed",
  "replay_unsupported",
  "replay_diverged",
  "replay_wrong_edge",
  "confirmed",
];

const REPLAY_FAILURE_REASONS = new Set([
  "replay_unsupported",
  "replay_diverged",
  "replay_wrong_edge",
]);
const TIMESTAMP_FIELDS = [
  "machineStartedAtMs",
  "lastTerminalProgressAtMs",
  "targetReachedAtMs",
  "modelExtractionAtMs",
  "replayFinishedAtMs",
  "terminalAtMs",
];
const STAGE_FIELDS = ["startupFrontendMs", "generationMs", "symbolicMs", "replayMs"];

const usage = `Usage:
  node telemetry-summary.mjs [--report <hybrid-report.json>]... [options]

Options:
  --out <summary.json>          write summary to a file instead of stdout
  --baseline <report.json>     legacy/flag-off wall-time sample (repeatable)
  --instrumented <report.json> telemetry-on wall-time sample (repeatable)
  --overhead-gate-pct <number> median wall-time overhead gate (default: 5)
  --help                       print this help

Positional paths are treated as --report. Baseline and instrumented samples
must be paired by argument order. Without pairs, the output prepares the gate
but deliberately reports no overhead result.`;

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.stack : String(error));
    process.exitCode = 1;
  });
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    console.log(usage);
    return;
  }
  if (options.reports.length === 0 && options.instrumented.length === 0) {
    throw new Error(`at least one --report or --instrumented path is required\n\n${usage}`);
  }
  if (options.baselines.length !== options.instrumented.length) {
    throw new Error("--baseline and --instrumented samples must have equal counts");
  }

  const reportPaths = options.reports.length > 0 ? options.reports : options.instrumented;
  const reports = await readReports(reportPaths);
  const baselines = await readReports(options.baselines);
  const instrumented = await readReports(options.instrumented);
  const result = {
    schemaVersion: 1,
    sources: reportPaths,
    telemetry: summarizeTelemetryReports(reports),
    overhead: summarizeOverhead(baselines, instrumented, options.overheadGatePct),
  };
  const encoded = `${JSON.stringify(result, null, 2)}\n`;
  if (options.out === null) {
    process.stdout.write(encoded);
  } else {
    await mkdir(dirname(options.out), { recursive: true });
    await writeFile(options.out, encoded, "utf8");
    console.log(JSON.stringify({ out: options.out, reports: reports.length }));
  }
}

function parseArgs(args) {
  const options = {
    reports: [],
    baselines: [],
    instrumented: [],
    out: null,
    overheadGatePct: 5,
    help: false,
  };
  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    switch (argument) {
      case "--report":
        options.reports.push(requiredValue(args, ++index, argument));
        break;
      case "--baseline":
        options.baselines.push(requiredValue(args, ++index, argument));
        break;
      case "--instrumented":
        options.instrumented.push(requiredValue(args, ++index, argument));
        break;
      case "--out":
        options.out = resolve(requiredValue(args, ++index, argument));
        break;
      case "--overhead-gate-pct":
        options.overheadGatePct = Number(requiredValue(args, ++index, argument));
        if (!Number.isFinite(options.overheadGatePct) || options.overheadGatePct < 0) {
          throw new Error("--overhead-gate-pct must be a non-negative number");
        }
        break;
      case "--help":
      case "-h":
        options.help = true;
        break;
      default:
        if (argument.startsWith("-")) throw new Error(`unknown option: ${argument}`);
        options.reports.push(resolve(argument));
    }
  }
  options.reports = options.reports.map((path) => resolve(path));
  options.baselines = options.baselines.map((path) => resolve(path));
  options.instrumented = options.instrumented.map((path) => resolve(path));
  return options;
}

function requiredValue(args, index, option) {
  if (index >= args.length) throw new Error(`${option} requires a value`);
  return args[index];
}

async function readReports(paths) {
  return Promise.all(paths.map(async (path) => {
    const report = JSON.parse(await readFile(path, "utf8"));
    if (!Array.isArray(report.methods)) throw new Error(`${path} is not a HybridReport: methods is missing`);
    return { path, report };
  }));
}

export function summarizeTelemetryReports(entries) {
  const reasonCounts = Object.fromEntries(STABLE_REASON_CODES.map((reason) => [reason, 0]));
  const unknownReasons = {};
  const capabilityLabels = {};
  const timestampCounts = Object.fromEntries(TIMESTAMP_FIELDS.map((field) => [field, 0]));
  const stageSamples = Object.fromEntries(STAGE_FIELDS.map((field) => [field, []]));
  const counterSamples = [];
  const shardIds = new Set();
  const targetIdentities = new Set();
  let telemetryReports = 0;
  let completeReports = 0;
  let partialReports = 0;
  let expectedTargets = 0;
  let recordedTargets = 0;
  let missingTargets = 0;
  let extraTargets = 0;
  let terminalTargets = 0;
  let notStartedTargets = 0;
  let terminalOrNotStartedViolations = 0;
  let terminalShapeViolations = 0;
  let targetClosureViolations = 0;
  let duplicateTargetIdentities = 0;
  let replayFailures = 0;
  let firstDivergences = 0;
  let divergenceNotObservable = 0;
  let divergenceObservabilityViolations = 0;
  let stageDurationViolations = 0;

  for (const { path, report } of entries) {
    const telemetry = report.telemetry;
    if (telemetry === undefined || telemetry === null) continue;
    telemetryReports += 1;
    if (telemetry.complete === true) completeReports += 1;
    else partialReports += 1;
    const targets = Array.isArray(telemetry.targets) ? telemetry.targets : [];
    const expected = nonNegativeInteger(telemetry.expectedTargetCount) ? telemetry.expectedTargetCount : targets.length;
    expectedTargets += expected;
    recordedTargets += targets.length;
    missingTargets += Math.max(0, expected - targets.length);
    extraTargets += Math.max(0, targets.length - expected);
    if (targets.length > expected || (telemetry.complete === true && targets.length !== expected)) {
      targetClosureViolations += 1;
    }

    for (const field of STAGE_FIELDS) {
      const duration = telemetry.stageDurations?.[field] ?? 0;
      if (typeof duration === "number" && Number.isFinite(duration) && duration >= 0) {
        stageSamples[field].push(duration);
      } else {
        stageDurationViolations += 1;
      }
    }

    for (const target of targets) {
      const identity = `${path}\u0000${target.methodId}\u0000${target.branchId}`;
      if (targetIdentities.has(identity)) duplicateTargetIdentities += 1;
      targetIdentities.add(identity);
      for (const label of target.capabilityLabels ?? []) increment(capabilityLabels, String(label));

      if (target.status === "not_started") {
        notStartedTargets += 1;
        if ((target.reason !== undefined && target.reason !== null) || target.timestamps != null ||
            target.counters != null || target.divergence != null) {
          terminalOrNotStartedViolations += 1;
        }
      } else if (target.status === "terminal") {
        terminalTargets += 1;
        const reason = target.reason;
        if (STABLE_REASON_CODES.includes(reason)) increment(reasonCounts, reason);
        else increment(unknownReasons, String(reason));
        if (!validTerminalShape(target)) terminalShapeViolations += 1;
        for (const field of TIMESTAMP_FIELDS) {
          if (typeof target.timestamps?.[field] === "number") increment(timestampCounts, field);
        }
        if (validCounters(target.counters)) {
          counterSamples.push(target.counters);
          shardIds.add(target.counters.shardId);
        }
        if (REPLAY_FAILURE_REASONS.has(reason)) {
          replayFailures += 1;
          if (target.divergence?.kind === "first_divergence" &&
              (nonBlank(target.divergence.stmt) || nonBlank(target.divergence.call))) {
            firstDivergences += 1;
          } else if (target.divergence?.kind === "divergence_not_observable") {
            divergenceNotObservable += 1;
          } else {
            divergenceObservabilityViolations += 1;
          }
        } else if (target.divergence !== undefined && target.divergence !== null) {
          divergenceObservabilityViolations += 1;
        }
      } else {
        terminalOrNotStartedViolations += 1;
      }
    }
  }

  const unknownReasonCount = sum(Object.values(unknownReasons));
  const invariantViolations = unknownReasonCount + terminalOrNotStartedViolations + terminalShapeViolations +
    targetClosureViolations + duplicateTargetIdentities + divergenceObservabilityViolations + stageDurationViolations;
  return {
    reports: {
      total: entries.length,
      telemetry: telemetryReports,
      legacyWithoutTelemetry: entries.length - telemetryReports,
      complete: completeReports,
      partial: partialReports,
    },
    targets: {
      expected: expectedTargets,
      recorded: recordedTargets,
      terminal: terminalTargets,
      notStarted: notStartedTargets,
      missing: missingTargets,
      extra: extraTargets,
    },
    reasons: reasonCounts,
    unknownReasons,
    capabilities: sortObject(capabilityLabels),
    timestamps: timestampCounts,
    divergence: {
      replayFailures,
      firstDivergence: firstDivergences,
      divergenceNotObservable,
      missing: divergenceObservabilityViolations,
    },
    counters: summarizeCounters(counterSamples, shardIds),
    stages: summarizeStages(stageSamples),
    invariants: {
      unknownReasonCount,
      terminalOrNotStartedViolations,
      terminalShapeViolations,
      targetClosureViolations,
      duplicateTargetIdentities,
      divergenceObservabilityViolations,
      stageDurationViolations,
      pass: invariantViolations === 0,
    },
  };
}

export function summarizeOverhead(baselineEntries, instrumentedEntries, gatePct = 5) {
  if (baselineEntries.length === 0 && instrumentedEntries.length === 0) {
    return {
      measured: false,
      gatePct,
      pass: null,
      note: "No paired flag-off/telemetry-on samples supplied; no campaign overhead result is inferred.",
    };
  }
  if (baselineEntries.length !== instrumentedEntries.length) {
    throw new Error("baseline and instrumented overhead samples must be paired");
  }
  const pairs = baselineEntries.map((baseline, index) => {
    const instrumented = instrumentedEntries[index];
    const baselineWallMs = reportWallMs(baseline.report);
    const instrumentedWallMs = reportWallMs(instrumented.report);
    return {
      baseline: baseline.path,
      instrumented: instrumented.path,
      baselineWallMs,
      instrumentedWallMs,
      overheadPct: baselineWallMs > 0 ? percent(instrumentedWallMs / baselineWallMs - 1) : null,
    };
  });
  const validPairs = pairs.filter((pair) => pair.overheadPct !== null);
  const baselineMedianWallMs = median(validPairs.map((pair) => pair.baselineWallMs));
  const instrumentedMedianWallMs = median(validPairs.map((pair) => pair.instrumentedWallMs));
  const medianWallOverheadPct = baselineMedianWallMs > 0
    ? percent(instrumentedMedianWallMs / baselineMedianWallMs - 1)
    : null;
  const measured = validPairs.length === pairs.length && pairs.length > 0;
  return {
    measured,
    gatePct,
    samples: pairs.length,
    validSamples: validPairs.length,
    baselineMedianWallMs,
    instrumentedMedianWallMs,
    medianWallOverheadPct,
    pairedMedianOverheadPct: median(validPairs.map((pair) => pair.overheadPct)),
    pass: measured && medianWallOverheadPct !== null && medianWallOverheadPct <= gatePct,
    pairs,
  };
}

function summarizeCounters(samples, shardIds) {
  const solverSamples = samples.map((sample) => sample.solverQueries).filter((value) => typeof value === "number");
  return {
    samples: samples.length,
    activeRoots: range(samples.map((sample) => sample.activeRoots)),
    shardIds: [...shardIds].sort(),
    states: range(samples.map((sample) => sample.states)),
    steps: range(samples.map((sample) => sample.steps)),
    solverQueries: {
      available: solverSamples.length,
      missing: samples.length - solverSamples.length,
      ...range(solverSamples),
    },
  };
}

function summarizeStages(samples) {
  const totalsMs = {};
  const mediansMs = {};
  for (const field of STAGE_FIELDS) {
    totalsMs[field] = sum(samples[field]);
    mediansMs[field] = median(samples[field]);
  }
  return { totalsMs, mediansMs };
}

function validCounters(counters) {
  return counters !== null && typeof counters === "object" &&
    nonNegativeInteger(counters.activeRoots) && nonBlank(counters.shardId) &&
    nonNegativeNumber(counters.states) && nonNegativeNumber(counters.steps) &&
    (counters.solverQueries === undefined || counters.solverQueries === null ||
      nonNegativeNumber(counters.solverQueries));
}

function validTerminalShape(target) {
  if (!STABLE_REASON_CODES.includes(target.reason) || !validCounters(target.counters)) return false;
  const timestamps = target.timestamps;
  if (timestamps === null || typeof timestamps !== "object") return false;
  const machineStart = timestamps.machineStartedAtMs;
  const terminal = timestamps.terminalAtMs;
  if (!nonNegativeNumber(machineStart) || !nonNegativeNumber(terminal) || terminal < machineStart) return false;
  const optional = TIMESTAMP_FIELDS.slice(1, -1).map((field) => timestamps[field]);
  if (optional.some((value) => value != null &&
      (!nonNegativeNumber(value) || value < machineStart || value > terminal))) return false;

  const reach = timestamps.targetReachedAtMs;
  const extraction = timestamps.modelExtractionAtMs;
  const replay = timestamps.replayFinishedAtMs;
  if (reach != null && extraction != null && reach > extraction) return false;
  if (extraction != null && replay != null && extraction > replay) return false;
  switch (target.reason) {
    case "timeout_no_progress":
    case "global_safety_timeout":
    case "unreachable_pruned":
      return reach == null && extraction == null && replay == null;
    case "solver_reached":
      return reach != null && extraction == null && replay == null;
    case "model_extraction_failed":
      return reach != null && extraction != null && replay == null;
    default:
      return reach != null && extraction != null && replay != null;
  }
}

function reportWallMs(report) {
  return sum(report.methods.map((method) => nonNegativeNumber(method.totalWallMs) ? method.totalWallMs : 0));
}

function range(values) {
  return values.length === 0 ? { min: null, max: null } : { min: Math.min(...values), max: Math.max(...values) };
}

function median(values) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((left, right) => left - right);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle];
}

function percent(ratio) {
  return Math.round(ratio * 100_000) / 1_000;
}

function increment(counts, key) {
  counts[key] = (counts[key] ?? 0) + 1;
}

function sum(values) {
  return values.reduce((total, value) => total + value, 0);
}

function nonBlank(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function nonNegativeNumber(value) {
  return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

function nonNegativeInteger(value) {
  return Number.isInteger(value) && value >= 0;
}

function sortObject(object) {
  return Object.fromEntries(Object.entries(object).sort(([left], [right]) => left.localeCompare(right)));
}
