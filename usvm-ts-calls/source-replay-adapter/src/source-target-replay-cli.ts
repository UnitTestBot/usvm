import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { copyFile, mkdir, mkdtemp, readFile, readdir, realpath, rm, stat, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import type { TypeScriptEntryPointReference } from './calls-entry-point.js';
import type { TaggedJsValue } from './calls-js-value.js';

interface SourcePosition {
  line: number;
  column: number;
}

interface ReplayRequest {
  sourceRoots: string[];
  entryPoint: TypeScriptEntryPointReference;
  inputs: TaggedJsValue[];
  target: {
    sourcePath: string;
    startOffset: number;
    endOffset: number;
    start: SourcePosition;
    end: SourcePosition;
  };
  timeoutMillis: number;
}

interface WorkerResult {
  invocation: 'returned' | 'threw';
  targetHit: boolean;
  errorName?: string;
  errorMessage?: string;
}

interface ProcessResult {
  exitCode: number | null;
  timedOut: boolean;
  stderrOverflow: boolean;
  stderr: string;
}

interface ResolvedTarget {
  sourceRootIndex: number;
  sourceRoot: string;
  sourcePath: string;
  absolutePath: string;
  source: string;
}

async function main(): Promise<void> {
  const request = validateRequest(JSON.parse(await readStdin()) as unknown);
  const workspace = await mkdtemp(path.join(tmpdir(), 'usvm-ts-calls-replay-'));

  try {
    const target = await resolveTarget(request);
    const sourceFile = ts.createSourceFile(
      target.absolutePath,
      target.source,
      ts.ScriptTarget.Latest,
      true,
      scriptKind(target.absolutePath),
    );
    const matches = collectStatements(sourceFile).filter((statement) =>
      statement.getStart(sourceFile, false) === request.target.startOffset
        && statement.getEnd() === request.target.endOffset);
    if (matches.length !== 1) {
      writeResponse({
        status: 'ok',
        replayStatus: matches.length === 0 ? 'unmapped' : 'ambiguous',
        reason: matches.length === 0 ? 'statement-range-unmapped' : 'statement-range-ambiguous',
        invocation: null,
      });
      return;
    }

    const statement = matches[0] as ts.Statement;
    const start = sourceFile.getLineAndCharacterOfPosition(statement.getStart(sourceFile, false));
    const end = sourceFile.getLineAndCharacterOfPosition(statement.getEnd());
    if (!samePosition(start, request.target.start) || !samePosition(end, request.target.end)) {
      writeResponse({ status: 'ok', replayStatus: 'unmapped', reason: 'statement-coordinate-mismatch', invocation: null });
      return;
    }
    if (!ts.isBlock(statement.parent) && !ts.isSourceFile(statement.parent)) {
      writeResponse({ status: 'ok', replayStatus: 'unsupported', reason: 'statement-parent-unsupported', invocation: null });
      return;
    }

    const hitKey = `__usvm_source_target_${randomUUID().replaceAll('-', '_')}`;
    const marker = `;(globalThis as Record<string, unknown>)[${JSON.stringify(hitKey)}] = true;\n`;
    const instrumented = target.source.slice(0, request.target.startOffset)
      + marker
      + target.source.slice(request.target.startOffset);
    const overlayRoot = path.join(workspace, 'source-overlay');
    await createOverlay(target.sourceRoot, overlayRoot, target.sourcePath, instrumented);
    const workerRequestPath = path.join(workspace, 'request.json');
    const workerResultPath = path.join(workspace, 'result.json');
    const workerPath = path.join(path.dirname(fileURLToPath(import.meta.url)), 'source-target-replay-worker.js');
    await requireFile(workerPath, 'source-target replay worker');
    const sourceRoots = request.sourceRoots.map((root, index) => index === target.sourceRootIndex ? overlayRoot : root);
    await writeFile(workerRequestPath, JSON.stringify({
      sourceRoots,
      entryPoint: request.entryPoint,
      inputs: request.inputs,
      hitKey,
      resultPath: workerResultPath,
    }), 'utf8');

    const execution = await runProcess(process.execPath, [workerPath, workerRequestPath], request.timeoutMillis);
    if (execution.timedOut) {
      writeResponse({ status: 'ok', replayStatus: 'timeout', invocation: null });
      return;
    }
    if (execution.stderrOverflow) {
      writeResponse({ status: 'error', replayStatus: 'tool-error', message: 'worker stderr exceeded 65536 bytes' });
      return;
    }
    if (execution.exitCode !== 0) {
      writeResponse({
        status: 'error', replayStatus: 'tool-error', message: execution.stderr.trim() || `worker exited with code ${execution.exitCode}`,
      });
      return;
    }

    const worker = JSON.parse(await readFile(workerResultPath, 'utf8')) as WorkerResult;
    writeResponse({ status: 'ok', replayStatus: worker.targetHit ? 'confirmed' : 'rejected', invocation: worker });
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
}

function validateRequest(value: unknown): ReplayRequest {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error('Request must be an object');
  const request = value as Partial<ReplayRequest>;
  if (!Array.isArray(request.sourceRoots) || request.sourceRoots.length === 0) throw new Error('sourceRoots are required');
  if (request.entryPoint === undefined || !Array.isArray(request.inputs)) throw new Error('entryPoint and inputs are required');
  if (request.target === undefined || typeof request.target.sourcePath !== 'string') throw new Error('target is required');
  if (!Number.isInteger(request.timeoutMillis) || (request.timeoutMillis as number) <= 0) throw new Error('timeoutMillis must be positive');

  return request as ReplayRequest;
}

async function resolveTarget(request: ReplayRequest): Promise<ResolvedTarget> {
  if (path.isAbsolute(request.target.sourcePath)) throw new Error('target.sourcePath must be relative');
  const matches: ResolvedTarget[] = [];

  for (const [sourceRootIndex, sourceRootValue] of request.sourceRoots.entries()) {
    const sourceRoot = await realpath(sourceRootValue);
    const candidate = path.resolve(sourceRoot, request.target.sourcePath);
    const relative = path.relative(sourceRoot, candidate);
    if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) continue;

    try {
      const absolutePath = await realpath(candidate);
      const canonicalRelative = path.relative(sourceRoot, absolutePath);
      if (canonicalRelative === '..' || canonicalRelative.startsWith(`..${path.sep}`) || path.isAbsolute(canonicalRelative)) continue;
      if ((await stat(absolutePath)).isFile()) {
        matches.push({
          sourceRootIndex,
          sourceRoot,
          sourcePath: request.target.sourcePath,
          absolutePath,
          source: await readFile(absolutePath, 'utf8'),
        });
      }
    } catch (error: unknown) {
      if (!(error instanceof Error && 'code' in error && (error as NodeJS.ErrnoException).code === 'ENOENT')) throw error;
    }
  }

  if (matches.length !== 1) throw new Error(`Target source path resolved to ${matches.length} files`);
  return matches[0] as ResolvedTarget;
}

function collectStatements(sourceFile: ts.SourceFile): ts.Statement[] {
  const statements: ts.Statement[] = [];
  const visit = (node: ts.Node): void => {
    if (ts.isStatement(node)) statements.push(node);
    ts.forEachChild(node, visit);
  };
  visit(sourceFile);
  return statements;
}

function scriptKind(filePath: string): ts.ScriptKind {
  return filePath.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
}

function samePosition(actual: ts.LineAndCharacter, expected: SourcePosition): boolean {
  return actual.line === expected.line && actual.character === expected.column;
}

async function createOverlay(
  sourceRoot: string,
  overlayRoot: string,
  relativeTarget: string,
  instrumentedSource: string,
): Promise<void> {
  const segments = relativeTarget.split('/');
  if (segments.length === 0 || segments.some((segment) => segment.length === 0 || segment === '.' || segment === '..')) {
    throw new Error('target.sourcePath must be normalized POSIX relative path');
  }

  let sourceDirectory = sourceRoot;
  let overlayDirectory = overlayRoot;
  await mkdir(overlayDirectory, { recursive: true });
  for (const [index, segment] of segments.entries()) {
    const last = index === segments.length - 1;
    const entries = await readdir(sourceDirectory);
    for (const entry of entries) {
      if (entry === segment) continue;
      const original = path.join(sourceDirectory, entry);
      await mirrorOverlayEntry(original, path.join(overlayDirectory, entry));
    }
    if (last) {
      await writeFile(path.join(overlayDirectory, segment), instrumentedSource, 'utf8');
    } else {
      sourceDirectory = path.join(sourceDirectory, segment);
      overlayDirectory = path.join(overlayDirectory, segment);
      await mkdir(overlayDirectory);
    }
  }
}

async function mirrorOverlayEntry(original: string, overlay: string): Promise<void> {
  const kind = (await stat(original)).isDirectory() ? 'dir' : 'file';
  if (process.platform === 'win32') {
    if (kind === 'dir') {
      await symlink(original, overlay, 'junction');
    } else {
      await copyFile(original, overlay);
    }
    return;
  }

  await symlink(original, overlay, kind);
}

async function runProcess(executable: string, args: string[], timeoutMillis: number): Promise<ProcessResult> {
  const child = spawn(executable, args, { stdio: ['ignore', 'ignore', 'pipe'] });
  const stderrChunks: Buffer[] = [];
  let stderrBytes = 0;
  let stderrOverflow = false;
  child.stderr.on('data', (chunk: Buffer) => {
    if (stderrOverflow) return;

    stderrBytes += chunk.length;
    if (stderrBytes > MAX_WORKER_STDERR_BYTES) {
      stderrOverflow = true;
      child.kill('SIGTERM');
      return;
    }

    stderrChunks.push(chunk);
  });

  return await new Promise((resolve, reject) => {
    let timedOut = false;
    let forceKillTimer: NodeJS.Timeout | undefined;
    const timer = setTimeout(() => {
      timedOut = true;
      child.kill('SIGTERM');
      forceKillTimer = setTimeout(() => child.kill('SIGKILL'), WORKER_SHUTDOWN_GRACE_MILLIS);
      forceKillTimer.unref();
    }, timeoutMillis);
    child.once('error', reject);
    child.once('close', (exitCode) => {
      clearTimeout(timer);
      if (forceKillTimer !== undefined) clearTimeout(forceKillTimer);
      const stderr = Buffer.concat(stderrChunks).toString('utf8');
      resolve({ exitCode, timedOut, stderrOverflow, stderr });
    });
  });
}

async function requireFile(filePath: string, description: string): Promise<void> {
  if (!(await stat(filePath)).isFile()) throw new Error(`Missing ${description}: ${filePath}`);
}

async function readStdin(): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of process.stdin) chunks.push(Buffer.from(chunk));
  return Buffer.concat(chunks).toString('utf8');
}

function writeResponse(response: unknown): void {
  process.stdout.write(`${JSON.stringify(response)}\n`);
}

main().catch((error: unknown) => {
  writeResponse({ status: 'error', replayStatus: 'tool-error', message: error instanceof Error ? error.message : String(error) });
  process.exitCode = 1;
});

const MAX_WORKER_STDERR_BYTES = 64 * 1024;
const WORKER_SHUTDOWN_GRACE_MILLIS = 250;
