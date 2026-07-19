#!/usr/bin/env node
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { ADAPTER_NAME, ADAPTER_VERSION, UPSTREAM_COMMIT } from "../src/constants.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const ADAPTER_ROOT = resolve(HERE, "..");
const PBT_ROOT = resolve(ADAPTER_ROOT, "../..");
const BASELINE = join(PBT_ROOT, "benchmarks/baselines/2026-07-19");
const OUTPUT = join(ADAPTER_ROOT, "fixtures");

const denominatorText = await readFile(join(BASELINE, "denominators/D_primitive-reference-v1.methods.tsv"), "utf8");
const selected = denominatorText.trim().split(/\r?\n/).map((line) => {
  const [projectId, methodId] = line.split("\t");
  return { projectId, methodId };
});
if (selected.length !== 42) throw new Error(`expected 42 frozen primitive methods, got ${selected.length}`);

const projects = new Map();
for (const projectId of [...new Set(selected.map((entry) => entry.projectId))]) {
  const projectRoot = join(BASELINE, "projects", projectId);
  projects.set(projectId, {
    manifest: JSON.parse(await readFile(join(projectRoot, "targets.json"), "utf8")),
    callableMap: JSON.parse(await readFile(join(projectRoot, "source-targets.json"), "utf8")),
  });
}

const methods = [];
const sourceTargets = [];
const expected = [];
for (const { projectId, methodId } of selected) {
  const project = projects.get(projectId);
  const method = project.manifest.methods.find((candidate) => candidate.methodId === methodId);
  const callable = project.callableMap.entries.find((candidate) => candidate.methodId === methodId);
  if (!method) throw new Error(`${projectId}: missing method '${methodId}'`);
  if (!callable?.primitiveEligible || !callable.sourceCallable || !callable.exportName) {
    throw new Error(`${projectId}: frozen primitive method '${methodId}' lacks unambiguous callable evidence`);
  }
  const normalized = structuredClone(method);
  normalized.projectName = projectId;
  methods.push(normalized);
  const sourceOrigin = {
    modulePath: callable.sourceFile,
    callableName: callable.exportName,
    callableKind: method.entryKind,
  };
  for (const branch of method.branches) {
    const origin = branch.conditionOrigin ?? branch.successorOrigin;
    if (!origin) throw new Error(`${methodId}: branch '${branch.branchId}' lacks a TypeScript origin`);
    sourceTargets.push({
      schemaVersion: 2,
      methodId,
      branchId: branch.branchId,
      stmtIndex: branch.ifStmtIndex,
      successorStmtIndex: branch.successorStmtIndex,
      successorOrdinal: branch.successorOrdinal,
      tsSourceRange: {
        fileName: origin.fileName,
        startOffset: origin.startOffset,
        endOffset: origin.endOffset,
        startLine: origin.startLine,
        startColumn: origin.startColumn,
        endLine: origin.endLine,
        endColumn: origin.endColumn,
      },
      sourceOrigin,
      mappingStatus: "exact",
      fixtureEvidence: "frozen-v1 primitiveEligible plus branch conditionOrigin",
    });
  }
  expected.push({
    methodId,
    status: "eligible",
    reasons: [],
    branchCount: method.branches.length,
    entryKind: method.entryKind,
    parameterTypes: method.parameterTypes,
    sourceOrigin,
  });
}

const edgeCount = methods.reduce((sum, method) => sum + method.branches.length, 0);
if (edgeCount !== 236 || sourceTargets.length !== 236) {
  throw new Error(`expected 236 frozen primitive edges, got manifest=${edgeCount}, mapping=${sourceTargets.length}`);
}

const artifacts = {
  "primitive-target-manifest.v2.json": `${JSON.stringify({
    schemaVersion: 2,
    generator: "syntest-adapter-fixture-builder@0.1.0",
    methods,
  }, null, 2)}\n`,
  "primitive-source-targets.v2.jsonl": `${sourceTargets.map((entry) => JSON.stringify(entry)).join("\n")}\n`,
  "primitive-method-ids.txt": `${selected.map((entry) => entry.methodId).join("\n")}\n`,
  "primitive-classification.expected.json": `${JSON.stringify({
    schemaVersion: 1,
    denominator: "D_primitive-reference-v1",
    selectedMethods: 42,
    selectedEdges: 236,
    eligibleMethods: 42,
    eligibleEdges: 236,
    methods: expected,
  }, null, 2)}\n`,
  "run-config.v2.json": `${JSON.stringify({
    schemaVersion: 2,
    runId: "syntest-primitive-fixture",
    adapter: {
      name: ADAPTER_NAME,
      version: ADAPTER_VERSION,
      commit: UPSTREAM_COMMIT,
    },
    seed: 20260719,
    budgetMs: 10000,
    exportReplayGraceMs: 1000,
    explorationDeadlineMs: 9000,
    hardResultDeadlineMs: 10000,
    cacheMode: "cold",
    versions: {
      node: ">=18",
      "syntest-javascript": `commit:${UPSTREAM_COMMIT}`,
    },
    commits: {
      usvm: "5a558721",
      "syntest-javascript": UPSTREAM_COMMIT,
    },
    flags: {
      syntest: {
        algorithm: "DynaMOSA",
        upstreamCapabilities: {
          initialCorpus: false,
        },
      },
    },
  }, null, 2)}\n`,
};

artifacts["provenance.json"] = `${JSON.stringify({
  schemaVersion: 1,
  frozenBaseline: "2026-07-19",
  derivation: "Only the 42 pre-frozen primitive IDs are selected. v1 callable evidence is joined with exact branch condition origins; no coverage observation is generated.",
  sourceSha256: {
    "D_primitive-reference-v1.methods.tsv": sha256(denominatorText),
  },
  generatedSha256: Object.fromEntries(Object.entries(artifacts).map(([name, text]) => [name, sha256(text)])),
}, null, 2)}\n`;

await mkdir(OUTPUT, { recursive: true });
await Promise.all(Object.entries(artifacts).map(([name, text]) => writeFile(join(OUTPUT, name), text, "utf8")));
process.stdout.write(`${JSON.stringify({ methods: 42, edges: 236, files: Object.keys(artifacts).length })}\n`);

function sha256(text) {
  return createHash("sha256").update(text).digest("hex");
}
