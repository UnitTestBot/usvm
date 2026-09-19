import assert from 'node:assert/strict';
import { mkdtemp, mkdir, realpath, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { ProtocolError } from '../src/js-value.js';
import { loadEntryPoint } from '../src/entry-point.js';

test('loads and invokes a TypeScript function directly from a source root', async () => {
  await withWorkspace(async (workspace) => {
    const sourceRoot = path.join(workspace, 'src');
    await mkdir(path.join(sourceRoot, 'properties'), { recursive: true });
    await writeFile(
      path.join(sourceRoot, 'properties', 'positive.ts'),
      'export function isPositive(value: number): boolean { return value > 0; }\n',
    );

    const loaded = await loadEntryPoint(
      {
        module: 'properties/positive.ts',
        exportName: 'isPositive',
        executionKind: 'sync',
      },
      [sourceRoot],
      'predicate',
    );

    assert.equal(loaded.invoke([1]), true);
    assert.equal(loaded.invoke([-1]), false);
  });
});

test('loads and invokes an asynchronous TypeScript function', async () => {
  await withWorkspace(async (workspace) => {
    const sourceRoot = path.join(workspace, 'src');
    await mkdir(sourceRoot);
    await writeFile(
      path.join(sourceRoot, 'async.ts'),
      'export async function predicate(value: boolean): Promise<boolean> { return value; }\n',
    );

    const loaded = await loadEntryPoint(
      { module: 'async.ts', exportName: 'predicate', executionKind: 'async' },
      [sourceRoot],
      'predicate',
    );

    assert.equal(await loaded.invoke([true]), true);
  });
});

test('rejects missing and ambiguous modules with distinct diagnostics', async () => {
  await withWorkspace(async (workspace) => {
    const firstRoot = path.join(workspace, 'first');
    const secondRoot = path.join(workspace, 'second');

    await mkdir(firstRoot);
    await mkdir(secondRoot);

    await assertProtocolError(
      loadEntryPoint(
        { module: 'missing.ts', exportName: 'predicate', executionKind: 'sync' },
        [firstRoot],
        'predicate',
      ),
      'entrypoint.module.not-found',
      'predicate.module',
    );

    await writeFile(path.join(firstRoot, 'duplicate.ts'), 'export function predicate() { return true; }\n');
    await writeFile(path.join(secondRoot, 'duplicate.ts'), 'export function predicate() { return true; }\n');

    await assertProtocolError(
      loadEntryPoint(
        { module: 'duplicate.ts', exportName: 'predicate', executionKind: 'sync' },
        [firstRoot, secondRoot],
        'predicate',
      ),
      'entrypoint.module.ambiguous',
      'predicate.module',
    );
  });
});

test('reports a candidate below a regular file as a missing module', async () => {
  await withWorkspace(async (workspace) => {
    const sourceRoot = path.join(workspace, 'src');
    await mkdir(sourceRoot);
    await writeFile(path.join(sourceRoot, 'file.ts'), 'export function predicate() { return true; }\n');

    await assertProtocolError(
      loadEntryPoint(
        { module: 'file.ts/nested.ts', exportName: 'predicate', executionKind: 'sync' },
        [sourceRoot],
        'predicate',
      ),
      'entrypoint.module.not-found',
      'predicate.module',
    );
  });
});

test('rejects a module whose symlink escapes its source root', async () => {
  await withWorkspace(async (workspace) => {
    const sourceRoot = path.join(workspace, 'src');
    const outside = path.join(workspace, 'outside.ts');

    await mkdir(sourceRoot);
    await writeFile(outside, 'export function predicate() { return true; }\n');
    await symlink(outside, path.join(sourceRoot, 'escape.ts'));

    await assertProtocolError(
      loadEntryPoint(
        { module: 'escape.ts', exportName: 'predicate', executionKind: 'sync' },
        [sourceRoot],
        'predicate',
      ),
      'entrypoint.module.outside-root',
      'predicate.module',
    );
  });
});

test('rejects missing and non-function exports', async () => {
  await withWorkspace(async (workspace) => {
    const sourceRoot = path.join(workspace, 'src');
    await mkdir(sourceRoot);
    await writeFile(path.join(sourceRoot, 'exports.ts'), 'export const value = 42;\n');

    await assertProtocolError(
      loadEntryPoint(
        { module: 'exports.ts', exportName: 'missing', executionKind: 'sync' },
        [sourceRoot],
        'predicate',
      ),
      'entrypoint.export.not-found',
      'predicate.exportName',
    );

    await assertProtocolError(
      loadEntryPoint(
        { module: 'exports.ts', exportName: 'value', executionKind: 'sync' },
        [sourceRoot],
        'predicate',
      ),
      'entrypoint.export.not-function',
      'predicate.exportName',
    );
  });
});

test('enforces declared execution kind and boolean results', async () => {
  await withWorkspace(async (workspace) => {
    const sourceRoot = path.join(workspace, 'src');
    await mkdir(sourceRoot);
    await writeFile(
      path.join(sourceRoot, 'contracts.ts'),
      [
        'export async function returnsPromise(): Promise<boolean> { return true; }',
        'export function returnsDirectly(): boolean { return true; }',
        'export function returnsNumber(): number { return 1; }',
      ].join('\n'),
    );

    const declaredSync = await loadEntryPoint(
      { module: 'contracts.ts', exportName: 'returnsPromise', executionKind: 'sync' },
      [sourceRoot],
      'predicate',
    );

    assert.throws(
      () => declaredSync.invoke([]),
      (error: unknown) => isProtocolError(error, 'entrypoint.execution-kind.mismatch'),
    );

    const declaredAsync = await loadEntryPoint(
      { module: 'contracts.ts', exportName: 'returnsDirectly', executionKind: 'async' },
      [sourceRoot],
      'predicate',
    );

    await assertProtocolError(
      Promise.resolve(declaredAsync.invoke([])),
      'entrypoint.execution-kind.mismatch',
      'predicate.executionKind',
    );

    const nonBoolean = await loadEntryPoint(
      { module: 'contracts.ts', exportName: 'returnsNumber', executionKind: 'sync' },
      [sourceRoot],
      'predicate',
    );

    assert.throws(
      () => nonBoolean.invoke([]),
      (error: unknown) => isProtocolError(error, 'entrypoint.result.invalid'),
    );
  });
});

async function withWorkspace(block: (workspace: string) => Promise<void>): Promise<void> {
  const workspace = await realpath(await mkdtemp(path.join(tmpdir(), 'usvm-entry-point-')));

  try {
    await block(workspace);
  } finally {
    await rm(workspace, { recursive: true, force: true });
  }
}

async function assertProtocolError(
  promise: Promise<unknown>,
  code: string,
  errorPath: string,
): Promise<void> {
  await assert.rejects(
    promise,
    (error: unknown) => isProtocolError(error, code) && error.path === errorPath,
  );
}

function isProtocolError(error: unknown, code: string): error is ProtocolError {
  return error instanceof ProtocolError && error.code === code;
}
