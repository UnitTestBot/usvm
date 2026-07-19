#!/usr/bin/env node
"use strict";

const { mkdir, readFile, writeFile, readdir } = require("node:fs/promises");
const { existsSync } = require("node:fs");
const { dirname, join, relative, resolve } = require("node:path");

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const ts = require(resolve(options.typescriptDir, "node_modules/typescript"));
  const manifest = JSON.parse(await readFile(options.manifest, "utf8"));
  const sourceFiles = await findFiles(options.sourceRoot);
  const sourceIndex = indexSources(sourceFiles, options.sourceRoot);
  const parsed = new Map();
  const entries = [];

  if (options.compiledOut) {
    for (const sourcePath of sourceFiles) await compileFile(ts, sourcePath, options.sourceRoot, options.compiledOut);
  }

  for (const method of manifest.methods) {
    const reasons = [];
    if ((method.branches?.length ?? 0) === 0) reasons.push("no-branches");
    if (method.entryKind !== "free" && method.entryKind !== "static") reasons.push(`entry-kind:${method.entryKind}`);
    const sourcePath = resolveSource(method.fileName, sourceIndex);
    if (!sourcePath) reasons.push("source-file-unresolved");

    let binding = null;
    if (sourcePath && reasons.length === 0) {
      let source = parsed.get(sourcePath);
      if (!source) {
        const text = await readFile(sourcePath, "utf8");
        const tree = ts.createSourceFile(sourcePath, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
        source = { tree, candidates: collectCandidates(ts, tree), exports: collectExports(ts, tree) };
        parsed.set(sourcePath, source);
      }
      binding = findBinding(method, source);
      if (!binding) reasons.push("function-origin-unresolved");
      else if (!binding.exportName) reasons.push(binding.topLevel ? "not-exported" : "nested-function");
    }

    const primitiveParameters = primitiveOnly(method.parameters ?? method.parameterTypes.map((type) => ({ type })));
    if (!primitiveParameters) reasons.push("non-primitive-parameters");
    const sourceRelative = sourcePath ? normalize(relative(options.sourceRoot, sourcePath)) : null;
    entries.push({
      methodId: method.methodId,
      fileName: method.fileName,
      sourceFile: sourceRelative,
      compiledModule: sourceRelative && options.compiledOut
        ? resolve(options.compiledOut, sourceRelative.replace(/\.tsx?$/, ".js"))
        : null,
      exportName: binding?.exportName ?? null,
      parameterTypes: method.parameterTypes,
      branches: method.branches?.length ?? 0,
      sourceCallable: reasons.every((reason) => reason === "non-primitive-parameters"),
      primitiveEligible: reasons.length === 0,
      reasons,
    });
  }

  excludeAmbiguousBindings(entries);

  const summary = {
    manifestMethods: entries.length,
    manifestBranches: entries.reduce((sum, entry) => sum + entry.branches, 0),
    sourceCallableMethods: entries.filter((entry) => entry.sourceCallable).length,
    sourceCallableBranches: entries.filter((entry) => entry.sourceCallable).reduce((sum, entry) => sum + entry.branches, 0),
    primitiveEligibleMethods: entries.filter((entry) => entry.primitiveEligible).length,
    primitiveEligibleBranches: entries.filter((entry) => entry.primitiveEligible).reduce((sum, entry) => sum + entry.branches, 0),
    exclusionsByReason: countReasons(entries),
  };
  await writeFile(options.out, `${JSON.stringify({ schemaVersion: 1, summary, entries }, null, 2)}\n`, "utf8");
  if (options.methodIdsOut) {
    await writeFile(options.methodIdsOut, `${entries.filter((entry) => entry.primitiveEligible).map((entry) => entry.methodId).join("\n")}\n`, "utf8");
  }
  console.log(JSON.stringify({ out: options.out, ...summary }));
}

function excludeAmbiguousBindings(entries) {
  const byBinding = new Map();
  for (const entry of entries) {
    if (!entry.sourceFile || !entry.exportName) continue;
    const key = `${entry.sourceFile}\0${entry.exportName}`;
    const values = byBinding.get(key) ?? [];
    values.push(entry);
    byBinding.set(key, values);
  }
  for (const values of byBinding.values()) {
    if (values.length < 2) continue;
    for (const entry of values) {
      entry.reasons.push("ambiguous-source-binding");
      entry.sourceCallable = false;
      entry.primitiveEligible = false;
    }
  }
}

function collectCandidates(ts, tree) {
  const result = [];
  const visit = (node) => {
    if (ts.isFunctionDeclaration(node) && node.name) {
      const topLevel = node.parent === tree;
      result.push({ node, localName: node.name.text, topLevel, directExport: exportName(ts, node, node.name.text) });
    } else if ((ts.isArrowFunction(node) || ts.isFunctionExpression(node)) && ts.isVariableDeclaration(node.parent)) {
      const declaration = node.parent;
      const statement = declaration.parent?.parent;
      const topLevel = ts.isVariableStatement(statement) && statement.parent === tree;
      const localName = ts.isIdentifier(declaration.name) ? declaration.name.text : null;
      result.push({ node, localName, topLevel, directExport: topLevel ? exportName(ts, statement, localName) : null });
    }
    ts.forEachChild(node, visit);
  };
  visit(tree);
  return result;
}

