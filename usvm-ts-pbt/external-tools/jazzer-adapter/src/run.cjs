#!/usr/bin/env node
"use strict";

const { mkdir, readFile, readdir, writeFile } = require("node:fs/promises");
const { spawnSync } = require("node:child_process");
const { dirname, join, resolve } = require("node:path");
const { exportRawCorpus } = require("./corpus.cjs");

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

  await mkdir(options.corpus, { recursive: true });
  await mkdir(options.crashes, { recursive: true });
  // libFuzzer executes an implicit empty input but does not persist it. Keep an
  // explicit minimal seed so every input needed for replay is present on disk.
  if ((await readdir(options.corpus)).length === 0) {
    await writeFile(join(options.corpus, "usvm-initial-seed"), Buffer.from([0]));
  }
  const packageRoot = resolve(__dirname, "..");
  const jazzer = join(packageRoot, "node_modules", ".bin", process.platform === "win32" ? "jazzer.cmd" : "jazzer");
  const target = resolve(__dirname, "fuzz-target.cjs");
  const includes = options.includes.length > 0 ? options.includes : [`${dirname(resolve(options.harness))}/`];
  const jazzerArgs = [target];
  if (options.sync) jazzerArgs.push("--sync");
  includes.forEach((include) => jazzerArgs.push("-i", include));
  jazzerArgs.push(options.corpus, "--", `-max_total_time=${options.seconds}`, `-seed=${options.seed}`,
    `-max_len=${options.maxLength}`, `-artifact_prefix=${resolve(options.crashes)}/`);

  const fuzz = spawnSync(jazzer, jazzerArgs, {
    cwd: options.workdir,
    env: {
      ...process.env,
      USVM_JAZZER_MANIFEST: resolve(options.manifest),
      USVM_JAZZER_METHOD_ID: options.methodId,
      USVM_JAZZER_HARNESS: resolve(options.harness),
      USVM_JAZZER_IGNORE_EXCEPTIONS: options.ignoreExceptions ? "1" : "0",
    },
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });

  const harness = require(resolve(options.harness));
  const exported = await exportRawCorpus({
    method,
    methodId: options.methodId,
    harness,
    directories: [options.corpus, options.crashes],
    out: options.out,
  });
  const summary = {
    producer: "jazzer.js@4.0.0",
    methodId: options.methodId,
    seed: options.seed,
    seconds: options.seconds,
    ignoreExceptions: options.ignoreExceptions,
    jazzerExitCode: fuzz.status,
    signal: fuzz.signal,
    out: options.out,
    ...exported,
  };
  console.log(JSON.stringify(summary));
  if (options.log) {
    await writeFile(options.log, `${fuzz.stdout ?? ""}${fuzz.stderr ?? ""}`, "utf8");
  }
  if (fuzz.error) throw fuzz.error;
  if (fuzz.status !== 0 && fuzz.status !== null) process.exitCode = fuzz.status;
}

function parseArgs(args) {
  const result = {
    manifest: null,
    methodId: null,
    harness: null,
    corpus: null,
    crashes: null,
    out: null,
    log: null,
    workdir: process.cwd(),
    includes: [],
    seconds: 10,
    seed: 0,
    maxLength: 256,
    sync: false,
    ignoreExceptions: false,
  };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--method": result.methodId = args[++index]; break;
      case "--harness": result.harness = args[++index]; break;
      case "--corpus": result.corpus = args[++index]; break;
      case "--crashes": result.crashes = args[++index]; break;
      case "--out": result.out = args[++index]; break;
      case "--log": result.log = args[++index]; break;
      case "--workdir": result.workdir = args[++index]; break;
      case "--instrument": result.includes.push(args[++index]); break;
      case "--seconds": result.seconds = nonNegativeInteger(args[++index], "seconds"); break;
      case "--seed": result.seed = nonNegativeInteger(args[++index], "seed"); break;
      case "--max-length": result.maxLength = nonNegativeInteger(args[++index], "max-length"); break;
      case "--sync": result.sync = true; break;
      case "--ignore-exceptions": result.ignoreExceptions = true; break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.methodId || !result.harness || !result.corpus || !result.crashes || !result.out) {
    throw new Error("required: --manifest --method --harness --corpus --crashes --out");
  }
  return result;
}

function nonNegativeInteger(raw, name) {
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value < 0) throw new Error(`${name} must be a non-negative integer`);
  return value;
}
