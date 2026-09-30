import { readFile, readdir, realpath } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import {
  generatedPositionFor,
  originalPositionFor,
  TraceMap,
} from '@jridgewell/trace-mapping';
import ts from 'typescript';

interface V8Range {
  startOffset: number;
  endOffset: number;
  count: number;
}

interface V8Function {
  ranges: V8Range[];
  isBlockCoverage: boolean;
}

interface V8Script {
  url: string;
  functions: V8Function[];
}

interface SourceMapCacheEntry {
  lineLengths: number[];
  data: {
    version: 3;
    sources: string[];
    sourcesContent?: (string | null)[];
    mappings: string;
    names: string[];
  };
}

interface V8Report {
  result?: V8Script[];
  'source-map-cache'?: Record<string, SourceMapCacheEntry>;
}

interface Position {
  line: number;
  column: number;
}

interface Range {
  start: Position;
  end: Position;
}

interface Branch {
  branchId: number;
  type: 'if';
  location: Range;
  arms: { location: Range; hits: number }[];
}

interface BranchDiagnostic {
  code: string;
  message: string;
  path: string;
}

interface BranchReport {
  files: { path: string; branches: Branch[] }[];
  diagnostics: BranchDiagnostic[];
}

interface ScriptCoverage {
  script: V8Script;
  cache: SourceMapCacheEntry;
  path: string;
}

/** Reconstructs only source-mapped TypeScript if arms whose V8 counters agree. */
export async function convertV8IfBranches(rawDirectory: string, sourceRoots: string[]): Promise<BranchReport> {
  const roots = await Promise.all(sourceRoots.map(root => realpath(root)));
  const names = (await readdir(rawDirectory)).filter(name => name.endsWith('.json')).sort();
  const reports = await Promise.all(names.map(async name =>
    JSON.parse(await readFile(path.join(rawDirectory, name), 'utf8')) as V8Report,
  ));
  const scripts = await collectScripts(reports, roots);
  const files: BranchReport['files'] = [];
  const diagnostics: BranchDiagnostic[] = [];

  for (const [sourcePath, candidates] of scripts) {
    if (candidates.length !== 1 || candidates[0] === undefined) {
      diagnostics.push(diagnostic(
        sourcePath,
        'ambiguous',
        'The TypeScript source has several V8 scripts or a script without source-map data',
      ));
      continue;
    }

    const converted = await convertScript(candidates[0]);
    files.push({ path: sourcePath, branches: converted.branches.map((branch, branchId) => ({
      ...branch,
      branchId,
    })) });
    diagnostics.push(...converted.diagnostics);
  }

  return { files, diagnostics };
}

async function collectScripts(reports: V8Report[], roots: string[]): Promise<Map<string, (ScriptCoverage | undefined)[]>> {
  const scripts = new Map<string, (ScriptCoverage | undefined)[]>();

  for (const report of reports) {
    for (const script of report.result ?? []) {
      if (!script.url.startsWith('file:')) continue;

      const sourcePath = await realpath(fileURLToPath(script.url)).catch(() => undefined);
      if (sourcePath === undefined || !sourcePath.endsWith('.ts')) continue;
      if (!roots.some(root => sourcePath.startsWith(`${root}${path.sep}`))) continue;

      const cache = report['source-map-cache']?.[script.url];
      const candidates = scripts.get(sourcePath) ?? [];
      candidates.push(cache === undefined ? undefined : { script, cache, path: sourcePath });
      scripts.set(sourcePath, candidates);
    }
  }

  return scripts;
}

