#!/usr/bin/env node
"use strict";

const { mkdir, readFile, writeFile, readdir } = require("node:fs/promises");
const { existsSync } = require("node:fs");
const { dirname, join, relative, resolve } = require("node:path");
const {
  SourceCallableId,
  SourceMapIndex,
  mapManifestEdges,
  normalizeModulePath,
  rangeFromOffsets,
} = require("./mapping/source-mapping.cjs");

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
  const compiled = new Map();
  const mappingContexts = new Map();
  const entries = [];

  if (options.sourceTargetsOut && manifest.schemaVersion !== 2) {
    throw new Error(
      `production source-targets JSONL requires target manifest schemaVersion 2; got ${manifest.schemaVersion}. ` +
        "Historical v1 manifests are accepted only for the legacy selection projection (--out).",
    );
  }

  for (const method of manifest.methods) {
    const reasons = [];
    if ((method.branches?.length ?? 0) === 0) reasons.push("no-branches");
    if (method.entryKind !== "free" && method.entryKind !== "static") reasons.push(`entry-kind:${method.entryKind}`);
    const sourceResolution = resolveSource(method.fileName, sourceIndex);
    const sourcePath = sourceResolution.path;
    if (sourceResolution.status !== "exact") reasons.push(
      sourceResolution.status === "ambiguous" ? "source-file-ambiguous" : "source-file-unresolved",
    );
    const resolveBindingForLegacySelection = reasons.length === 0;

    let bindingResolution = { status: "unmapped", candidates: [] };
    let selectionBinding = null;
    if (sourcePath) {
      let source = parsed.get(sourcePath);
      if (!source) {
        const text = await readFile(sourcePath, "utf8");
        const tree = ts.createSourceFile(sourcePath, text, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
        const sourceRelative = normalize(relative(options.sourceRoot, sourcePath));
        source = {
          tree,
          text,
          modulePath: sourceRelative,
          candidates: collectCandidates(ts, tree, sourceRelative),
          exports: collectExports(ts, tree),
          importOrigins: collectImports(ts, tree, sourceRelative),
        };
        parsed.set(sourcePath, source);
      }
      bindingResolution = findBinding(method, source);
      if (resolveBindingForLegacySelection) {
        const selectionResolution = manifest.schemaVersion === 1
          ? findBinding(method, source, { legacyV1Projection: true })
          : bindingResolution;
        selectionBinding = selectionResolution.binding;
        if (selectionResolution.status !== "exact") reasons.push(
          selectionResolution.status === "ambiguous" ? "function-origin-ambiguous" : "function-origin-unresolved",
        );
        else if (!selectionBinding.exportName) reasons.push(
          selectionBinding.topLevel ? "not-exported" : "nested-function",
        );
      }

      let compiledSource = null;
      if (options.compiledOut || options.sourceTargetsOut) {
        compiledSource = compiled.get(sourcePath);
        if (!compiledSource) {
          compiledSource = await compileFile(ts, sourcePath, options.sourceRoot, options.compiledOut);
          compiled.set(sourcePath, compiledSource);
        }
      }
      mappingContexts.set(method.methodId, createMappingContext(sourceResolution, bindingResolution, source, compiledSource));
    } else {
      mappingContexts.set(method.methodId, {
        modulePath: normalizeModulePath(method.fileName),
        sourceResolution: sourceResolution.status,
        bindingResolution: "unmapped",
        candidates: sourceResolution.candidates,
      });
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
      exportName: selectionBinding?.exportName ?? null,
      parameterTypes: method.parameterTypes,
      branches: method.branches?.length ?? 0,
      sourceCallable: reasons.every((reason) => reason === "non-primitive-parameters"),
      primitiveEligible: reasons.length === 0,
      reasons,
    });
  }

  const ambiguousBindings = excludeAmbiguousBindings(entries);
  for (const methodId of ambiguousBindings) {
    const context = mappingContexts.get(methodId);
    if (context) {
      context.bindingResolution = "ambiguous";
      context.reasons = [...(context.reasons ?? []), "runtime-export-binding-collision"];
    }
  }

  const summary = {
    manifestMethods: entries.length,
    manifestBranches: entries.reduce((sum, entry) => sum + entry.branches, 0),
    sourceCallableMethods: entries.filter((entry) => entry.sourceCallable).length,
    sourceCallableBranches: entries.filter((entry) => entry.sourceCallable).reduce((sum, entry) => sum + entry.branches, 0),
    primitiveEligibleMethods: entries.filter((entry) => entry.primitiveEligible).length,
    primitiveEligibleBranches: entries.filter((entry) => entry.primitiveEligible).reduce((sum, entry) => sum + entry.branches, 0),
    exclusionsByReason: countReasons(entries),
  };
  if (options.out) {
    await mkdir(dirname(options.out), { recursive: true });
    await writeFile(options.out, `${JSON.stringify({ schemaVersion: 1, summary, entries }, null, 2)}\n`, "utf8");
  }
  if (options.methodIdsOut) {
    await mkdir(dirname(options.methodIdsOut), { recursive: true });
    await writeFile(options.methodIdsOut, `${entries.filter((entry) => entry.primitiveEligible).map((entry) => entry.methodId).join("\n")}\n`, "utf8");
  }
  if (options.sourceCallableMethodIdsOut) {
    await mkdir(dirname(options.sourceCallableMethodIdsOut), { recursive: true });
    await writeFile(
      options.sourceCallableMethodIdsOut,
      `${entries.filter((entry) => entry.sourceCallable).map((entry) => entry.methodId).join("\n")}\n`,
      "utf8",
    );
  }
  let mappingSummary = null;
  if (options.sourceTargetsOut) {
    const mapping = mapManifestEdges(manifest, mappingContexts);
    await mkdir(dirname(options.sourceTargetsOut), { recursive: true });
    await writeFile(
      options.sourceTargetsOut,
      `${mapping.records.map((record) => JSON.stringify(record)).join("\n")}\n`,
      "utf8",
    );
    await mkdir(dirname(options.mappingReportOut), { recursive: true });
    await writeFile(options.mappingReportOut, `${JSON.stringify(mapping.report, null, 2)}\n`, "utf8");
    mappingSummary = {
      sourceTargetsOut: options.sourceTargetsOut,
      mappingReportOut: options.mappingReportOut,
      edges: mapping.records.length,
      mappingStatus: mapping.report.mappingStatus,
      silentDrops: mapping.report.silentDrops,
    };
  }
  console.log(JSON.stringify({ out: options.out, ...summary, mapping: mappingSummary }));
}

