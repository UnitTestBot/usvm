import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, readdir, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import type { Readable } from 'node:stream';
import { fileURLToPath, pathToFileURL } from 'node:url';
import test from 'node:test';
import type { ProtocolDiagnostic } from '../src/js-value.js';
import { convertV8IfBranches } from '../src/coverage-branches.js';

const cliPath = fileURLToPath(new URL('../src/execution-cli.js', import.meta.url));
const coverageLauncher = fileURLToPath(new URL('../src/coverage-launcher.js', import.meta.url));

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
    assert.equal((response.result as Record<string, unknown>).runtimeVersion, process.version);
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

test('execution CLI keeps one JSON response after a predicate adds inherited toJSON', async () => {
  const sourceRoot = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-execution-cli-')));
  try {
    await writeFile(sourceRoot + '/property.ts', `
export function predicate(value: boolean) {
  Object.prototype.toJSON = () => { throw new Error('sentinel toJSON'); };
  Object.defineProperty(process, 'version', { value: 'changed-by-property' });
  return value;
}
`);

    const invocation = await invokeCli(JSON.stringify(executionRequest(sourceRoot)));
    const response = JSON.parse(invocation.stdout) as Record<string, unknown>;

    assert.equal(invocation.exitCode, 0);
    assert.equal(invocation.stdout.trim().split('\n').length, 1);
    assert.equal(response.status, 'ok');
    assert.equal((response.result as Record<string, unknown>).runtimeVersion, process.version);
  } finally {
    await rm(sourceRoot, { recursive: true, force: true });
  }
});

for (const installSerializer of [false, true]) {
  test(`execution coverage preserves imported source maps with prototype serializers=${installSerializer}`, async () => {
    const sourceRoot = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-execution-coverage-')));
    const raw = path.join(sourceRoot, 'raw');
    const helperPath = path.join(sourceRoot, 'helper.ts');
    const propertyPath = path.join(sourceRoot, 'property.ts');
    try {
      await writeFile(helperPath, `
export function helper(value: boolean): boolean {
  ${installSerializer ? `
  for (const prototype of [Object.prototype, Array.prototype]) {
    Object.defineProperty(prototype, 'toJSON', {
      get() { throw new Error('Inherited serializer getter must not run'); },
    });
  }` : ''}
  if (value) { return true; }
  else { return false; }
}
`);
      await writeFile(propertyPath, `
import { helper } from './helper';
export function predicate(value: boolean): boolean { return helper(value); }
`);

      const invocation = await invokeCli(JSON.stringify(executionRequest(sourceRoot)), {
        coverageDirectory: raw,
        timeoutMillis: 10_000,
      });
      assert.equal(invocation.timedOut, false);
      assert.equal(invocation.exitCode, 0, invocation.stderr);
      assert.equal(invocation.stderr, '');

      const response = JSON.parse(invocation.stdout) as Record<string, unknown>;
      const reports = await Promise.all((await readdir(raw)).filter(name => name.endsWith('.json'))
        .map(async name => JSON.parse(await readFile(path.join(raw, name), 'utf8')) as {
          result: { url: string; functions: {
            functionName: string;
            ranges: { startOffset: number; endOffset: number; count: number }[];
          }[] }[];
          'source-map-cache'?: Record<string, {
            data: { sources: string[]; sourcesContent?: (string | null)[] };
            lineLengths: number[];
          }>;
        }));

      assert.equal(response.status, 'ok');
      for (const [sourcePath, functionName] of [[propertyPath, 'predicate'], [helperPath, 'helper']] as const) {
        const url = pathToFileURL(sourcePath).href;
        const scripts = reports.flatMap(report => report.result.filter(script => script.url === url));
        assert.equal(scripts.length, 1, `Expected exactly one coverage script for ${url}`);
        // tsx gives an export getter the same name as the function. Select the
        // larger body range so the assertion checks the actual invocation.
        const namedFunctions = scripts[0]?.functions.filter(fn => fn.functionName === functionName) ?? [];
        const invokedFunction = namedFunctions.sort((left, right) => {
          const leftRange = left.ranges[0];
          const rightRange = right.ranges[0];
          assert.ok(leftRange);
          assert.ok(rightRange);

          return rightRange.endOffset - rightRange.startOffset - (leftRange.endOffset - leftRange.startOffset);
        })[0];
        assert.equal(invokedFunction?.ranges[0]?.count, 1);

        const maps = reports.flatMap(report => report['source-map-cache']?.[url] ?? []);
        assert.equal(maps.length, 1, `Expected exactly one source map for ${url}`);
        assert.deepEqual(maps[0]?.data.sources, [url]);
        assert.deepEqual(maps[0]?.data.sourcesContent, [await readFile(sourcePath, 'utf8')]);
        assert.ok((maps[0]?.lineLengths.length ?? 0) > 0);
      }

      const branches = await convertV8IfBranches(raw, [sourceRoot]);
      const helper = branches.files.find(file => file.path === helperPath);
      assert.deepEqual(helper?.branches.map(branch => branch.arms.map(arm => arm.hits)), [[1, 0]]);
      assert.deepEqual(branches.diagnostics, []);

      await writeFile(helperPath, `${await readFile(helperPath, 'utf8')}\n// Changed after execution.\n`);
      const changed = await convertV8IfBranches(raw, [sourceRoot]);

      assert.deepEqual(changed.files.find(file => file.path === helperPath)?.branches, []);
      assert.equal(changed.diagnostics.some(entry => entry.path === helperPath &&
        entry.code === 'coverage.branch.unsupported'), true);
    } finally {
      await rm(sourceRoot, { recursive: true, force: true });
    }
  });
}

