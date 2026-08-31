import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import type { Readable } from 'node:stream';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import type { ProtocolDiagnostic } from '../src/js-value.js';

const cliPath = fileURLToPath(new URL('../src/execution-cli.js', import.meta.url));

test('execution CLI emits one successful response document', async () => {
  const sourceRoot = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-execution-cli-')));
  try {
    await writeFile(sourceRoot + '/property.ts', 'export function predicate(value: boolean) { return value; }\n');

    const invocation = await invokeCli(JSON.stringify(executionRequest(sourceRoot)));
    const response = JSON.parse(invocation.stdout) as Record<string, unknown>;

    assert.equal(invocation.timedOut, false);
    assert.equal(invocation.exitCode, 0);
    assert.equal(invocation.stderr, '');
    assert.equal(invocation.stdout.trim().split('\n').length, 1);
    assert.equal(response.status, 'ok');
    assert.equal('protocolVersion' in response, false);
  } finally {
    await rm(sourceRoot, { recursive: true, force: true });
  }
});

test('execution CLI reports malformed JSON without crashing', async () => {
  const invocation = await invokeCli('{not-json');
  const response = JSON.parse(invocation.stdout) as ExecutionErrorResponse;

  assert.equal(invocation.exitCode, 0);
  assert.equal(invocation.stderr, '');
  assert.equal(response.status, 'error');
  assert.equal(response.diagnostics[0]?.kind, 'invalid-request');
  assert.equal(response.diagnostics[0]?.code, 'protocol.json.invalid');
});

test('execution CLI keeps user logging outside the protocol response', async () => {
  const sourceRoot = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-execution-cli-')));
  try {
    await writeFile(
      sourceRoot + '/property.ts',
      "console.log('module log');\n"
        + "export function predicate(value: boolean) { console.log('predicate log'); return value; }\n",
    );

    const invocation = await invokeCli(JSON.stringify(executionRequest(sourceRoot)));

    assert.equal(invocation.timedOut, false);
    assert.equal(invocation.exitCode, 0);
    assert.equal(invocation.stdout.trim().split('\n').length, 1);
    assert.equal((JSON.parse(invocation.stdout) as Record<string, unknown>).status, 'ok');
    assert.match(invocation.stderr, /module log/);
    assert.match(invocation.stderr, /predicate log/);
  } finally {
    await rm(sourceRoot, { recursive: true, force: true });
  }
});

test('execution CLI exits after writing a response when user code leaves an open handle', async () => {
  const sourceRoot = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-execution-cli-')));
  try {
    await writeFile(
      sourceRoot + '/property.ts',
      'export function predicate(value: boolean) { setInterval(() => undefined, 1_000); return value; }\n',
    );

    const invocation = await invokeCli(JSON.stringify(executionRequest(sourceRoot)));

    assert.equal(invocation.timedOut, false);
    assert.equal(invocation.exitCode, 0);
    assert.equal((JSON.parse(invocation.stdout) as Record<string, unknown>).status, 'ok');
  } finally {
    await rm(sourceRoot, { recursive: true, force: true });
  }
});

for (const replayPath of ['garbage', '0:999999:0']) {
  test(`execution CLI reports replay path ${replayPath} as a typed protocol error`, async () => {
    const sourceRoot = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-execution-cli-')));
    try {
      await writeFile(sourceRoot + '/property.ts', 'export function predicate(value: boolean) { return value; }\n');
      const request = executionRequest(sourceRoot);
      request.replayPath = replayPath;

      const invocation = await invokeCli(JSON.stringify(request));
      const response = JSON.parse(invocation.stdout) as ExecutionErrorResponse;

      assert.equal(invocation.timedOut, false);
      assert.equal(invocation.exitCode, 0);
      assert.equal(invocation.stderr, '');
      assert.equal(response.status, 'error');
      assert.equal(response.diagnostics[0]?.kind, 'invalid-request');
      assert.equal(response.diagnostics[0]?.code, 'protocol.replay-path.invalid');
      assert.equal(response.diagnostics[0]?.path, 'replayPath');
    } finally {
      await rm(sourceRoot, { recursive: true, force: true });
    }
  });
}

interface ExecutionErrorResponse {
  status: string;
  diagnostics: ProtocolDiagnostic[];
}

interface CliInvocation {
  exitCode: number | null;
  stdout: string;
  stderr: string;
  timedOut: boolean;
}

async function invokeCli(input: string, timeoutMillis = 3_000): Promise<CliInvocation> {
  const child = spawn(process.execPath, [cliPath], { stdio: ['pipe', 'pipe', 'pipe'] });

  let timedOut = false;
  const timeout = setTimeout(() => {
    timedOut = true;
    child.kill();
  }, timeoutMillis);

  child.stdin.end(input);

  try {
    const [exitCode, stdout, stderr] = await Promise.all([
      new Promise<number | null>((resolve, reject) => {
        child.once('error', reject);
        child.once('close', (code) => resolve(code));
      }),
      collect(child.stdout),
      collect(child.stderr),
    ]);

    return { exitCode, stdout, stderr, timedOut };
  } finally {
    clearTimeout(timeout);
  }
}

async function collect(stream: Readable): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(Buffer.from(chunk));

  return Buffer.concat(chunks).toString('utf8');
}

function executionRequest(sourceRoot: string): Record<string, unknown> {
  return {
    manifest: {
      propertyId: 'example.cli',
      inputs: [{ name: 'value', domain: { kind: 'constant', value: { kind: 'boolean', value: true } } }],
      predicate: { module: 'property.ts', exportName: 'predicate', executionKind: 'sync' },
    },
    sourceRoots: [sourceRoot],
    seed: 42,
    numRuns: 1,
    timeoutMillis: 1_000,
    examples: [],
  };
}
