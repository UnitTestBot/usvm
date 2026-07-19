import fc from "fast-check";
import { mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { performance } from "node:perf_hooks";
import { arbitraryForMethod } from "./arbitraries.mjs";
import { selectMethods, stableMethodSeed, summarizeDenominator } from "./batch.mjs";
import {
  loadFrozenContract,
  makeInlineRunConfig,
  parseMethodIds,
  parseSourceTargets,
  producerIdentity,
  readJson,
  validateDenominator,
  validateRunConfig,
  validateTargetManifest,
} from "./contract.mjs";
import {
  DEFAULT_LOG_CAP_BYTES,
  PRODUCER_LABEL,
  RAW_ARTIFACT_NAMES,
  SCHEMA_VERSION,
  TOOL_COMMIT,
} from "./constants.mjs";
import {
  encodeCorpus,
  makeCase,
  makeInitialPrefix,
  makeRejectedCase,
  parseInitialCorpus,
} from "./corpus.mjs";
import { materializeInput } from "./value-codec.mjs";

export async function runAdapter(options, dependencies = {}) {
  const now = dependencies.now ?? (() => performance.now());
  const startedAt = now();
  const elapsed = () => Math.max(0, Math.floor(now() - startedAt));
  const outDirectory = resolve(options.outDir);
  await prepareOutputDirectory(outDirectory);

  const log = new BoundedLog(DEFAULT_LOG_CAP_BYTES);
  let config = safeFallbackConfig(options);
  let contract;
  let manifest;
  let sourceTargets = [];
  let methodIds = [];
  let methods = [];
  let harness = {};
  let initial = null;
  let startupMs = 0;
  let generationMs = 0;
  let exitStatus = "success";
  let timedOut = false;
  const cases = [];
  const funnel = createFunnel();
  let denominator = { methods: 0, branches: 0, entryKinds: {}, mappingStatuses: {} };
  let configurationError = null;
  let toolError = null;

  try {
    contract = await loadFrozenContract();
    const inputReads = [
      readFile(options.targetManifest, "utf8"),
      readFile(options.sourceTargets, "utf8"),
      readFile(options.methodIds, "utf8"),
      options.runConfig === null ? Promise.resolve(null) : readFile(options.runConfig, "utf8"),
      options.initialEtc === null ? Promise.resolve(null) : readFile(options.initialEtc, "utf8"),
    ];
    const [manifestText, sourceTargetsText, methodIdsText, runConfigText, initialText] = await Promise.all(inputReads);
    const requestedConfig = runConfigText === null
      ? makeInlineRunConfig(options)
      : readJson(runConfigText, options.runConfig);
    validateRunConfig(requestedConfig, contract);
    validateCliConfigConsistency(options, requestedConfig);
    log.setCap(readLogCap(requestedConfig.flags));
    readRunsPerMethod(requestedConfig.flags);
    config = requestedConfig;

    manifest = validateTargetManifest(readJson(manifestText, options.targetManifest), contract);
    sourceTargets = parseSourceTargets(sourceTargetsText, contract);
    methodIds = parseMethodIds(methodIdsText, manifest);
    validateDenominator(manifest, sourceTargets, methodIds);
    methods = selectMethods(manifest, methodIds);
    denominator = summarizeDenominator(methods, sourceTargets);
    validatePathConfiguration(options, config.flags, methods);
    if (initialText !== null) initial = parseInitialCorpus(initialText, contract, options.initialEtc);
    harness = await loadHarness(options.harness, config.runId);
    validateHarness(harness);
    startupMs = elapsed();
    log.append(`run ${config.runId}: ${methods.length} methods, ${denominator.branches} source targets`);
  } catch (error) {
    configurationError = firstLine(error);
    startupMs = elapsed();
    exitStatus = "unsupported_configuration";
    log.append(`unsupported configuration: ${configurationError}`);
  }

  const generationStartedAt = now();
  if (exitStatus === "success") {
    try {
      let initialByMethod = new Map();
      if (initial !== null) {
        const prefix = makeInitialPrefix(initial, methodIds);
        cases.push(...prefix.prefix);
        funnel.initialReceived = initial.cases.length;
        funnel.initialPrefix = prefix.prefix.length;
        funnel.initialOutsideDenominator = prefix.outsideDenominator;
        initialByMethod = await materializeExamples(prefix.prefix, harness, funnel, log);
      }

      const runsPerMethod = readRunsPerMethod(config.flags);
      for (let index = 0; index < methods.length; index += 1) {
        if (elapsed() >= config.explorationDeadlineMs) {
          timedOut = true;
          break;
        }
        const method = methods[index];
        const result = await runMethod({
          method,
          methodIndex: index,
          campaignSeed: config.seed,
          runs: runsPerMethod,
          path: pathForMethod(options, config.flags, method, methods.length),
          examples: initialByMethod.get(method.methodId) ?? [],
          harness,
          remainingMs: () => config.explorationDeadlineMs - elapsed(),
          elapsed,
          cases,
          funnel,
          log,
        });
        timedOut ||= result.interrupted;
        if (timedOut) break;
      }
      if (typeof harness.getNativeCoverageClaims === "function") {
        funnel.nativeClaims = validateNativeClaims(
          await harness.getNativeCoverageClaims({ methods, sourceTargets, cases: [...cases] }),
        );
      }
    } catch (error) {
      toolError = firstLine(error);
      exitStatus = "tool_failure";
      log.append(`tool failure: ${toolError}`);
    }
  }
  generationMs = Math.max(0, Math.floor(now() - generationStartedAt));
  funnel.exported = cases.length;

  const nativeClaims = Array.isArray(funnel.nativeClaims) ? funnel.nativeClaims : [];
  delete funnel.nativeClaims;
  const producer = config.adapter ?? producerIdentity();
  const producerLabel = `${producer.name}@${producer.version}`;
  const nativeCoverage = {
    schemaVersion: SCHEMA_VERSION,
    producer,
    claims: nativeClaims,
    diagnostics: {
      coverageAuthority: "diagnostic-only; EtsIR replay is authoritative",
      denominator,
      funnel,
      initialCorpusRole: "examples-and-mandatory-replay-prefix",
      initialCorpusIsMutationSeeds: false,
      configurationError,
      toolError,
    },
  };

  const exportStartedAt = now();
  await writeFile(resolve(outDirectory, "corpus.etc.jsonl"), encodeCorpus(cases, producerLabel), "utf8");
  await writeFile(
    resolve(outDirectory, "native-coverage.json"),
    `${JSON.stringify(nativeCoverage, null, 2)}\n`,
    "utf8",
  );
  await writeFile(resolve(outDirectory, "stderr.log"), log.bytes());

  let exportMs = Math.max(0, Math.floor(now() - exportStartedAt));
  let totalMs = Math.max(elapsed(), startupMs + generationMs + exportMs);
  if (exitStatus === "success" && (timedOut || totalMs >= config.hardResultDeadlineMs)) {
    exitStatus = "timeout_partial_corpus";
    timedOut = true;
  }
  let meta = makeRunMeta({
    config,
    producer,
    startupMs,
    generationMs,
    exportMs,
    totalMs,
    exitStatus,
    timedOut,
    log,
    funnel,
  });
  await writeFile(resolve(outDirectory, "run-meta.json"), `${JSON.stringify(meta, null, 2)}\n`, "utf8");

  exportMs = Math.max(exportMs, Math.floor(now() - exportStartedAt));
  totalMs = Math.max(elapsed(), startupMs + generationMs + exportMs);
  if (exitStatus === "success" && totalMs >= config.hardResultDeadlineMs) {
    exitStatus = "timeout_partial_corpus";
    timedOut = true;
  }
  meta = makeRunMeta({
    config,
    producer,
    startupMs,
    generationMs,
    exportMs,
    totalMs,
    exitStatus,
    timedOut,
    log,
    funnel,
  });
  await writeFile(resolve(outDirectory, "run-meta.json"), `${JSON.stringify(meta, null, 2)}\n`, "utf8");

  return {
    exitCode: exitStatus === "success" ? 0 : exitStatus === "timeout_partial_corpus" ? 124 : exitStatus === "unsupported_configuration" ? 64 : 1,
    event: {
      event: "run-complete",
      schemaVersion: SCHEMA_VERSION,
      runId: config.runId,
      producer: producerLabel,
      exitStatus,
      cases: cases.length,
      outDir: outDirectory,
    },
    meta,
    nativeCoverage,
  };
}

async function runMethod({
  method,
  methodIndex,
  campaignSeed,
  runs,
  path,
  examples,
  harness,
  remainingMs,
  elapsed,
  cases,
  funnel,
  log,
}) {
  const methodSeed = stableMethodSeed(campaignSeed, method.methodId);
  let serial = 0;
  let exampleCursor = 0;
  let failureSeen = false;
  let failureOrigin = null;
  let failingExample = null;
  let failure = "property returned false";

  const property = fc.asyncProperty(arbitraryForMethod(method), async (args) => {
    const isExample = !failureSeen && exampleCursor < examples.length;
    const phase = failureSeen ? "shrink-attempt" : isExample ? "example" : "generated";
    let input;
    let recorded = null;
    if (isExample) {
      const example = examples[exampleCursor++];
      input = example.input;
    } else {
      const mapped = failureOrigin === "example" && failingExample !== null
        ? { input: { receiver: failingExample.input.receiver, arguments: args }, error: null }
        : await mapGeneratedInput(args, method, harness);
      input = mapped.input;
      const id = `m${String(methodIndex).padStart(4, "0")}-${phase}-${String(serial++).padStart(6, "0")}`;
      recorded = mapped.error === null
        ? encodeCandidate({
          id,
          methodId: method.methodId,
          generatedAtMs: Math.max(1, elapsed()),
          seed: methodSeed,
          receiver: input.receiver,
          args: input.arguments,
          receiverPlan: input.receiverPlan,
          metadata: {
            phase,
            campaignSeed,
            methodSeed,
            fastCheckPath: path,
          },
        })
        : makeRejectedCase({
          id,
          methodId: method.methodId,
          generatedAtMs: Math.max(1, elapsed()),
          seed: methodSeed,
          path,
          reason: mapped.error,
          phase,
        });
      if (recorded.rejection.length > 0) {
        recorded.testCase.metadata.disposition = "rejected";
        recorded.testCase.metadata.rejectionCount = String(recorded.rejection.length);
        recorded.testCase.metadata.rejectionKinds = [...new Set(recorded.rejection.map((entry) => entry.kind))].join(",");
        funnel.rejected += 1;
      } else {
        recorded.testCase.metadata.disposition = "exported";
      }
      cases.push(recorded.testCase);
      if (phase === "generated") funnel.generated += 1;
      else funnel.shrinkAttempts += 1;
    }

    if (recorded?.rejection.length > 0 || typeof harness.invoke !== "function") return true;
    funnel.executed += 1;
    try {
      const verdict = await harness.invoke(
        { receiver: input.receiver, arguments: input.arguments },
        { method, phase },
      );
      if (verdict !== false) return true;
      failure = "property returned false";
    } catch (error) {
      failure = firstLine(error);
    }
    if (!failureSeen) {
      failureOrigin = isExample ? "example" : "generated";
      if (isExample) failingExample = examples[exampleCursor - 1];
      funnel.failures += 1;
    }
    failureSeen = true;
    return false;
  });

  const checkParameters = {
    seed: methodSeed,
    // fast-check counts explicit examples in numRuns. Add them here so the
    // requested random-run denominator stays unchanged by a replay prefix.
    numRuns: runs + examples.length,
    examples: examples.map((example) => [example.input.arguments]),
    interruptAfterTimeLimit: Math.max(1, remainingMs()),
    markInterruptAsFailure: false,
  };
  if (path !== null) checkParameters.path = path;
  const details = await fc.check(property, checkParameters);
  const interrupted = details.interrupted === true || remainingMs() <= 0;
  if (details.failed && Array.isArray(details.counterexample?.[0])) {
    const args = details.counterexample[0];
    const mapped = failureOrigin === "example" && failingExample !== null
      ? { input: { receiver: failingExample.input.receiver, arguments: args }, error: null }
      : await mapGeneratedInput(args, method, harness);
    const id = `m${String(methodIndex).padStart(4, "0")}-counterexample-${String(serial++).padStart(6, "0")}`;
    const counterexamplePath = details.counterexamplePath ?? "";
    const recorded = mapped.error === null
      ? encodeCandidate({
        id,
        methodId: method.methodId,
        generatedAtMs: Math.max(1, elapsed()),
        seed: methodSeed,
        path: counterexamplePath,
        receiver: mapped.input.receiver,
        args: mapped.input.arguments,
        receiverPlan: mapped.input.receiverPlan,
        metadata: {
          phase: "counterexample",
          disposition: "exported",
          campaignSeed,
          methodSeed,
          fastCheckPath: counterexamplePath,
          failure: failure.slice(0, 500),
          reportedRuns: details.numRuns,
          reportedShrinks: details.numShrinks,
        },
      })
      : makeRejectedCase({
        id,
        methodId: method.methodId,
        generatedAtMs: Math.max(1, elapsed()),
        seed: methodSeed,
        path: counterexamplePath,
        reason: mapped.error,
        phase: "counterexample",
      });
    if (recorded.rejection.length > 0) {
      recorded.testCase.metadata.disposition = "rejected";
      recorded.testCase.metadata.rejectionCount = String(recorded.rejection.length);
      recorded.testCase.metadata.rejectionKinds = [...new Set(recorded.rejection.map((entry) => entry.kind))].join(",");
      funnel.rejected += 1;
    }
    cases.push(recorded.testCase);
    funnel.counterexamples += 1;
    funnel.reportedShrinks += details.numShrinks;
    log.append(
      `${method.methodId}: counterexample seed=${methodSeed} path=${JSON.stringify(counterexamplePath)} `
      + `shrinks=${details.numShrinks}`,
    );
  }
  return { interrupted };
}

async function mapGeneratedInput(args, method, harness) {
  if (typeof harness.toCorpusCase === "function") {
    try {
      const mapped = await harness.toCorpusCase(args, { method });
      if (mapped === null || typeof mapped !== "object" || !Array.isArray(mapped.arguments)) {
        throw new TypeError("toCorpusCase must return { receiver?, arguments: [...], receiverPlan? }");
      }
      return {
        input: {
          receiver: mapped.receiver,
          arguments: mapped.arguments,
          receiverPlan: mapped.receiverPlan ?? null,
        },
        error: null,
      };
    } catch (error) {
      return {
        input: { receiver: undefined, arguments: args },
        error: `toCorpusCase rejected candidate: ${firstLine(error)}`,
      };
    }
  }
  if (method.entryKind === "instance") {
    return {
      input: { receiver: undefined, arguments: args },
      error: "instance method requires a concrete receiver or receiverPlan from harness.toCorpusCase",
    };
  }
  return { input: { receiver: undefined, arguments: args }, error: null };
}

async function materializeExamples(prefix, harness, funnel, log) {
  const byMethod = new Map();
  for (const testCase of prefix) {
    try {
      const input = await materializeInput(testCase, harness);
      const examples = byMethod.get(testCase.methodId) ?? [];
      examples.push({ testCase, input });
      byMethod.set(testCase.methodId, examples);
      funnel.initialMaterialized += 1;
    } catch (error) {
      funnel.initialRejected += 1;
      log.append(`initial example ${testCase.id} not materialized: ${firstLine(error)}`);
    }
  }
  return byMethod;
}

function validateNativeClaims(value) {
  if (!Array.isArray(value)) throw new TypeError("getNativeCoverageClaims must return an array");
  const identities = new Set();
  return value.map((claim, index) => {
    if (claim === null || typeof claim !== "object") throw new TypeError(`native claim ${index} is not an object`);
    if (typeof claim.methodId !== "string" || claim.methodId.length === 0) {
      throw new TypeError(`native claim ${index} has no methodId`);
    }
    if (typeof claim.nativeTargetId !== "string" || claim.nativeTargetId.length === 0) {
      throw new TypeError(`native claim ${index} has no nativeTargetId`);
    }
    if (typeof claim.claimedCovered !== "boolean") throw new TypeError(`native claim ${index} has no boolean verdict`);
    const identity = `${claim.methodId}\u0000${claim.nativeTargetId}`;
    if (identities.has(identity)) throw new TypeError(`duplicate native claim ${claim.nativeTargetId}`);
    identities.add(identity);
    if (
      claim.discoveredAtMs !== undefined
      && (!Number.isSafeInteger(claim.discoveredAtMs) || claim.discoveredAtMs < 0)
    ) {
      throw new TypeError(`native claim ${index} has invalid discoveredAtMs`);
    }
    return {
      methodId: claim.methodId,
      nativeTargetId: claim.nativeTargetId,
      claimedCovered: claim.claimedCovered,
      ...(claim.discoveredAtMs === undefined ? {} : { discoveredAtMs: claim.discoveredAtMs }),
    };
  });
}

async function loadHarness(path, runId) {
  if (path === null) return {};
  const url = pathToFileURL(resolve(path));
  url.searchParams.set("fastCheckRun", runId);
  const module = await import(url.href);
  return {
    ...module,
    invoke: module.invoke ?? (typeof module.default === "function" ? module.default : undefined),
  };
}

function validateHarness(harness) {
  for (const name of ["invoke", "toCorpusCase", "resolveCallable", "construct", "getNativeCoverageClaims"]) {
    if (harness[name] !== undefined && typeof harness[name] !== "function") {
      throw new TypeError(`harness.${name} must be a function`);
    }
  }
}

function validateCliConfigConsistency(options, config) {
  const comparisons = [
    ["seed", options.seed, config.seed],
    ["budget-ms", options.budgetMs, config.budgetMs],
    ["export-replay-grace-ms", options.exportReplayGraceMs, config.exportReplayGraceMs],
  ];
  for (const [name, cli, configured] of comparisons) {
    if (cli !== null && cli !== configured) throw new Error(`--${name} differs from run-config (${cli} != ${configured})`);
  }
  if (options.runId !== null && options.runId !== config.runId) {
    throw new Error(`--run-id differs from run-config (${options.runId} != ${config.runId})`);
  }
  if (options.cacheMode !== "cold" && options.cacheMode !== config.cacheMode) {
    throw new Error(`--cache-mode differs from run-config (${options.cacheMode} != ${config.cacheMode})`);
  }
}

function validatePathConfiguration(options, flags, methods) {
  if (options.path !== null && methods.length !== 1) {
    throw new Error("--path requires exactly one selected method");
  }
  const configured = flags.fastCheckPathsByMethod ?? flags.pathsByMethod;
  if (configured === undefined) return;
  if (configured === null || typeof configured !== "object" || Array.isArray(configured)) {
    throw new Error("pathsByMethod flag must be an object");
  }
  const selected = new Set(methods.map((method) => method.methodId));
  for (const [methodId, value] of Object.entries(configured)) {
    if (!selected.has(methodId)) throw new Error(`path configured for unselected method '${methodId}'`);
    if (typeof value !== "string") throw new Error(`path for '${methodId}' must be a string`);
  }
}

function pathForMethod(options, flags, method, selectedMethodCount) {
  if (options.path !== null) {
    if (selectedMethodCount !== 1) throw new Error("--path requires exactly one selected method");
    return options.path;
  }
  const value = flags.fastCheckPathsByMethod?.[method.methodId] ?? flags.pathsByMethod?.[method.methodId];
  if (value === undefined || value === null) return null;
  if (typeof value !== "string") throw new Error(`path for '${method.methodId}' must be a string`);
  return value;
}

function readRunsPerMethod(flags) {
  const value = flags.fastCheckRunsPerMethod ?? flags.runsPerMethod ?? 1_000;
  if (!Number.isSafeInteger(value) || value < 0) throw new Error("runsPerMethod flag must be non-negative integer");
  return value;
}

function readLogCap(flags) {
  const value = flags.logCapBytes ?? DEFAULT_LOG_CAP_BYTES;
  if (!Number.isSafeInteger(value) || value < 1) throw new Error("logCapBytes flag must be a positive integer");
  return value;
}

function safeFallbackConfig(options) {
  const requestedBudget = Number.isSafeInteger(options.budgetMs) && options.budgetMs > 1_000
    ? options.budgetMs
    : 10_000;
  const grace = Math.min(5_000, Math.max(1_000, Math.floor(requestedBudget / 10)));
  return {
    schemaVersion: SCHEMA_VERSION,
    runId: options.runId ?? "fast-check-invalid-configuration",
    adapter: producerIdentity(),
    seed: Number.isSafeInteger(options.seed) && options.seed >= 0 ? options.seed : 0,
    budgetMs: requestedBudget,
    exportReplayGraceMs: grace,
    explorationDeadlineMs: requestedBudget - grace,
    hardResultDeadlineMs: requestedBudget,
    cacheMode: "cold",
    versions: { node: process.versions.node, fastCheck: PRODUCER_LABEL.split("@").at(-1) },
    commits: { fastCheck: TOOL_COMMIT },
    flags: {},
  };
}

function makeRunMeta({ config, producer, startupMs, generationMs, exportMs, totalMs, exitStatus, timedOut, log, funnel }) {
  return {
    schemaVersion: SCHEMA_VERSION,
    runId: config.runId,
    producer,
    startupMs,
    generationMs,
    exportMs,
    totalMs,
    commits: config.commits,
    exitStatus,
    timedOut,
    logCapBytes: log.cap,
    logTruncated: log.truncated,
    overBudgetMs: Math.max(0, totalMs - config.hardResultDeadlineMs),
    timingSemantics: "monotonic milliseconds from process-local run start",
    funnel,
  };
}

function createFunnel() {
  return {
    initialReceived: 0,
    initialPrefix: 0,
    initialOutsideDenominator: 0,
    initialMaterialized: 0,
    initialRejected: 0,
    generated: 0,
    rejected: 0,
    executed: 0,
    failures: 0,
    shrinkAttempts: 0,
    reportedShrinks: 0,
    counterexamples: 0,
    exported: 0,
  };
}

class BoundedLog {
  constructor(cap) {
    this.cap = cap;
    this.chunks = [];
    this.size = 0;
    this.truncated = false;
  }

  setCap(cap) {
    this.cap = cap;
    if (this.size > cap) {
      const bytes = this.bytes().subarray(0, cap);
      this.chunks = [bytes];
      this.size = bytes.length;
      this.truncated = true;
    }
  }

  append(message) {
    if (this.size >= this.cap) {
      this.truncated = true;
      return;
    }
    const bytes = Buffer.from(`${message}\n`, "utf8");
    const accepted = bytes.subarray(0, this.cap - this.size);
    this.chunks.push(accepted);
    this.size += accepted.length;
    if (accepted.length !== bytes.length) this.truncated = true;
  }

  bytes() {
    return Buffer.concat(this.chunks, this.size);
  }
}

async function prepareOutputDirectory(path) {
  await mkdir(path, { recursive: true });
  const entries = await readdir(path);
  if (entries.length > 0) {
    throw new Error(`--out-dir must be empty; found ${entries.sort().join(", ")}`);
  }
  if (RAW_ARTIFACT_NAMES.length !== 4) throw new Error("raw artifact list invariant failed");
}

function firstLine(error) {
  return (error instanceof Error ? `${error.name}: ${error.message}` : String(error)).split("\n", 1)[0].slice(0, 500);
}

function encodeCandidate(options) {
  try {
    return makeCase(options);
  } catch (error) {
    return makeRejectedCase({
      id: options.id,
      methodId: options.methodId,
      generatedAtMs: options.generatedAtMs,
      seed: options.seed,
      path: options.path,
      reason: `ETC encoding rejected candidate: ${firstLine(error)}`,
      phase: options.metadata.phase,
    });
  }
}
