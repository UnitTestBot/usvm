import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import type { Readable } from 'node:stream';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import type { ProtocolDiagnostic, TaggedJsValue } from '../src/js-value.js';

const cliPath = fileURLToPath(new URL('../src/projection-cli.js', import.meta.url));

interface SuccessResponse {
  protocolVersion: number;
  status: 'ok';
  samples: TaggedJsValue[][];
}

interface ErrorResponse {
  protocolVersion: number;
  status: 'error';
  diagnostics: ProtocolDiagnostic[];
}

type WireResponse = SuccessResponse | ErrorResponse;

interface InvocationResult {
  exitCode: number | null;
  stdout: string;
  stderr: string;
  response: WireResponse | undefined;
}

test('sample response returns deterministic tagged values', async () => {
  const request = {
    protocolVersion: 1,
    seed: 42,
    numSamples: 4,
    domains: [{ kind: 'integer', min: -1, max: 1 }],
  };

  const first = await invokeCli(JSON.stringify(request));
  const second = await invokeCli(JSON.stringify(request));
  const firstResponse = requireResponse(first);
  const secondResponse = requireResponse(second);

  assert.equal(first.exitCode, 0);
  assert.equal(first.stderr, '');
  assert.equal(first.stdout.trim().split('\n').length, 1);
  assert.deepEqual(firstResponse, secondResponse);
  assert.equal(firstResponse.protocolVersion, 1);
  assert.equal(firstResponse.status, 'ok');
  if (firstResponse.status !== 'ok') assert.fail('Expected a successful response');
  assert.equal(firstResponse.samples.length, 4);
  assert.ok(firstResponse.samples.every(
    (tuple) => tuple.length === 1 && tuple[0]?.kind === 'number',
  ));
});

interface ProtocolErrorCase {
  name: string;
  input: unknown;
  code: string;
  path: string;
}

const protocolErrorCases: ProtocolErrorCase[] = [
  {
    name: 'unsupported protocol version',
    input: {
      protocolVersion: 2,
      seed: 1,
      numSamples: 1,
      domains: [{ kind: 'boolean' }],
    },
    code: 'protocol.version.unsupported',
    path: 'protocolVersion',
  },
  {
    name: 'invalid request',
    input: {
      protocolVersion: 1,
      seed: 1.5,
      numSamples: 0,
      domains: [],
    },
    code: 'protocol.request.invalid',
    path: 'request',
  },
];

for (const { name, input, code, path } of protocolErrorCases) {
  test(`${name} returns a typed protocol error`, async () => {
    const result = await invokeCli(JSON.stringify(input));
    const response = requireResponse(result);
    assert.equal(result.exitCode, 0);
    assert.equal(response.status, 'error');
    if (response.status !== 'error') assert.fail('Expected an error response');
    assert.deepEqual(response, {
      protocolVersion: 1,
      status: 'error',
      diagnostics: [{
        code,
        message: response.diagnostics[0]?.message,
        path,
      }],
    });
  });
}

test('malformed JSON produces one clean protocol error document', async () => {
  const result = await invokeCli('{not-json');
  const response = requireResponse(result);

  assert.equal(result.exitCode, 0);
  assert.equal(result.stderr, '');
  assert.equal(result.stdout.trim().split('\n').length, 1);
  assert.equal(response.protocolVersion, 1);
  assert.equal(response.status, 'error');
  if (response.status !== 'error') assert.fail('Expected an error response');
  assert.equal(response.diagnostics[0]?.code, 'protocol.json.invalid');
});

async function invokeCli(input: string): Promise<InvocationResult> {
  const child = spawn(process.execPath, [cliPath], { stdio: ['pipe', 'pipe', 'pipe'] });
  child.stdin.end(input);
  const [exitCode, stdout, stderr] = await Promise.all([
    new Promise<number | null>((resolve, reject) => {
      child.once('error', reject);
      child.once('close', (code) => resolve(code));
    }),
    collect(child.stdout),
    collect(child.stderr),
  ]);
  let response: WireResponse | undefined;
  try {
    response = JSON.parse(stdout) as WireResponse;
  } catch {
    response = undefined;
  }
  return { exitCode, stdout, stderr, response };
}

async function collect(stream: Readable): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(Buffer.from(chunk));
  return Buffer.concat(chunks).toString('utf8');
}

function requireResponse(result: InvocationResult): WireResponse {
  assert.ok(result.response, `CLI did not return JSON: ${result.stdout}`);
  return result.response;
}