async function convertScript(coverage: ScriptCoverage): Promise<{
  branches: Branch[];
  diagnostics: BranchDiagnostic[];
}> {
  const source = await readFile(coverage.path, 'utf8');
  const sourceIndex = coverage.cache.data.sources.findIndex(candidate =>
    candidate.startsWith('file:') && fileURLToPath(candidate) === coverage.path,
  );
  const coveredSource = coverage.cache.data.sourcesContent?.[sourceIndex];
  if (coveredSource !== source) {
    return {
      branches: [],
      diagnostics: [diagnostic(coverage.path, 'unsupported', 'Covered TypeScript source differs from the current file')],
    };
  }

  const ast = ts.createSourceFile(coverage.path, source, ts.ScriptTarget.Latest, true);
  const map = new TraceMap(coverage.cache.data);
  const branches: Branch[] = [];
  const diagnostics: BranchDiagnostic[] = [];

  function visit(node: ts.Node, unsupportedAncestor: boolean): void {
    const unsupported = unsupportedAncestor || isRepeatedOrExceptional(node);

    if (ts.isIfStatement(node)) {
      const result = convertIf(node, ast, coverage, map, unsupported);
      if (result.branch !== undefined) branches.push(result.branch);
      if (result.reason !== undefined) diagnostics.push(diagnostic(coverage.path, result.kind, result.reason));
    }

    ts.forEachChild(node, child => visit(child, unsupported));
  }

  visit(ast, false);

  return { branches, diagnostics };
}

function convertIf(
  node: ts.IfStatement,
  ast: ts.SourceFile,
  coverage: ScriptCoverage,
  map: TraceMap,
  unsupportedAncestor: boolean,
): { branch?: Branch; reason?: string; kind: 'unsupported' | 'ambiguous' } {
  if (unsupportedAncestor || hasUnsupportedExpression(node.expression)) {
    return { kind: 'unsupported', reason: 'If condition is inside an unsupported control-flow construct' };
  }

  const trueBody = firstExecutable(node.thenStatement);
  const falseBody = node.elseStatement === undefined
    ? falseSuccessor(node)
    : firstExecutable(node.elseStatement);
  if (trueBody === undefined) {
    return { kind: 'unsupported', reason: 'Empty true arm has no V8 execution point' };
  }
  if (falseBody === undefined) {
    const reason = node.elseStatement === undefined
      ? 'An if without else requires a terminating true arm and a following false successor'
      : 'Empty false arm has no V8 execution point';

    return { kind: 'unsupported', reason };
  }

  const conditionHits = countAt(node.getStart(ast), ast, coverage, map);
  const trueHits = countAt(trueBody.getStart(ast), ast, coverage, map);
  const falseHits = countAt(falseBody.getStart(ast), ast, coverage, map);

  if (conditionHits === undefined || trueHits === undefined || falseHits === undefined ||
      trueHits < 0 || falseHits < 0 || trueHits + falseHits !== conditionHits) {
    return { kind: 'ambiguous', reason: 'V8 ranges and exact TypeScript source-map points do not identify both if arms' };
  }

  const branch: Branch = {
    branchId: 0,
    type: 'if',
    // The predicate span excludes nested if statements from this branch's EtsIR target candidates.
    location: sourceRange(node.expression, ast),
    arms: [
      { location: sourceRange(node.thenStatement, ast), hits: trueHits },
      {
        location: node.elseStatement === undefined
          ? sourceRange(node.expression, ast)
          : sourceRange(node.elseStatement, ast),
        hits: falseHits,
      },
    ],
  };

  return { branch, kind: 'ambiguous' };
}

