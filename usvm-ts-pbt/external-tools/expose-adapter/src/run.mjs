#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { readInitialArguments } from "./etc.mjs";
import { exportExpoSeCorpus } from "./export.mjs";
import { defaultArguments, generateTarget, symbolName } from "./target.mjs";

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const manifest = JSON.parse(await readFile(options.manifest, "utf8"));
  if (manifest.schemaVersion !== 1) throw new Error(`unsupported target manifest schemaVersion ${manifest.schemaVersion}`);
  const method = manifest.methods.find((candidate) => candidate.methodId === options.methodId);
  if (!method) throw new Error(`methodId '${options.methodId}' is absent from ${options.manifest}`);
  const initialArguments = await readInitialArguments(options.initialInputs, options.methodId) ?? defaultArguments(method);

  await mkdir(options.workdir, { recursive: true });
  await mkdir(dirname(resolve(options.out)), { recursive: true });
  const targetPath = resolve(options.workdir, "expose-target.cjs");
  const rawPath = resolve(options.raw ?? join(options.workdir, "expose-raw.json"));
  await writeFile(targetPath, generateTarget({ harnessPath: resolve(options.harness), method, initialArguments }), "utf8");

  const initialInput = Object.fromEntries(initialArguments.map((value, index) => [symbolName(index), value]));
  initialInput._bound = 0;
  const executable = resolve(options.exposeDir, "expoSE");
  const nodeDirectory = dirname(resolve(options.node));
  const existingPath = process.env.PATH ?? "";
  const invocation = spawnSync(executable, [targetPath, JSON.stringify(initialInput)], {
    cwd: resolve(options.exposeDir),
    env: {
      ...process.env,
      PATH: `${nodeDirectory}:${existingPath}`,
      EXPOSE_MAX_TIME: `${options.seconds}s`,
      EXPOSE_TEST_TIMEOUT: `${options.testTimeout}s`,
      EXPOSE_MAX_CONCURRENT: String(options.concurrency),
      EXPOSE_JSON_PATH: rawPath,
      ...options.harnessEnv,
      ...(options.z3Library ? { Z3_PATH: resolve(options.z3Library) } : {}),
    },
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
  if (options.log) await writeFile(options.log, `${invocation.stdout ?? ""}${invocation.stderr ?? ""}`, "utf8");
  if (invocation.error) throw invocation.error;
  await waitForReadableJson(rawPath);
  const producer = `expose@${options.commit}`;
  const exported = await exportExpoSeCorpus({
    rawPath, outPath: options.out, method, methodId: options.methodId,
    harnessPath: options.harness, producer,
  });
  const summary = {
    producer, methodId: options.methodId, seconds: options.seconds,
    exposeExitCode: invocation.status, raw: rawPath, out: options.out, ...exported,
  };
  console.log(JSON.stringify(summary));
  if (invocation.status !== 0 && invocation.status !== null) process.exitCode = invocation.status;
}

async function waitForReadableJson(path) {
  let lastError;
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      JSON.parse(await readFile(path, "utf8"));
      return;
    } catch (error) {
      lastError = error;
      await new Promise((resolveDelay) => setTimeout(resolveDelay, 50));
    }
  }
  throw new Error(`ExpoSE did not produce readable JSON at ${path}: ${lastError?.message ?? "unknown error"}`);
}

function parseArgs(args) {
  const result = {
    exposeDir: null, node: process.execPath, z3Library: null, manifest: null, methodId: null,
    harness: null, workdir: null, raw: null, out: null, log: null, initialInputs: [],
    seconds: 10, testTimeout: 5, concurrency: 1, commit: "unknown",
    harnessEnv: {},
  };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--expose-dir": result.exposeDir = args[++index]; break;
      case "--node": result.node = args[++index]; break;
      case "--z3-library": result.z3Library = args[++index]; break;
      case "--manifest": result.manifest = args[++index]; break;
      case "--method": result.methodId = args[++index]; break;
      case "--harness": result.harness = args[++index]; break;
      case "--workdir": result.workdir = args[++index]; break;
      case "--raw": result.raw = args[++index]; break;
      case "--out": result.out = args[++index]; break;
      case "--log": result.log = args[++index]; break;
      case "--initial-external-inputs": result.initialInputs.push(args[++index]); break;
      case "--seconds": result.seconds = positiveInteger(args[++index], "seconds"); break;
      case "--test-timeout": result.testTimeout = positiveInteger(args[++index], "test-timeout"); break;
      case "--concurrency": result.concurrency = positiveInteger(args[++index], "concurrency"); break;
      case "--commit": result.commit = args[++index]; break;
      case "--harness-env": {
        const [name, ...value] = args[++index].split("=");
        if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name) || value.length === 0) {
          throw new Error("harness-env must be NAME=value");
        }
        result.harnessEnv[name] = value.join("=");
        break;
      }
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.exposeDir || !result.manifest || !result.methodId || !result.harness || !result.workdir || !result.out) {
    throw new Error("required: --expose-dir --manifest --method --harness --workdir --out");
  }
  return result;
}

function positiveInteger(raw, name) {
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}