function collectExports(ts, tree) {
  const result = new Map();
  for (const statement of tree.statements) {
    if (!ts.isExportDeclaration(statement) || !statement.exportClause || !ts.isNamedExports(statement.exportClause)) continue;
    for (const element of statement.exportClause.elements) {
      result.set((element.propertyName ?? element.name).text, element.name.text);
    }
  }
  return result;
}

function exportName(ts, node, localName) {
  const modifiers = ts.getModifiers(node) ?? [];
  if (!modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword)) return null;
  if (modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.DefaultKeyword)) return "default";
  return localName;
}

function findBinding(method, source) {
  const origins = (method.branches ?? []).flatMap((branch) => [branch.conditionOrigin, branch.successorOrigin])
    .filter((origin) => Number.isInteger(origin?.startOffset) && Number.isInteger(origin?.endOffset));
  if (origins.length === 0) return null;
  const start = Math.min(...origins.map((origin) => origin.startOffset));
  const end = Math.max(...origins.map((origin) => origin.endOffset));
  const containing = source.candidates
    .filter((candidate) => candidate.node.getStart(source.tree) <= start && candidate.node.getEnd() >= end)
    .sort((left, right) => (left.node.getEnd() - left.node.getStart(source.tree)) - (right.node.getEnd() - right.node.getStart(source.tree)));
  const candidate = containing[0];
  if (!candidate) return null;
  return {
    ...candidate,
    exportName: candidate.directExport ?? (candidate.topLevel ? source.exports.get(candidate.localName) ?? null : null),
  };
}

function primitiveOnly(parameters) {
  return parameters.every((parameter) => !parameter.rest && primitiveType(parameter.type));
}

function primitiveType(raw) {
  const type = stripParentheses(String(raw).trim());
  const union = splitTopLevel(type, "|");
  if (union.length > 1) return union.every(primitiveType);
  return ["number", "boolean", "string"].includes(type)
    || /^"(?:[^"\\]|\\.)*"$/.test(type)
    || type === "true" || type === "false"
    || /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(type);
}

function splitTopLevel(value, separator) {
  const result = [];
  let start = 0, angle = 0, square = 0, round = 0;
  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    if (char === "<") angle += 1;
    else if (char === ">") angle -= 1;
    else if (char === "[") square += 1;
    else if (char === "]") square -= 1;
    else if (char === "(") round += 1;
    else if (char === ")") round -= 1;
    else if (char === separator && angle === 0 && square === 0 && round === 0) {
      result.push(value.slice(start, index).trim());
      start = index + 1;
    }
  }
  result.push(value.slice(start).trim());
  return result.filter(Boolean);
}

function stripParentheses(value) {
  if (!value.startsWith("(") || !value.endsWith(")")) return value;
  let depth = 0;
  for (let index = 0; index < value.length; index += 1) {
    if (value[index] === "(") depth += 1;
    else if (value[index] === ")") depth -= 1;
    if (depth === 0 && index < value.length - 1) return value;
  }
  return stripParentheses(value.slice(1, -1).trim());
}

async function compileFile(ts, sourcePath, sourceRoot, compiledOut) {
  const source = await readFile(sourcePath, "utf8");
  const output = ts.transpileModule(source, {
    fileName: sourcePath,
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
      sourceMap: true,
    },
  });
  const relativePath = relative(sourceRoot, sourcePath).replace(/\.tsx?$/, ".js");
  const outputPath = resolve(compiledOut, relativePath);
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, output.outputText, "utf8");
  if (output.sourceMapText) await writeFile(`${outputPath}.map`, output.sourceMapText, "utf8");
}

async function findFiles(root) {
  const result = [];
  for (const entry of await readdir(root, { withFileTypes: true })) {
    const path = join(root, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== "node_modules" && entry.name !== "__test__" && entry.name !== "test") result.push(...await findFiles(path));
    } else if (entry.isFile() && /\.tsx?$/.test(entry.name) && !/\.(?:test|spec|d)\.ts$/.test(entry.name)) {
      result.push(resolve(path));
    }
  }
  return result.sort();
}

function indexSources(files, root) {
  const result = new Map();
  for (const file of files) {
    const rel = normalize(relative(root, file));
    for (const key of new Set([rel, normalize(file), rel.split("/").pop()])) {
      const values = result.get(key) ?? [];
      values.push(file);
      result.set(key, values);
    }
  }
  return result;
}

function resolveSource(fileName, index) {
  for (const key of [normalize(fileName), normalize(fileName).split("/").pop()]) {
    const matches = index.get(key) ?? [];
    if (matches.length === 1 && existsSync(matches[0])) return matches[0];
  }
  return null;
}

function countReasons(entries) {
  const result = {};
  for (const entry of entries) for (const reason of entry.reasons) result[reason] = (result[reason] ?? 0) + 1;
  return result;
}

function normalize(path) { return String(path).replaceAll("\\", "/"); }

function parseArgs(args) {
  const result = { manifest: null, sourceRoot: null, typescriptDir: null, compiledOut: null, methodIdsOut: null, out: null };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--source-root": result.sourceRoot = resolve(args[++index]); break;
      case "--typescript-dir": result.typescriptDir = resolve(args[++index]); break;
      case "--compiled-out": result.compiledOut = resolve(args[++index]); break;
      case "--method-ids-out": result.methodIdsOut = resolve(args[++index]); break;
      case "--out": result.out = resolve(args[++index]); break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.sourceRoot || !result.typescriptDir || !result.out) {
    throw new Error("required: --manifest --source-root --typescript-dir --out [--compiled-out]");
  }
  return result;
}
