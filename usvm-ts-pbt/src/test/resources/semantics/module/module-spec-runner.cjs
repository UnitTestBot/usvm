'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

function normalize(value) {
  if (value === undefined) return { kind: 'undefined' };
  if (typeof value === 'bigint') return { kind: 'bigint', value: value.toString() };
  if (typeof value === 'function') return { kind: 'callable', name: value.name, length: value.length };
  if (Array.isArray(value)) return value.map(normalize);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, normalize(item)]));
  }
  return value;
}

function classify(error) {
  if (error && error.name === 'SyntaxError') return 'link_error';
  return 'initialization_error';
}

async function main() {
  const contractPath = path.resolve(process.argv[2]);
  const resourceRoot = path.dirname(contractPath);
  const contract = JSON.parse(fs.readFileSync(contractPath, 'utf8'));
  const cases = [];

  for (const fixture of contract.cases) {
    globalThis.__usvmModuleSemanticsTrace = [];
    let record;
    try {
      const entryUrl = pathToFileURL(path.resolve(resourceRoot, fixture.entryModule));
      const namespace = await import(entryUrl.href);
      const result = Object.prototype.hasOwnProperty.call(namespace, 'result')
        ? normalize(namespace.result)
        : { kind: 'undefined' };
      record = {
        id: fixture.id,
        outcome: 'returned',
        result,
        trace: [...globalThis.__usvmModuleSemanticsTrace],
      };
    } catch (error) {
      record = {
        id: fixture.id,
        outcome: classify(error),
        result: null,
        trace: [...globalThis.__usvmModuleSemanticsTrace],
        errorName: error && error.name ? error.name : 'Error',
      };
    }
    cases.push(record);
  }

  process.stdout.write(JSON.stringify({
    protocolVersion: contract.nodeProtocolVersion,
    contractId: contract.contractId,
    cases,
  }));
}

main().catch((error) => {
  process.stderr.write(`${error && error.stack ? error.stack : error}\n`);
  process.exitCode = 1;
});