test('execution coverage retains user worker and subprocess counters without duplicating main', async () => {
  const sourceRoot = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-descendant-coverage-')));
  const raw = path.join(sourceRoot, 'raw');
  const descendantPath = path.join(sourceRoot, 'descendant.cjs');
  const propertyPath = path.join(sourceRoot, 'property.ts');
  try {
    await writeFile(descendantPath, 'function descendant() { return 1; } descendant();\n');
    await writeFile(propertyPath, `
import { Worker } from 'node:worker_threads';
import { spawn } from 'node:child_process';
export async function predicate(value: boolean): Promise<boolean> {
  await new Promise<void>((resolve, reject) => {
    const worker = new Worker(${JSON.stringify(descendantPath)});
    worker.once('error', reject);
    worker.once('exit', code => code === 0 ? resolve() : reject(new Error('worker failed')));
  });
  await new Promise<void>((resolve, reject) => {
    const child = spawn(process.execPath, [${JSON.stringify(descendantPath)}], { stdio: 'ignore' });
    child.once('error', reject);
    child.once('close', code => code === 0 ? resolve() : reject(new Error('subprocess failed')));
  });
  return value;
}
`);
    const request = executionRequest(sourceRoot);
    const manifest = request.manifest as { predicate: { executionKind: string } };
    manifest.predicate.executionKind = 'async';
    request.timeoutMillis = 5_000;

    const invocation = await invokeCli(JSON.stringify(request), {
      coverageDirectory: raw,
      timeoutMillis: 10_000,
    });

    assert.equal(invocation.timedOut, false);
    assert.equal(invocation.exitCode, 0, invocation.stderr);
    assert.equal(invocation.stderr, '');
    assert.equal((JSON.parse(invocation.stdout) as { status: string }).status, 'ok');
    const reports = await Promise.all((await readdir(raw)).filter(name => name.endsWith('.json'))
      .map(async name => JSON.parse(await readFile(path.join(raw, name), 'utf8')) as {
        result: { url: string; functions: { functionName: string; ranges: { count: number }[] }[] }[];
      }));
    const descendantUrl = pathToFileURL(descendantPath).href;
    const descendants = reports.flatMap(report => report.result.filter(script => script.url === descendantUrl));
    assert.equal(descendants.length, 2);
    assert.deepEqual(descendants.map(script => script.functions.find(fn => fn.functionName === 'descendant')
      ?.ranges[0]?.count), [1, 1]);
    const propertyUrl = pathToFileURL(propertyPath).href;
    assert.equal(reports.flatMap(report => report.result.filter(script => script.url === propertyUrl)).length, 1);
  } finally {
    await rm(sourceRoot, { recursive: true, force: true });
  }
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

async function invokeCli(input: string, options: {
  timeoutMillis?: number;
  coverageDirectory?: string;
} = {}): Promise<CliInvocation> {
  const args = options.coverageDirectory === undefined ? [cliPath] : [coverageLauncher, cliPath];
  const environment = options.coverageDirectory === undefined ? process.env
    : { ...process.env, NODE_V8_COVERAGE: options.coverageDirectory };
  const child = spawn(process.execPath, args, {
    stdio: ['pipe', 'pipe', 'pipe'],
    env: environment,
  });

  let timedOut = false;
  const timeout = setTimeout(() => {
    timedOut = true;
    child.kill();
  }, options.timeoutMillis ?? 3_000);

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
