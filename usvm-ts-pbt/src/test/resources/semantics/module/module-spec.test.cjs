'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { spawnSync } = require('node:child_process');

test('Node ESM results and traces match module-semantics-v1', () => {
  const root = __dirname;
  const contractPath = path.join(root, 'module-semantics-v1.json');
  const contract = JSON.parse(fs.readFileSync(contractPath, 'utf8'));
  const run = spawnSync(process.execPath, [path.join(root, 'module-spec-runner.cjs'), contractPath], {
    encoding: 'utf8',
  });
  assert.equal(run.status, 0, run.stderr);

  const actual = JSON.parse(run.stdout);
  const expectedCases = contract.cases.map((fixture) => {
    const expected = {
      id: fixture.id,
      outcome: fixture.expected.outcome,
      result: fixture.expected.result,
      trace: fixture.expected.trace,
    };
    if (fixture.expected.errorName) expected.errorName = fixture.expected.errorName;
    return expected;
  });
  assert.deepEqual(actual, {
    protocolVersion: contract.nodeProtocolVersion,
    contractId: contract.contractId,
    cases: expectedCases,
  });
});
