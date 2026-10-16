import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { executeProperty } from '../src/execute-property.js';
import { ProtocolError } from '../src/js-value.js';
import { validateInputExportRequest } from '../src/input-export.js';

const source = `export async function delayed(value: number): Promise<boolean> {
  await new Promise(resolve => setTimeout(resolve, 100)); return true;
}`;

function request(sourceRoot: string) {
  return {
    manifest: { propertyId: 'input.late', inputs: [{ name: 'value', domain: { kind: 'integer', min: 0, max: 1 } }],
      predicate: { module: 'properties.ts', exportName: 'delayed', executionKind: 'async' as const } },
    sourceRoots: [sourceRoot], seed: 37, numRuns: 1, timeoutMillis: 10, examples: [],
    inputExportRequest: { sources: [{ module: 'properties.ts', sha256: createHash('sha256').update(source).digest('hex') }],
      maxInvocations: 100, maxBytes: 65_536, maxArrayElements: 16 },
  };
}

test('a late asynchronous outcome cannot rewrite the already published input receipt', async () => {
  const directory = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-input-export-')));
  try {
    await writeFile(path.join(directory, 'properties.ts'), source);

    const result = (await executeProperty(request(directory))).result;
    const receipt = result.inputExport;

    assert.equal(receipt?.pendingInvocations, 1);
    assert.equal(receipt?.records[0]?.outcome, 'pending');
    await new Promise(resolve => setTimeout(resolve, 150));
    assert.equal(receipt?.pendingInvocations, 1);
    assert.equal(receipt?.records[0]?.outcome, 'pending');
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test('input export rejects invalid wire bounds and source identities before loading the property', async () => {
  const invalid = [
    { ...request('/missing').inputExportRequest, maxBytes: 64 * 1024 * 1024 + 1 },
    { ...request('/missing').inputExportRequest, maxInvocations: 100_001 },
    { ...request('/missing').inputExportRequest, sources: [null] },
    { ...request('/missing').inputExportRequest, sources: [{ module: 'other.ts', sha256: '0'.repeat(64) }] },
  ];
  for (const inputExportRequest of invalid) {
    await assert.rejects(executeProperty({ ...request('/missing'), inputExportRequest }),
      error => error instanceof ProtocolError && error.code === 'protocol.request.invalid');
  }
});

test('explicit large input export remains bounded without changing the ordinary byte allowance', () => {
  const ordinary = request('/unused');
  const large = { ...ordinary.inputExportRequest, maxBytes: 64 * 1024 * 1024 };

  assert.equal(validateInputExportRequest(large, ordinary.manifest).maxBytes, 64 * 1024 * 1024);
  assert.equal(validateInputExportRequest(ordinary.inputExportRequest, ordinary.manifest).maxBytes, 65_536);
});
