import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { cp, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { convertV8IfBranches } from '../src/coverage-branches.js';

const adapterRoot = path.resolve(fileURLToPath(new URL('../', import.meta.url)), '..');
const c8 = path.join(adapterRoot, 'node_modules/c8/bin/c8.js');

async function runCoverage(source: string): Promise<{
  root: string;
  raw: string;
  sourcePath: string;
}> {
  const root = await mkdtemp(path.join(os.tmpdir(), 'usvm-branch-test-'));
  const sourcePath = path.join(root, 'subject.ts');
  const raw = path.join(root, 'raw');
  await writeFile(sourcePath, source);

  const result = spawnSync(process.execPath, [
    c8,
    '--reporter=json',
    `--reports-dir=${path.join(root, 'report')}`,
    `--temp-directory=${raw}`,
    '--allowExternal',
    '--exclude=__usvm_no_default_excludes__',
    process.execPath,
    '--import',
    'tsx',
    sourcePath,
  ], { cwd: adapterRoot, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);

  return { root, raw, sourcePath };
}

test('real c8 and tsx preserve true, false, mixed, and nested TypeScript arms', async () => {
  for (const [calls, expected] of [
    ['nested(20);', [[1, 0], [1, 0]]],
    ['nested(-1);', [[0, 1], [0, 0]]],
    ['nested(20); nested(5); nested(-1);', [[2, 1], [1, 1]]],
  ] as const) {
    const fixture = await runCoverage(`
export function nested(value: number): string {
  if (value > 0) {
    if (value > 10) {
      return 'large';
    } else {
      return 'small';
    }
  } else {
    return 'negative';
  }
}
${calls}
`);

    try {
      const report = await convertV8IfBranches(fixture.raw, [fixture.root]);
      const branches = report.files[0]?.branches ?? [];

      assert.deepEqual(branches.map(branch => branch.arms.map(arm => arm.hits)), expected);
      assert.deepEqual(branches.map(branch => branch.location.start.line), [3, 4]);
      assert.deepEqual(report.diagnostics, []);
    } finally {
      await rm(fixture.root, { recursive: true, force: true });
    }
  }
});

test('if without else observes an actual false successor and rejects throwing conditions', async () => {
  const fixture = await runCoverage(`
function gate(value: number): boolean {
  if (value < 0) throw new Error('guard failed');
  return value > 0;
}
function subject(value: number): number {
  if (gate(value)) {
    return 1;
  }
  return 0;
}
for (const value of [-1, 1]) {
  try { subject(value); } catch { /* expected */ }
}
`);

  try {
    const report = await convertV8IfBranches(fixture.raw, [fixture.root]);

    assert.equal(report.files[0]?.branches.some(branch => branch.location.start.line === 7), false);
    assert.equal(report.diagnostics.some(diagnostic => diagnostic.code === 'coverage.branch.ambiguous'), true);
  } finally {
    await rm(fixture.root, { recursive: true, force: true });
  }
});

test('if without else maps both arms when the true arm terminates', async () => {
  for (const [calls, expected] of [
    ['subject(1);', [1, 0]],
    ['subject(-1);', [0, 1]],
    ['subject(1); subject(-1);', [1, 1]],
  ] as const) {
    const fixture = await runCoverage(`
function subject(value: number): number {
  if (value > 0) {
    return 1;
  }
  return 0;
}
${calls}
`);

    try {
      const report = await convertV8IfBranches(fixture.raw, [fixture.root]);

      assert.deepEqual(report.files[0]?.branches[0]?.arms.map(arm => arm.hits), expected);
      assert.deepEqual(report.diagnostics, []);
    } finally {
      await rm(fixture.root, { recursive: true, force: true });
    }
  }
});

test('duplicate V8 scripts remain ambiguous', async () => {
  const fixture = await runCoverage(`
function subject(value: number): number {
  if (value > 0) return 1;
  else return 0;
}
subject(1);
`);

  try {
    const reports = (await readdir(fixture.raw)).filter(name => name.endsWith('.json'));
    let scriptReport: string | undefined;
    for (const name of reports) {
      const report = JSON.parse(await readFile(path.join(fixture.raw, name), 'utf8')) as {
        result?: { url: string }[];
      };

      if (report.result?.some(script => script.url.endsWith('/subject.ts'))) {
        scriptReport = name;
        break;
      }
    }
    assert.ok(scriptReport);
    await cp(path.join(fixture.raw, scriptReport), path.join(fixture.raw, 'duplicate.json'));

    const duplicate = await convertV8IfBranches(fixture.raw, [fixture.root]);

    assert.deepEqual(duplicate.files, []);
    assert.equal(duplicate.diagnostics[0]?.code, 'coverage.branch.ambiguous');
  } finally {
    await rm(fixture.root, { recursive: true, force: true });
  }
});

test('unmapped source-map positions do not produce branch counts', async () => {
  const fixture = await runCoverage(`
function subject(value: number): number {
  if (value > 0) return 1;
  else return 0;
}
subject(1);
`);

  try {
    let changed = 0;
    for (const name of await readdir(fixture.raw)) {
      if (!name.endsWith('.json')) continue;

      const reportPath = path.join(fixture.raw, name);
      const report = JSON.parse(await readFile(reportPath, 'utf8')) as {
        'source-map-cache'?: Record<string, { data: { mappings: string } }>;
      };
      const entry = Object.entries(report['source-map-cache'] ?? {})
        .find(([url]) => url.endsWith('/subject.ts'))?.[1];
      if (entry === undefined) continue;

      entry.data.mappings = '';
      await writeFile(reportPath, JSON.stringify(report));
      changed += 1;
    }
    assert.ok(changed > 0);

    const converted = await convertV8IfBranches(fixture.raw, [fixture.root]);

    assert.deepEqual(converted.files[0]?.branches, []);
    assert.equal(converted.diagnostics[0]?.code, 'coverage.branch.ambiguous');
  } finally {
    await rm(fixture.root, { recursive: true, force: true });
  }
});

test('function, script, switch, conditional, and logical ranges are not if arms', async () => {
  const fixture = await runCoverage(`
function subject(value: number): boolean {
  switch (value) {
    case 1: return value > 0 && value < 2;
    default: return value === 0 ? true : false;
  }
}
subject(1);
`);

  try {
    const converted = await convertV8IfBranches(fixture.raw, [fixture.root]);

    assert.deepEqual(converted.files[0]?.branches, []);
  } finally {
    await rm(fixture.root, { recursive: true, force: true });
  }
});

test('if ranges inside loops are explicitly unsupported', async () => {
  const fixture = await runCoverage(`
function subject(value: number): number {
  for (let index = 0; index < 2; index += 1) {
    if (value > index) return index;
    else value += 1;
  }
  return -1;
}
subject(1);
`);

  try {
    const converted = await convertV8IfBranches(fixture.raw, [fixture.root]);

    assert.deepEqual(converted.files[0]?.branches, []);
    assert.equal(converted.diagnostics[0]?.code, 'coverage.branch.unsupported');
  } finally {
    await rm(fixture.root, { recursive: true, force: true });
  }
});

test('early exits and UTF-16 columns retain exact source positions', async () => {
  const fixture = await runCoverage(`
function subject(value: number): number {
  const marker = '😀'; if (value < 0) return -1;
  if (value > 0) return marker.length;
  return 0;
}
subject(-1);
subject(1);
`);

  try {
    const converted = await convertV8IfBranches(fixture.raw, [fixture.root]);
    const branches = converted.files[0]?.branches ?? [];

    assert.deepEqual(branches.map(branch => branch.arms.map(arm => arm.hits)), [[1, 1], [1, 0]]);
    assert.equal(branches[0]?.location.start.column, 27);
    assert.deepEqual(converted.diagnostics, []);
  } finally {
    await rm(fixture.root, { recursive: true, force: true });
  }
});
