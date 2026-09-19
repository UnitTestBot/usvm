import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

interface ReplayResponse {
  status: 'ok' | 'error';
  replayStatus: string;
  reason?: string;
  invocation?: {
    invocation: 'returned' | 'threw';
    targetHit: boolean;
  } | null;
}

interface StatementTarget {
  sourcePath: string;
  sourceSha256: string;
  startOffset: number;
  endOffset: number;
  start: { line: number; column: number };
  end: { line: number; column: number };
}

const adapterRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const fixturePath = path.join(adapterRoot, 'test', 'source-target-replay-fixture.ts');
const cliPath = path.join(adapterRoot, 'dist', 'src', 'source-target-replay-cli.js');

test('confirms only the exact source statement reached by original TypeScript', async () => {
  const taken = await statementTarget('inlineChoose', 'return 1;');
  const untaken = await statementTarget('inlineChoose', 'return 0;');
  const request = baseRequest('inlineChoose');

  const takenResponse = await replay({ ...request, target: taken });
  const untakenResponse = await replay({ ...request, target: untaken });

  assert.equal(takenResponse.replayStatus, 'confirmed');
  assert.equal(takenResponse.invocation?.targetHit, true);
  assert.equal(untakenResponse.replayStatus, 'rejected');
  assert.equal(untakenResponse.invocation?.targetHit, false);
});

test('retains target confirmation when the original TypeScript invocation throws', async () => {
  const target = await statementTarget('throwsAtTarget', "throw new Error('expected');");

  const response = await replay({ ...baseRequest('throwsAtTarget', []), target });

  assert.equal(response.replayStatus, 'confirmed');
  assert.equal(response.invocation?.invocation, 'threw');
  assert.equal(response.invocation?.targetHit, true);
});

test('counts target hits from the selected invocation rather than module import', async () => {
  const target = await statementTarget('importOnlyTarget', 'return 7;');

  const importOnly = await replay({ ...baseRequest('skipsImportOnlyTarget', []), target });
  const invoked = await replay({ ...baseRequest('importOnlyTarget', []), target });

  assert.equal(importOnly.replayStatus, 'rejected');
  assert.equal(importOnly.invocation?.targetHit, false);
  assert.equal(invoked.replayStatus, 'confirmed');
  assert.equal(invoked.invocation?.targetHit, true);
});

test('rejects stale source identity before executing', async () => {
  const target = await statementTarget('choose', 'return 1;');

  const response = await replay({
    ...baseRequest('choose'),
    target: { ...target, sourceSha256: '0'.repeat(64) },
  });

  assert.equal(response.replayStatus, 'unmapped');
  assert.equal(response.reason, 'source-hash-mismatch');
  assert.equal(response.invocation, null);
});

function baseRequest(exportName: string, inputs = [numberValue(1)]): Record<string, unknown> {
  return {
    sourceRoots: [path.dirname(fixturePath)],
    entryPoint: {
      module: path.basename(fixturePath),
      exportName,
      executionKind: 'sync',
    },
    inputs,
    timeoutMillis: 10_000,
  };
}

async function statementTarget(functionName: string, text: string): Promise<StatementTarget> {
  const source = await readFile(fixturePath, 'utf8');
  const sourceFile = ts.createSourceFile(fixturePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const functions = sourceFile.statements.filter(ts.isFunctionDeclaration);
  const declaration = functions.find((candidate) => candidate.name?.text === functionName);
  assert.ok(declaration, `missing function ${functionName}`);
  let match: ts.Statement | undefined;
  const visit = (node: ts.Node): void => {
    if (ts.isStatement(node) && node.getText(sourceFile) === text) match = node;
    ts.forEachChild(node, visit);
  };
  visit(declaration);
  assert.ok(match, `missing statement ${text}`);
  const startOffset = match.getStart(sourceFile, false);
  const endOffset = match.getEnd();
  const start = sourceFile.getLineAndCharacterOfPosition(startOffset);
  const end = sourceFile.getLineAndCharacterOfPosition(endOffset);

  return {
    sourcePath: path.basename(fixturePath),
    sourceSha256: createHash('sha256').update(source, 'utf8').digest('hex'),
    startOffset,
    endOffset,
    start: { line: start.line, column: start.character },
    end: { line: end.line, column: end.character },
  };
}

function replay(request: Record<string, unknown>): Promise<ReplayResponse> {
  const child = spawn(process.execPath, [cliPath], { stdio: ['pipe', 'pipe', 'pipe'] });
  child.stdin.end(JSON.stringify(request));

  return new Promise((resolve, reject) => {
    let stdout = '';
    let stderr = '';
    child.stdout.setEncoding('utf8');
    child.stderr.setEncoding('utf8');
    child.stdout.on('data', (chunk: string) => { stdout += chunk; });
    child.stderr.on('data', (chunk: string) => { stderr += chunk; });
    child.once('error', reject);
    child.once('close', (exitCode) => {
      if (exitCode !== 0) {
        reject(new Error(`replay CLI failed with ${exitCode}: ${stderr}\n${stdout}`));
        return;
      }
      resolve(JSON.parse(stdout) as ReplayResponse);
    });
  });
}

function numberValue(value: number): Record<string, string> {
  const buffer = Buffer.allocUnsafe(8);
  buffer.writeDoubleBE(value);

  return { kind: 'number', value: 'finite', bits: buffer.toString('hex') };
}
