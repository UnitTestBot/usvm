import { dirname, resolve } from "node:path";
import { mkdir, readdir, writeFile } from "node:fs/promises";
import {
  ADAPTER_NAME,
  ADAPTER_VERSION,
  DEFAULT_LOG_CAP_BYTES,
  SEARCH_ALGORITHM,
  UPSTREAM_COMMIT,
  UPSTREAM_REPOSITORY,
} from "./constants.mjs";
import {
  commonValidatorBridge,
  loadAdapterInputs,
  readEtcCorpus,
  validateRawRunLocal,
} from "./contract-bridge.mjs";
import { classifyMethods } from "./classify.mjs";
import { extractMethodResult } from "./extract.mjs";
import { buildHarnessPlans } from "./harness.mjs";
import { createRunner } from "./runner.mjs";

export async function runAdapter(paths, options = {}) {
  const now = options.now ?? Date.now;
  const startMs = now();
  const outDir = resolve(paths.outDir);
  await prepareEmptyOutput(outDir);

  const inputs = await loadAdapterInputs(paths);
  const classification = classifyMethods(inputs);
  const harnesses = buildHarnessPlans({ ...inputs, classification });
  const startupMs = elapsed(startMs, now());
  const runner = options.runner ?? createRunner(inputs.runConfig);
  const initialCorpus = await prepareInitialCorpus({
    runConfig: inputs.runConfig,
    runConfigPath: paths.runConfig,
    runner,
  });

  const generationStartMs = now();
  const methodResults = [];
  for (const [methodOrdinal, harness] of harnesses.entries()) {
    let rawResult;
    try {
      rawResult = await runner.runMethod({
        harness,
        runConfig: inputs.runConfig,
        methodOrdinal,
        methodCount: harnesses.length,
        initialCorpus: initialCorpus.cases,
      });
    } catch (error) {
      rawResult = {
        status: "failure",
        started: true,
        cases: [],
        objectives: [],
        stderr: `[${harness.methodId}] runner error: ${error instanceof Error ? error.stack : String(error)}\n`,
      };
    }
    try {
      methodResults.push({
        methodId: harness.methodId,
        ...extractMethodResult({
          methodId: harness.methodId,
          result: rawResult,
          runConfig: inputs.runConfig,
          methodOrdinal,
        }),
      });
    } catch (error) {
      methodResults.push({
        methodId: harness.methodId,
        status: "failure",
        started: rawResult?.started !== false,
        rawCaseCount: Array.isArray(rawResult?.cases) ? rawResult.cases.length : 0,
        cases: [],
        claims: [],
        rejections: [{ methodId: harness.methodId, rawCaseId: "result", reason: error.message }],
        stderr: `${rawResult?.stderr ?? ""}[${harness.methodId}] extraction error: ${error.stack ?? error}\n`,
        diagnostics: {},
      });
    }
  }
  const generationMs = elapsed(generationStartMs, now());

  const collected = collectResults(methodResults);
  const exitStatus = aggregateExitStatus(methodResults);
  const producer = structuredClone(inputs.runConfig.adapter);
  const rawCaseCount = methodResults.reduce((sum, result) => sum + result.rawCaseCount, 0);
  const exportStartMs = now();
  const corpusText = [
    JSON.stringify({ schemaVersion: 2, producer: `${producer.name}@${producer.version}` }),
    ...collected.cases.map((entry) => JSON.stringify(entry)),
    "",
  ].join("\n");

  const logCapBytes = positiveInteger(inputs.runConfig.flags?.syntest?.logCapBytes, DEFAULT_LOG_CAP_BYTES);
  const rawLog = methodResults.map((result) => result.stderr).filter(Boolean).join("");
  const boundedLog = truncateUtf8(rawLog, logCapBytes);
  const exportMs = elapsed(exportStartMs, now());
  const maxGeneratedAt = collected.cases.reduce((maximum, entry) => Math.max(maximum, entry.generatedAtMs), 0);
  const totalMs = Math.max(elapsed(startMs, now()), startupMs + generationMs + exportMs, maxGeneratedAt);
  const denominatorName = classification.selectedMethods === 42 && classification.selectedEdges === 236
    ? "D_primitive-reference-v1"
    : "selected-method-ids";
  const funnel = {
    selectedMethods: classification.selectedMethods,
    selectedEdges: classification.selectedEdges,
    eligibleMethods: classification.eligibleMethods,
    eligibleEdges: classification.eligibleEdges,
    ineligibleMethods: classification.selectedMethods - classification.eligibleMethods,
    harnessedMethods: harnesses.length,
    attemptedMethods: methodResults.length,
    upstreamStartedMethods: methodResults.filter((result) => result.started).length,
    rawCases: rawCaseCount,
    exportedCases: collected.cases.length,
    handedToUnifiedReplayCases: collected.cases.length,
    replayedCases: null,
    confirmedEdges: null,
  };

  const nativeCoverage = {
    schemaVersion: 2,
    producer,
    claims: collected.claims,
    diagnostics: {
      metric: "SynTest native branch/objective coverage",
      algorithm: SEARCH_ALGORITHM,
      coverageTruth: false,
      mayIncreasePaperNumerator: false,
      finalNumeratorSource: "unified Kotlin concrete EtsIR replay only",
      denominator: {
        name: denominatorName,
        selectedMethods: classification.selectedMethods,
        selectedEdges: classification.selectedEdges,
        broadPrimitiveMethods: denominatorName === "D_primitive-reference-v1" ? 42 : null,
        broadPrimitiveEdges: denominatorName === "D_primitive-reference-v1" ? 236 : null,
      },
      eligibleCoverageMustNotBeReportedAsBroadCoverage: true,
      duplicateNativeClaimsDropped: collected.duplicateClaims,
      funnel,
    },
  };

  const runMeta = {
    schemaVersion: 2,
    runId: inputs.runConfig.runId,
    producer,
    startupMs,
    generationMs,
    exportMs,
    totalMs,
    commits: inputs.runConfig.commits,
    exitStatus,
    timedOut: exitStatus === "timeout_partial_corpus",
    logCapBytes,
    logTruncated: boundedLog.truncated,
    overBudgetMs: Math.max(0, totalMs - inputs.runConfig.budgetMs),
    algorithm: SEARCH_ALGORITHM,
    upstream: {
      repository: UPSTREAM_REPOSITORY,
      commit: UPSTREAM_COMMIT,
      license: "Apache-2.0",
      licenseFiles: ["upstream/LICENSE", "upstream/NOTICE"],
    },
    campaignStatus: runner.capabilities?.available === false
      ? "fresh-external-run-deferred-a-bench"
      : "adapter-run-complete-replay-pending",
    initialCorpus: initialCorpus.diagnostics,
    classification: {
      reasonVocabulary: classification.reasonVocabulary,
      methods: classification.classifications,
    },
    funnel,
    caseAccounting: {
      rawCases: rawCaseCount,
      exportedCases: collected.cases.length,
      rejectedCases: collected.rejections.length,
      rejections: collected.rejections,
    },
    replayHandoff: {
      artifact: "corpus.etc.jsonl",
      status: "pending-unified-kotlin-replay",
      rawNativeCoverageIsDiagnosticOnly: true,
    },
  };

  await Promise.all([
    writeFile(`${outDir}/corpus.etc.jsonl`, corpusText, "utf8"),
    writeFile(`${outDir}/native-coverage.json`, `${JSON.stringify(nativeCoverage, null, 2)}\n`, "utf8"),
    writeFile(`${outDir}/run-meta.json`, `${JSON.stringify(runMeta, null, 2)}\n`, "utf8"),
    writeFile(`${outDir}/stderr.log`, boundedLog.text, "utf8"),
  ]);

  const localValidation = await validateRawRunLocal(outDir);
  const validator = options.commonValidator
    ?? commonValidatorBridge(inputs.runConfig.flags?.commonArtifactValidatorCommand);
  const commonValidation = await validator.validateRawRun(outDir);
  return {
    exitStatus,
    outDir,
    funnel,
    localValidation,
    commonValidation,
    classification: classification.classifications,
  };
}

