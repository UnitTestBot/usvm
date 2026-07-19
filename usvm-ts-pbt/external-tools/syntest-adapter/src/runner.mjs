import { spawn } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

export function createRunner(runConfig) {
  const syntest = runConfig.flags?.syntest;
  const command = syntest?.command;
  if (!Array.isArray(command) || command.length === 0) return new UnavailableRunner();
  return new ProcessProtocolRunner({
    command,
    perMethodBudgetMs: positiveIntegerOr(syntest.perMethodBudgetMs, null),
    initialCorpus: syntest.upstreamCapabilities?.initialCorpus === true,
  });
}

export class UnavailableRunner {
  capabilities = Object.freeze({ available: false, initialCorpus: false, protocol: "none" });

  async runMethod({ harness }) {
    return {
      status: "unsupported_configuration",
      started: false,
      cases: [],
      objectives: [],
      stderr: `[${harness.methodId}] SynTest runtime/wrapper is not configured; fresh run deferred to A-BENCH.\n`,
      diagnostics: { reason: "syntest-command-not-configured" },
    };
  }
}

export class ProcessProtocolRunner {
  constructor({ command, perMethodBudgetMs, initialCorpus }) {
    if (!Array.isArray(command) || command.length === 0 || command.some((entry) => typeof entry !== "string" || entry.length === 0)) {
      throw new Error("SynTest process command must be a non-empty string array");
    }
    this.command = [...command];
    this.perMethodBudgetMs = perMethodBudgetMs;
    this.capabilities = Object.freeze({ available: true, initialCorpus, protocol: "usvm-syntest-wrapper-v1" });
  }

  async runMethod({ harness, runConfig, methodCount, initialCorpus }) {
    const work = await mkdtemp(join(tmpdir(), "usvm-syntest-wrapper-"));
    const requestPath = join(work, "request.json");
    const resultPath = join(work, "result.json");
    const budgetMs = this.perMethodBudgetMs
      ?? Math.max(1, Math.floor(runConfig.explorationDeadlineMs / Math.max(1, methodCount)));
    const request = {
      protocolVersion: 1,
      algorithm: "DynaMOSA",
      seed: runConfig.seed,
      budgetMs,
      harness,
    };
    if (this.capabilities.initialCorpus && initialCorpus !== undefined) request.initialCorpus = initialCorpus;
    await writeFile(requestPath, `${JSON.stringify(request)}\n`, "utf8");
    try {
      const processResult = await execute(this.command, ["--request", requestPath, "--result", resultPath], budgetMs);
      let checkpoint = null;
      try {
        checkpoint = JSON.parse(await readFile(resultPath, "utf8"));
      } catch (error) {
        if (error?.code !== "ENOENT") {
          return { status: processResult.timedOut ? "timeout" : "failure", cases: [], objectives: [], stderr: `${processResult.stderr}${error.message}\n` };
        }
      }
      const result = checkpoint ?? { cases: [], objectives: [] };
      result.status = processResult.timedOut ? "timeout"
        : processResult.code === 0 ? (result.status ?? "success") : "failure";
      result.started = true;
      result.stderr = `${result.stderr ?? ""}${processResult.stderr}`;
      result.diagnostics = {
        ...(result.diagnostics ?? {}),
        wrapperExitCode: processResult.code === null ? "null" : String(processResult.code),
        wrapperSignal: processResult.signal ?? "none",
        partialCheckpointRead: checkpoint !== null,
      };
      return result;
    } finally {
      await rm(work, { recursive: true, force: true });
    }
  }
}

async function execute(command, suffix, timeoutMs) {
  return await new Promise((resolve, reject) => {
    const child = spawn(command[0], [...command.slice(1), ...suffix], {
      stdio: ["ignore", "ignore", "pipe"],
      shell: false,
    });
    let stderr = "";
    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk) => {
      if (Buffer.byteLength(stderr) < 8 * 1024 * 1024) stderr += chunk;
    });
    child.on("error", reject);
    let timedOut = false;
    const timeout = setTimeout(() => {
      timedOut = true;
      child.kill("SIGTERM");
    }, timeoutMs);
    child.on("close", (code, signal) => {
      clearTimeout(timeout);
      resolve({ code, signal, timedOut, stderr });
    });
  });
}

function positiveIntegerOr(value, fallback) {
  return Number.isInteger(value) && value > 0 ? value : fallback;
}
