"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const {
  SourceCallableId,
  SourceMapIndex,
  mapManifestEdges,
  rangeFromOffsets,
} = require("./source-mapping.cjs");

const BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

test("stable source callable IDs use source bindings rather than frontend anonymous names", () => {
  assert.equal(
    SourceCallableId.create({
      modulePath: "./src\\math.ts",
      callableKind: "arrow",
      qualifiedName: "classify",
      arity: 1,
    }),
    "ts:src/math.ts::arrow:classify/1",
  );
});

test("golden if else loop ternary short-circuit and optional-chain decisions map exactly", () => {
  const source = [
    "if (x > 0) yes(); else no();",
    "while (i < n) i++;",
    "const sign = x > 0 ? 1 : -1;",
    "const both = left && right;",
    "const member = value?.member;",
  ].join("\n");
  const needles = ["x > 0", "i < n", "x > 0 ? 1 : -1", "left && right", "value?.member"];
  const ranges = occurrences(source, needles).map(([start, end]) => rangeFromOffsets("src/golden.ts", source, start, end));
  const sourceMap = identitySourceMap(source, ranges);
  const sourceMapIndex = new SourceMapIndex({
    sourceMap,
    sourceText: source,
    generatedText: source,
    sourceFile: "src/golden.ts",
    generatedFile: "dist/golden.js",
  });
  const method = {
    methodId: "frontend-generated-name-is-irrelevant",
    fileName: "src/golden.ts",
    className: "%dflt",
    methodName: "%AM7$%dflt",
    arity: 1,
    entryKind: "free",
    branches: ranges.flatMap((range, decision) => [0, 1].map((successorOrdinal) => ({
      branchId: `golden#${decision}:${successorOrdinal}`,
      ifStmtIndex: decision * 3,
      successorStmtIndex: decision * 3 + successorOrdinal + 1,
      successorOrdinal,
      conditionOrigin: { ...range, nodeKind: decision === 4 ? "PropertyAccessExpression" : "fixture" },
      successorOrigin: { ...range, nodeKind: "fixture" },
    }))),
  };
  const manifest = { schemaVersion: 2, methods: [method] };
  const contexts = new Map([[method.methodId, exactContext(sourceMapIndex, ranges[0])]]);

  const mapping = mapManifestEdges(manifest, contexts);

  assert.equal(mapping.records.length, 10);
  assert.deepEqual(mapping.report.mappingStatus, {
    exact: 10, oneToMany: 0, ambiguous: 0, unmapped: 0, synthetic: 0,
  });
  assert.equal(mapping.report.silentDrops, 0);
  assert.equal(new Set(mapping.records.map((record) => record.sourceOrigin.sourceCallableId)).size, 1);
  assert.ok(mapping.records.every((record) => record.emittedJsRange?.fileName === "dist/golden.js"));
  assert.deepEqual(mapping.records.map((record) => record.successorOrdinal), [0, 1, 0, 1, 0, 1, 0, 1, 0, 1]);
});

test("a source-map split and repeated EtsIR decision are explicit oneToMany", () => {
  const source = "const member = value?.member;";
  const generated = "const member = value == null ? void 0 : value.member;";
  const targetStart = source.indexOf("value");
  const targetRange = rangeFromOffsets("src/optional.ts", source, targetStart, source.indexOf(";"));
  const sourceMap = mapDocument("optional.js", "optional.ts", [
    point(generated, source, 0, 0),
    point(generated, source, generated.indexOf("value"), targetStart),
    point(generated, source, generated.indexOf("=="), 0),
    point(generated, source, generated.lastIndexOf("value"), targetStart + 1),
  ]);
  const index = new SourceMapIndex({
    sourceMap,
    sourceText: source,
    generatedText: generated,
    sourceFile: "src/optional.ts",
    generatedFile: "dist/optional.js",
  });
  assert.equal(index.resolve(targetRange).status, "oneToMany");

  const branches = [1, 2].flatMap((stmtIndex) => [0, 1].map((successorOrdinal) => ({
    branchId: `optional#${stmtIndex}:${successorOrdinal}`,
    ifStmtIndex: stmtIndex,
    successorStmtIndex: stmtIndex * 10 + successorOrdinal,
    successorOrdinal,
    conditionOrigin: { ...targetRange, nodeKind: "PropertyAccessExpression" },
  })));
  const method = {
    methodId: "optional/1", fileName: "src/optional.ts", className: "%dflt",
    methodName: "optional", arity: 1, entryKind: "free", branches,
  };
  const mapping = mapManifestEdges(
    { schemaVersion: 2, methods: [method] },
    new Map([[method.methodId, exactContext(index, targetRange, "optional")]]),
  );
  assert.equal(mapping.report.mappingStatus.oneToMany, 4);
  assert.equal(mapping.report.silentDrops, 0);
  assert.ok(mapping.records.every((record) => record.emittedJsRanges.length === 2));
});