async function prepareEmptyOutput(outDir) {
  await mkdir(outDir, { recursive: true });
  const entries = await readdir(outDir);
  if (entries.length > 0) throw new Error(`output directory must be empty: ${outDir}`);
}

async function prepareInitialCorpus({ runConfig, runConfigPath, runner }) {
  const configuredPath = runConfig.flags?.syntest?.initialCorpusPath;
  const requested = typeof configuredPath === "string" && configuredPath.length > 0;
  const supported = runner.capabilities?.initialCorpus === true;
  if (!requested) {
    return { cases: undefined, diagnostics: { requested: false, supported, used: false, reason: "not-requested" } };
  }
  if (!supported) {
    return {
      cases: undefined,
      diagnostics: {
        requested: true,
        supported: false,
        used: false,
        reason: "upstream-initial-corpus-capability-not-declared",
      },
    };
  }
  const path = resolve(dirname(resolve(runConfigPath)), configuredPath);
  const corpus = await readEtcCorpus(path);
  return {
    cases: corpus.cases,
    diagnostics: {
      requested: true,
      supported: true,
      used: true,
      cases: corpus.cases.length,
      semantics: "upstream-declared initial concrete population; never a mutational-seed claim",
    },
  };
}

function collectResults(methodResults) {
  const cases = [];
  const caseIds = new Set();
  const claims = [];
  const claimIds = new Set();
  const rejections = methodResults.flatMap((result) => result.rejections);
  let duplicateClaims = 0;
  for (const result of methodResults) {
    for (const entry of result.cases) {
      if (caseIds.has(entry.id)) {
        rejections.push({ methodId: entry.methodId, rawCaseId: entry.id, reason: "duplicate canonical case ID" });
      } else {
        caseIds.add(entry.id);
        cases.push(entry);
      }
    }
    for (const claim of result.claims) {
      const identity = `${claim.methodId}\0${claim.nativeTargetId}`;
      if (claimIds.has(identity)) duplicateClaims += 1;
      else {
        claimIds.add(identity);
        claims.push(claim);
      }
    }
  }
  cases.sort((left, right) => left.generatedAtMs - right.generatedAtMs || left.id.localeCompare(right.id));
  claims.sort((left, right) => left.methodId.localeCompare(right.methodId)
    || left.nativeTargetId.localeCompare(right.nativeTargetId));
  return { cases, claims, rejections, duplicateClaims };
}

function aggregateExitStatus(results) {
  if (results.some((result) => result.status === "timeout")) return "timeout_partial_corpus";
  if (results.some((result) => result.status === "failure")) return "tool_failure";
  const unsupported = results.filter((result) => result.status === "unsupported_configuration").length;
  if (unsupported === results.length || results.length === 0) return "unsupported_configuration";
  if (unsupported > 0) return "tool_failure";
  return "success";
}

function truncateUtf8(text, capBytes) {
  const bytes = Buffer.from(text, "utf8");
  if (bytes.length <= capBytes) return { text, truncated: false };
  const marker = Buffer.from("\n[stderr truncated]\n", "utf8");
  const prefixLength = Math.max(0, capBytes - marker.length);
  const combined = Buffer.concat([bytes.subarray(0, prefixLength), marker]).subarray(0, capBytes);
  return { text: combined.toString("utf8"), truncated: true };
}

function elapsed(start, end) {
  return Math.max(0, Math.floor(end - start));
}

function positiveInteger(value, fallback) {
  return Number.isInteger(value) && value > 0 ? value : fallback;
}

export const adapterIdentity = Object.freeze({
  name: ADAPTER_NAME,
  version: ADAPTER_VERSION,
  commit: UPSTREAM_COMMIT,
});
