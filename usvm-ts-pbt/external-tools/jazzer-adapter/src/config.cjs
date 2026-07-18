"use strict";

const { readFileSync } = require("node:fs");
const { resolve } = require("node:path");

function loadConfiguration() {
  const manifestPath = requiredEnv("USVM_JAZZER_MANIFEST");
  const methodId = requiredEnv("USVM_JAZZER_METHOD_ID");
  const harnessPath = requiredEnv("USVM_JAZZER_HARNESS");
  const manifest = JSON.parse(readFileSync(resolve(manifestPath), "utf8"));
  if (manifest.schemaVersion !== 1) throw new Error(`unsupported target manifest schemaVersion ${manifest.schemaVersion}`);
  const method = manifest.methods.find((candidate) => candidate.methodId === methodId);
  if (!method) throw new Error(`methodId '${methodId}' is absent from ${manifestPath}`);
  const harness = require(resolve(harnessPath));
  const invoke = harness.invoke ?? harness.default ?? harness;
  if (typeof invoke !== "function") throw new Error("harness must export invoke(args) or a function");
  const ignoreExceptions = process.env.USVM_JAZZER_IGNORE_EXCEPTIONS === "1";
  return { manifestPath, methodId, harnessPath, method, harness, invoke, ignoreExceptions };
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

module.exports = { loadConfiguration };