test("ambiguous unmapped and synthetic edges are reported without silent drops", () => {
  const range = {
    fileName: "src/failures.ts", startOffset: 1, endOffset: 2,
    startLine: 0, startColumn: 1, endLine: 0, endColumn: 2, nodeKind: "Identifier",
  };
  const edge = (id, withOrigin = true) => ({
    branchId: id, ifStmtIndex: Number(id.at(-1)), successorStmtIndex: 10 + Number(id.at(-1)), successorOrdinal: 0,
    conditionOrigin: withOrigin ? range : null,
  });
  const methods = [
    { methodId: "ambiguous", fileName: "src/failures.ts", methodName: "a", arity: 0, entryKind: "free", branches: [edge("a1")] },
    { methodId: "unmapped", fileName: "src/failures.ts", methodName: "u", arity: 0, entryKind: "free", branches: [edge("u2")] },
    { methodId: "synthetic", fileName: "src/failures.ts", methodName: "s", arity: 0, entryKind: "free", branches: [edge("s3", false)] },
  ];
  const mapping = mapManifestEdges({ schemaVersion: 2, methods }, new Map([
    ["ambiguous", { modulePath: "src/failures.ts", sourceResolution: "exact", bindingResolution: "ambiguous", candidates: [{ qualifiedName: "a" }, { qualifiedName: "b" }] }],
    ["unmapped", { modulePath: "src/failures.ts", sourceResolution: "unmapped", bindingResolution: "unmapped" }],
    ["synthetic", { modulePath: "src/failures.ts", sourceResolution: "exact", bindingResolution: "exact" }],
  ]));

  assert.deepEqual(mapping.report.mappingStatus, {
    exact: 0, oneToMany: 0, ambiguous: 1, unmapped: 1, synthetic: 1,
  });
  assert.equal(mapping.report.expectedEdges, 3);
  assert.equal(mapping.report.writtenEdges, 3);
  assert.equal(mapping.report.silentDrops, 0);
  assert.equal(mapping.report.ambiguities.length, 1);
  assert.equal(mapping.report.ambiguities[0].candidates.length, 2);
});

function exactContext(sourceMapIndex, declarationRange, name = "golden") {
  const modulePath = sourceMapIndex.sourceFile;
  return {
    modulePath,
    sourceResolution: "exact",
    bindingResolution: "exact",
    sourceMapIndex,
    binding: {
      callableKind: "free",
      localName: name,
      qualifiedName: name,
      runtimeName: name,
      exportName: name,
      declarationRange,
      bindingRange: declarationRange,
      sourceCallableId: SourceCallableId.create({ modulePath, callableKind: "free", qualifiedName: name, arity: 1 }),
    },
    moduleOrigin: { moduleId: `${modulePath}::%module`, modulePath, sourceRange: declarationRange },
    importOrigins: [{ importingModulePath: modulePath, sourceModule: "./dependency", importKind: "named", importedName: "x", localName: "x", sourceRange: declarationRange }],
    fileInitOrigin: { fileInitId: `${modulePath}::%file-init`, modulePath, sourceRange: declarationRange },
  };
}

function occurrences(text, needles) {
  const from = new Map();
  return needles.map((needle) => {
    const start = text.indexOf(needle, from.get(needle) ?? 0);
    assert.notEqual(start, -1, `fixture substring '${needle}'`);
    from.set(needle, start + needle.length);
    return [start, start + needle.length];
  });
}

function identitySourceMap(text, ranges) {
  const points = [{ generatedOffset: 0, originalOffset: 0 }];
  for (const range of ranges) {
    points.push({ generatedOffset: range.startOffset, originalOffset: range.startOffset });
    points.push({ generatedOffset: Math.max(range.startOffset, range.endOffset - 1), originalOffset: Math.max(range.startOffset, range.endOffset - 1) });
  }
  return mapDocument("golden.js", "golden.ts", points.map(({ generatedOffset, originalOffset }) =>
    point(text, text, generatedOffset, originalOffset)));
}

function point(generated, source, generatedOffset, originalOffset) {
  return {
    generated: position(generated, generatedOffset),
    original: position(source, originalOffset),
  };
}

function mapDocument(file, source, points) {
  const sorted = [...points].sort((left, right) =>
    left.generated.line - right.generated.line || left.generated.column - right.generated.column);
  let previousSource = 0, previousOriginalLine = 0, previousOriginalColumn = 0;
  const lines = [];
  for (const item of sorted) {
    while (lines.length <= item.generated.line) lines.push([]);
    const line = lines[item.generated.line];
    const previousGeneratedColumn = line.length === 0 ? 0 : line.at(-1).generatedColumn;
    line.push({
      generatedColumn: item.generated.column,
      encoded: [
        item.generated.column - previousGeneratedColumn,
        0 - previousSource,
        item.original.line - previousOriginalLine,
        item.original.column - previousOriginalColumn,
      ],
    });
    previousSource = 0;
    previousOriginalLine = item.original.line;
    previousOriginalColumn = item.original.column;
  }
  return {
    version: 3,
    file,
    sourceRoot: "",
    sources: [source],
    names: [],
    mappings: lines.map((line) => line.map(({ encoded }) => encoded.map(encodeVlq).join("")).join(",")).join(";"),
  };
}

function encodeVlq(raw) {
  let value = raw < 0 ? ((-raw) << 1) | 1 : raw << 1;
  let result = "";
  do {
    let digit = value & 31;
    value >>>= 5;
    if (value > 0) digit |= 32;
    result += BASE64[digit];
  } while (value > 0);
  return result;
}

function position(text, offset) {
  const before = text.slice(0, offset);
  const lines = before.split("\n");
  return { line: lines.length - 1, column: lines.at(-1).length };
}