function excludeAmbiguousBindings(entries) {
  const ambiguous = new Set();
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
      ambiguous.add(entry.methodId);
    }
  }
  return ambiguous;
}

function collectCandidates(ts, tree, modulePath) {
  const result = [];
  const visit = (node) => {
    if (ts.isFunctionDeclaration(node) && node.name) {
      const topLevel = node.parent === tree;
      result.push(candidate(tree, modulePath, node, node.name, {
        localName: node.name.text,
        qualifiedName: node.name.text,
        callableKind: "free",
        topLevel,
        directExport: exportName(ts, node, node.name.text),
      }));
    } else if ((ts.isArrowFunction(node) || ts.isFunctionExpression(node)) && ts.isVariableDeclaration(node.parent)) {
      const declaration = node.parent;
      const statement = declaration.parent?.parent;
      const topLevel = ts.isVariableStatement(statement) && statement.parent === tree;
      const localName = ts.isIdentifier(declaration.name) ? declaration.name.text : null;
      if (localName) result.push(candidate(tree, modulePath, node, declaration.name, {
        localName,
        qualifiedName: localName,
        callableKind: "arrow",
        topLevel,
        directExport: topLevel ? exportName(ts, statement, localName) : null,
      }));
    } else if (ts.isMethodDeclaration(node) && hasModifier(ts, node, ts.SyntaxKind.StaticKeyword)) {
      const classNode = node.parent;
      if (ts.isClassDeclaration(classNode) && classNode.name && ts.isIdentifier(node.name)) {
        const topLevel = classNode.parent === tree;
        const classLocalName = classNode.name.text;
        const classExport = topLevel ? exportName(ts, classNode, classLocalName) : null;
        const memberName = node.name.text;
        result.push(candidate(tree, modulePath, node, node.name, {
          localName: memberName,
          qualifiedName: `${classLocalName}.${memberName}`,
          callableKind: "static",
          topLevel,
          directExport: classExport ? `${classExport}.${memberName}` : null,
          classLocalName,
        }));
      }
    }
    ts.forEachChild(node, visit);
  };
  visit(tree);
  return result;
}

