import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const cliPath = fileURLToPath(new URL('../src/projection-cli.mjs', import.meta.url));

test('sample response echoes request identity and returns deterministic tagged values', async () => {
  const request = {
    protocolVersion: 1,
    requestId: 'sample-1',
    operation: 'sample',
    seed: 42,
    numSamples: 4,
    domains: [{ kind: 'integer', min: -1, max: 1 }],
  };

  const first = await invokeCli(JSON.stringify(request));
  const second = await invokeCli(JSON.stringify(request));

  assert.equal(first.exitCode, 0);
  assert.equal(first.stderr, '');
  assert.equal(first.stdout.trim().split('\n').length, 1);
  assert.deepEqual(first.response, second.response);
  assert.equal(first.response.protocolVersion, 1);
  assert.equal(first.response.requestId, 'sample-1');
  assert.equal(first.response.status, 'ok');
  assert.equal(first.response.samples.length, 4);
  assert.ok(first.response.samples.every((tuple) => tuple.length === 1 && tuple[0].kind === 'number'));
});

for (const [name, input, code, requestId, path] of [
  [
    'unsupported protocol version',
    { protocolVersion: 2, requestId: 'wrong-version', operation: 'sample', seed: 1, numSamples: 1, domains: [{ kind: 'boolean' }] },
    'protocol.version.unsupported',
    'wrong-version',
    'protocolVersion',
  ],
  [
    'unsupported operation',
    { protocolVersion: 1, requestId: 'wrong-operation', operation: 'check', seed: 1, numSamples: 1, domains: [{ kind: 'boolean' }] },
    'protocol.operation.unsupported',
    'wrong-operation',
    'operation',
  ],
  [
    'invalid request',
    { protocolVersion: 1, requestId: '', operation: 'sample', seed: 1.5, numSamples: 0, domains: [] },
    'protocol.request.invalid',
    '',
    'request',
  ],
]) {
  test(`${name} returns a typed protocol error`, async () => {
    const result = await invokeCli(JSON.stringify(input));
    assert.equal(result.exitCode, 0);
    assert.deepEqual(result.response, {
      protocolVersion: 1,
      requestId,
      status: 'error',
      diagnostics: [{
        code,
        message: result.response.diagnostics[0].message,
        path,
      }],
    });
  });
}

test('malformed JSON produces one clean protocol error document', async () => {
  const result = await invokeCli('{not-json');

  assert.equal(result.exitCode, 0);
  assert.equal(result.stderr, '');
  assert.equal(result.stdout.trim().split('\n').length, 1);
  assert.equal(result.response.protocolVersion, 1);
  assert.equal(result.response.status, 'error');
  assert.equal(result.response.diagnostics[0].code, 'protocol.json.invalid');
  assert.ok(!('requestId' in result.response));
});

async function invokeCli(input) {
  const child = spawn(process.execPath, [cliPath], { stdio: ['pipe', 'pipe', 'pipe'] });
  child.stdin.end(input);
  const [exitCode, stdout, stderr] = await Promise.all([
    new Promise((resolve, reject) => {
      child.once('error', reject);
      child.once('close', resolve);
    }),
    collect(child.stdout),
    collect(child.stderr),
  ]);
  let response;
  try {
    response = JSON.parse(stdout);
  } catch {
    response = undefined;
  }
  return { exitCode, stdout, stderr, response };
}

async function collect(stream) {
  const chunks = [];
  for await (const chunk of stream) chunks.push(chunk);
  return Buffer.concat(chunks).toString('utf8');
}