function countAt(
  sourceOffset: number,
  ast: ts.SourceFile,
  coverage: ScriptCoverage,
  map: TraceMap,
): number | undefined {
  const sourcePosition = ast.getLineAndCharacterOfPosition(sourceOffset);
  const sourceUrl = coverage.cache.data.sources.find(candidate =>
    candidate.startsWith('file:') && fileURLToPath(candidate) === coverage.path,
  );
  if (sourceUrl === undefined) return undefined;

  const generated = generatedPositionFor(map, {
    source: sourceUrl,
    line: sourcePosition.line + 1,
    column: sourcePosition.character,
  });
  if (generated.line === null || generated.column === null) return undefined;

  const original = originalPositionFor(map, { line: generated.line, column: generated.column });
  if (original.source !== sourceUrl || original.line !== sourcePosition.line + 1 ||
      original.column !== sourcePosition.character) return undefined;

  const lineLengths = coverage.cache.lineLengths;
  if (lineLengths.some(length => !Number.isSafeInteger(length) || length < 0) ||
      generated.line > lineLengths.length || generated.column > (lineLengths[generated.line - 1] ?? -1)) {
    return undefined;
  }

  const offset = lineLengths.slice(0, generated.line - 1).reduce((sum, length) => sum + length + 1, 0) +
    generated.column;
  const functions = coverage.script.functions.filter(fn => fn.isBlockCoverage &&
    fn.ranges.length > 0 && contains(fn.ranges[0] as V8Range, offset));
  const orderedFunctions = functions.sort((left, right) =>
    rangeLength(left.ranges[0] as V8Range) - rangeLength(right.ranges[0] as V8Range),
  );
  const functionCoverage = orderedFunctions[0];
  if (functionCoverage === undefined) return undefined;
  if (orderedFunctions[1] !== undefined &&
      rangeLength(orderedFunctions[1].ranges[0] as V8Range) === rangeLength(functionCoverage.ranges[0] as V8Range)) {
    return undefined;
  }

  const ranges = functionCoverage.ranges.filter(range => contains(range, offset));
  const orderedRanges = ranges.sort((left, right) => rangeLength(left) - rangeLength(right));
  const innermost = orderedRanges[0];
  if (innermost === undefined || orderedRanges[1] !== undefined &&
      rangeLength(orderedRanges[1]) === rangeLength(innermost)) return undefined;

  return Number.isSafeInteger(innermost.count) && innermost.count >= 0 ? innermost.count : undefined;
}

function firstExecutable(statement: ts.Statement): ts.Statement | undefined {
  if (ts.isBlock(statement)) return statement.statements[0];

  return statement;
}

function falseSuccessor(node: ts.IfStatement): ts.Statement | undefined {
  const consequent = node.thenStatement;
  const soleStatement = ts.isBlock(consequent) ? consequent.statements[0] : consequent;
  const terminates = soleStatement !== undefined &&
    (!ts.isBlock(consequent) || consequent.statements.length === 1) &&
    (ts.isReturnStatement(soleStatement) || ts.isThrowStatement(soleStatement));
  if (!terminates) return undefined;

  const parent = node.parent;
  if (!ts.isBlock(parent) && !ts.isSourceFile(parent)) return undefined;

  const index = parent.statements.indexOf(node);

  return index < 0 ? undefined : parent.statements[index + 1];
}

const isRepeatedOrExceptional = (node: ts.Node): boolean =>
  ts.isForStatement(node) || ts.isForInStatement(node) || ts.isForOfStatement(node) ||
  ts.isWhileStatement(node) || ts.isDoStatement(node) || ts.isSwitchStatement(node) ||
  ts.isTryStatement(node) || ts.isConditionalExpression(node) ||
  ts.isBinaryExpression(node) && [
    ts.SyntaxKind.AmpersandAmpersandToken,
    ts.SyntaxKind.BarBarToken,
    ts.SyntaxKind.QuestionQuestionToken,
  ].includes(node.operatorToken.kind);

function hasUnsupportedExpression(node: ts.Node): boolean {
  if (isRepeatedOrExceptional(node)) return true;

  return ts.forEachChild(node, child => hasUnsupportedExpression(child) ? true : undefined) === true;
}

function sourceRange(node: ts.Node, ast: ts.SourceFile): Range {
  const start = ast.getLineAndCharacterOfPosition(node.getStart(ast));
  const end = ast.getLineAndCharacterOfPosition(node.getEnd());

  return {
    start: { line: start.line + 1, column: start.character },
    end: { line: end.line + 1, column: end.character },
  };
}

const contains = (range: V8Range, offset: number): boolean =>
  range.startOffset <= offset && offset < range.endOffset;

const rangeLength = (range: V8Range): number => range.endOffset - range.startOffset;

function diagnostic(sourcePath: string, kind: string, message: string): BranchDiagnostic {
  return { code: `coverage.branch.${kind}`, message, path: sourcePath };
}

if (process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [rawDirectory, ...sourceRoots] = process.argv.slice(2);
  if (rawDirectory === undefined) throw new Error('Raw V8 coverage directory is required');

  process.stdout.write(`${JSON.stringify(await convertV8IfBranches(rawDirectory, sourceRoots))}\n`);
}