function candidate(tree, modulePath, node, bindingNode, fields) {
  return {
    ...fields,
    node,
    declarationRange: nodeRange(tree, node, modulePath),
    bindingRange: nodeRange(tree, bindingNode, modulePath),
    parameterCount: node.parameters?.length ?? 0,
  };
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

function collectImports(ts, tree, modulePath) {
  const result = [];
  for (const statement of tree.statements) {
    if (ts.isImportDeclaration(statement) && ts.isStringLiteral(statement.moduleSpecifier)) {
      const sourceModule = statement.moduleSpecifier.text;
      const clause = statement.importClause;
      if (!clause) {
        result.push(importOrigin(tree, modulePath, sourceModule, "side-effect", null, null, statement));
        continue;
      }
      if (clause.name) {
        result.push(importOrigin(tree, modulePath, sourceModule, "default", "default", clause.name.text, clause.name));
      }
      if (clause.namedBindings && ts.isNamespaceImport(clause.namedBindings)) {
        result.push(importOrigin(
          tree, modulePath, sourceModule, "namespace", "*", clause.namedBindings.name.text, clause.namedBindings.name,
        ));
      } else if (clause.namedBindings && ts.isNamedImports(clause.namedBindings)) {
        for (const element of clause.namedBindings.elements) {
          result.push(importOrigin(
            tree,
            modulePath,
            sourceModule,
            "named",
            (element.propertyName ?? element.name).text,
            element.name.text,
            element,
          ));
        }
      }
    } else if (ts.isImportEqualsDeclaration(statement)) {
      result.push(importOrigin(
        tree,
        modulePath,
        statement.moduleReference.getText(tree),
        "import-equals",
        "=",
        statement.name.text,
        statement,
      ));
    }
  }
  return result;
}

function importOrigin(tree, modulePath, sourceModule, importKind, importedName, localName, node) {
  const bindingName = localName ?? importedName ?? importKind;
  return {
    importBindingId: `ts:${encodeURIComponent(modulePath)}::import:${encodeURIComponent(bindingName)}`,
    importingModulePath: modulePath,
    sourceModule,
    importKind,
    importedName,
    localName,
    sourceRange: nodeRange(tree, node, modulePath),
  };
}

function exportName(ts, node, localName) {
  const modifiers = ts.getModifiers(node) ?? [];
  if (!modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword)) return null;
  if (modifiers.some((modifier) => modifier.kind === ts.SyntaxKind.DefaultKeyword)) return "default";
  return localName;
}

function findBinding(method, source, { legacyV1Projection = false } = {}) {
  const origins = (method.branches ?? []).flatMap((branch) => [branch.conditionOrigin, branch.successorOrigin])
    .filter((origin) => Number.isInteger(origin?.startOffset) && Number.isInteger(origin?.endOffset));
  if (origins.length === 0) return { status: "unmapped", binding: null, candidates: [] };
  const start = Math.min(...origins.map((origin) => origin.startOffset));
  const end = Math.max(...origins.map((origin) => origin.endOffset));
  const bindingCandidates = legacyV1Projection
    ? source.candidates.filter((candidate) => candidate.callableKind !== "static")
    : source.candidates;
  const containing = bindingCandidates
    .filter((candidate) => candidate.node.getStart(source.tree) <= start && candidate.node.getEnd() >= end)
    .sort((left, right) => (left.node.getEnd() - left.node.getStart(source.tree)) - (right.node.getEnd() - right.node.getStart(source.tree)));
  if (containing.length === 0) return { status: "unmapped", binding: null, candidates: [] };
  const minimum = containing[0].node.getEnd() - containing[0].node.getStart(source.tree);
  const narrowest = containing.filter((item) => item.node.getEnd() - item.node.getStart(source.tree) === minimum);
  const candidates = narrowest.map((item) => ({
    qualifiedName: item.qualifiedName,
    callableKind: item.callableKind,
    declarationRange: item.declarationRange,
  }));
  if (narrowest.length !== 1) return { status: "ambiguous", binding: null, candidates };
  const selected = narrowest[0];
  let indirectExport = null;
  if (selected.topLevel) {
    if (selected.callableKind === "static") {
      const classExport = source.exports.get(selected.classLocalName);
      if (classExport) indirectExport = `${classExport}.${selected.localName}`;
    } else {
      indirectExport = source.exports.get(selected.localName) ?? null;
    }
  }
  const exportBinding = selected.directExport ?? indirectExport;
  const bindingKind = selected.directExport
    ? "direct-export"
    : indirectExport
      ? "named-export"
      : selected.topLevel ? "module-local" : "nested";
  return {
    status: "exact",
    candidates,
    binding: {
      ...selected,
      exportName: exportBinding,
      runtimeName: exportBinding ?? selected.qualifiedName,
      bindingKind,
      sourceCallableId: SourceCallableId.create({
        modulePath: source.modulePath,
        callableKind: selected.callableKind,
        qualifiedName: selected.qualifiedName,
        arity: Number.isInteger(method.arity) ? method.arity : selected.parameterCount,
      }),
    },
  };
}

function createMappingContext(sourceResolution, bindingResolution, source, compiledSource) {
  const moduleRange = rangeFromOffsets(source.modulePath, source.text, 0, source.text.length);
  return {
    modulePath: source.modulePath,
    sourceResolution: sourceResolution.status,
    bindingResolution: bindingResolution.status,
    binding: bindingResolution.binding,
    candidates: [...(sourceResolution.candidates ?? []), ...(bindingResolution.candidates ?? [])],
    sourceMapIndex: compiledSource?.sourceMapIndex ?? null,
    moduleOrigin: {
      moduleId: `ts:${encodeURIComponent(source.modulePath)}::%module`,
      modulePath: source.modulePath,
      sourceRange: moduleRange,
    },
    importOrigins: source.importOrigins,
    fileInitOrigin: {
      fileInitId: `ts:${encodeURIComponent(source.modulePath)}::%file-init`,
      modulePath: source.modulePath,
      sourceRange: moduleRange,
    },
  };
}

function nodeRange(tree, node, fileName = tree.fileName) {
  const startOffset = node.getStart(tree, false);
  const endOffset = node.getEnd();
  const start = tree.getLineAndCharacterOfPosition(startOffset);
  const end = tree.getLineAndCharacterOfPosition(endOffset);
  return {
    fileName: normalize(fileName),
    startOffset,
    endOffset,
    startLine: start.line,
    startColumn: start.character,
    endLine: end.line,
    endColumn: end.character,
  };
}

function hasModifier(ts, node, kind) {
  return (ts.getModifiers(node) ?? []).some((modifier) => modifier.kind === kind);
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
  const outputPath = compiledOut ? resolve(compiledOut, relativePath) : null;
  if (outputPath) {
    await mkdir(dirname(outputPath), { recursive: true });
    await writeFile(outputPath, output.outputText, "utf8");
    if (output.sourceMapText) await writeFile(`${outputPath}.map`, output.sourceMapText, "utf8");
  }
  const sourceMap = output.sourceMapText ? JSON.parse(output.sourceMapText) : null;
  return {
    outputPath,
    outputText: output.outputText,
    sourceMap,
    sourceMapIndex: sourceMap ? new SourceMapIndex({
      sourceMap,
      sourceText: source,
      generatedText: output.outputText,
      sourceFile: normalize(relative(sourceRoot, sourcePath)),
      generatedFile: normalize(relativePath),
    }) : null,
  };
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
  const candidates = [];
  for (const key of [normalize(fileName), normalize(fileName).split("/").pop()]) {
    const matches = index.get(key) ?? [];
    for (const match of matches) if (existsSync(match) && !candidates.includes(match)) candidates.push(match);
    if (matches.length === 1 && existsSync(matches[0])) {
      return { status: "exact", path: matches[0], candidates: [] };
    }
  }
  if (candidates.length > 1) return { status: "ambiguous", path: null, candidates: candidates.map(normalize) };
  return { status: "unmapped", path: null, candidates: [] };
}

function countReasons(entries) {
  const result = {};
  for (const entry of entries) for (const reason of entry.reasons) result[reason] = (result[reason] ?? 0) + 1;
  return result;
}

function normalize(path) { return String(path).replaceAll("\\", "/"); }

function parseArgs(args) {
  const result = {
    manifest: null,
    sourceRoot: null,
    typescriptDir: null,
    compiledOut: null,
    methodIdsOut: null,
    sourceCallableMethodIdsOut: null,
    sourceTargetsOut: null,
    mappingReportOut: null,
    out: null,
  };
  for (let index = 0; index < args.length; index += 1) {
    switch (args[index]) {
      case "--manifest": result.manifest = args[++index]; break;
      case "--source-root": result.sourceRoot = resolve(args[++index]); break;
      case "--typescript-dir": result.typescriptDir = resolve(args[++index]); break;
      case "--compiled-out": result.compiledOut = resolve(args[++index]); break;
      case "--method-ids-out": result.methodIdsOut = resolve(args[++index]); break;
      case "--source-callable-method-ids-out": result.sourceCallableMethodIdsOut = resolve(args[++index]); break;
      case "--source-targets-out": result.sourceTargetsOut = resolve(args[++index]); break;
      case "--mapping-report-out": result.mappingReportOut = resolve(args[++index]); break;
      case "--out": result.out = resolve(args[++index]); break;
      default: throw new Error(`unknown option '${args[index]}'`);
    }
  }
  if (!result.manifest || !result.sourceRoot || !result.typescriptDir || (!result.out && !result.sourceTargetsOut)) {
    throw new Error(
      "required: --manifest --source-root --typescript-dir and at least one of --out/--source-targets-out; " +
        "optional: [--mapping-report-out] [--compiled-out] [--method-ids-out] [--source-callable-method-ids-out]",
    );
  }
  if (result.mappingReportOut && !result.sourceTargetsOut) {
    throw new Error("--mapping-report-out requires --source-targets-out");
  }
  if (result.sourceTargetsOut && !result.mappingReportOut) {
    result.mappingReportOut = resolve(dirname(result.sourceTargetsOut), "mapping-report.json");
  }
  return result;
}
