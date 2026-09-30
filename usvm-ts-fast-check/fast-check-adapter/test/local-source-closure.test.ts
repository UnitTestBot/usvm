import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, realpathSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'
import ts from 'typescript'
import { inspectLocalSourceClosure } from '../src/local-source-closure.js'

function workspace(files: Record<string, string>, action: (root: string) => void): void {
  const root = realpathSync(mkdtempSync(path.join(tmpdir(), 'usvm-closure-test-')))
  try {
    for (const [relative, source] of Object.entries(files)) {
      const file = path.join(root, relative)
      mkdirSync(path.dirname(file), { recursive: true })
      writeFileSync(file, source)
    }
    action(root)
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
}

test('local closure follows reexports while ignoring unrelated effects and type imports', () => {
  workspace({
    'entry.ts': "import { helper } from './barrel'; export function entry() { return helper(); }",
    'barrel.ts': "export { helper } from './nested/helper.js';",
    'nested/helper.ts': "import type { Missing } from 'external'; "
      + 'export function helper() { return 1; }',
    'unrelated.ts': "throw new Error('unrelated');",
  }, root => {
    assert.deepEqual(inspectLocalSourceClosure(root, path.join(root, 'entry.ts')), {
      files: ['barrel.ts', 'entry.ts', 'nested/helper.ts'],
    })
  })
})

for (const [name, entry, helper, expected] of [
  ['external import', "import { x } from 'node:fs';", '', 'IMPORTED_CALLEES_UNSUPPORTED'],
  ['missing module', "import { helper } from './missing';", '', 'IMPORTED_CALLEES_UNSUPPORTED'],
  ['missing export', "import { missing } from './helper';", 'export const helper = 1;', 'IMPORTED_CALLEES_UNSUPPORTED'],
  ['side effect dependency', "import './helper';", 'console.log(1);', 'TOP_LEVEL_INITIALIZATION_UNSUPPORTED'],
  ['top level call', 'const value = Math.random();', '', 'TOP_LEVEL_INITIALIZATION_UNSUPPORTED'],
  ['dynamic import', "export function load() { return import('./helper'); }", '', 'IMPORTED_CALLEES_UNSUPPORTED'],
  ['require', "export function load() { return require('./helper'); }", '', 'IMPORTED_CALLEES_UNSUPPORTED'],
] as const) {
  test(`local closure rejects ${name}`, () => {
    workspace({ 'entry.ts': entry, 'helper.ts': helper }, root => {
      assert.equal(inspectLocalSourceClosure(root, path.join(root, 'entry.ts')).reasonCode, expected)
    })
  })
}

for (const [name, declaration] of [
  ['constant', 'export declare const helper: () => number;'],
  ['function', 'export declare function helper(): number;'],
  ['class', 'export declare class helper { value(): number; }'],
] as const) {
  test(`local closure rejects ambient runtime ${name} exports`, () => {
    workspace({
      'entry.ts': "import { helper } from './helper'; export function entry() { return helper(); }",
      'helper.ts': declaration,
    }, root => {
      const result = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))
      assert.equal(result.reasonCode, 'IMPORTED_CALLEES_UNSUPPORTED')
      assert.match(result.diagnostic!, /Ambient runtime declaration/)
    })
  })
}

test('local closure rejects ambiguous extensionless imports', () => {
  workspace({
    'entry.ts': "import './helper';",
    'helper.ts': 'export const x = 1;',
    'helper/index.ts': 'export const x = 2;',
  }, root => {
    assert.equal(inspectLocalSourceClosure(root, path.join(root, 'entry.ts')).reasonCode, 'IMPORTED_CALLEES_UNSUPPORTED')
  })
})

test('local closure accepts a symlink alias for the root but rejects dependency symlinks', () => {
  workspace({ 'real/entry.ts': 'export function value() { return 1; }' }, root => {
    const alias = path.join(root, 'alias')
    symlinkSync(path.join(root, 'real'), alias)
    assert.deepEqual(inspectLocalSourceClosure(alias, path.join(alias, 'entry.ts')), { files: ['entry.ts'] })

    writeFileSync(path.join(root, 'entry.ts'), "import './link';")
    symlinkSync(path.join(root, 'real/entry.ts'), path.join(root, 'link.ts'))
    assert.equal(inspectLocalSourceClosure(root, path.join(root, 'entry.ts')).reasonCode, 'IMPORTED_CALLEES_UNSUPPORTED')
  })
})

test('local closure rejects a local export of an absent binding', () => {
  workspace({
    'entry.ts': "import { missing } from './barrel'; export function entry() { return missing(); }",
    'barrel.ts': 'export { missing };',
  }, root => {
    assert.equal(inspectLocalSourceClosure(root, path.join(root, 'entry.ts')).reasonCode, 'IMPORTED_CALLEES_UNSUPPORTED')
  })
})

test('local closure rejects package directory redirection before checking different source files', () => {
  workspace({
    'entry.ts': "import { helper } from './lib'; export function entry() { return helper(); }",
    'lib/index.ts': 'export const wrongExport = 1;',
    'lib/actual.ts': 'export function helper() { return 2; }',
    'lib/package.json': '{"main":"./actual.ts"}',
  }, root => {
    const result = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))
    assert.equal(result.reasonCode, 'IMPORTED_CALLEES_UNSUPPORTED')
    assert.match(result.diagnostic!, /Directory package resolution/)
  })
})

test('local closure rejects a cycle that would split mutable module state in replay', () => {
  workspace({
    'entry.ts': "import { readCount } from './helper'; export let count = 0; "
      + 'export function entry(value: number) { count = value; return readCount() === value; }',
    'helper.ts': "import { count } from './entry'; export function readCount() { return count; }",
  }, root => {
    const result = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))
    assert.equal(result.reasonCode, 'IMPORTED_CALLEES_UNSUPPORTED')
    assert.match(result.diagnostic!, /Cyclic local module/)
  })
})

test('binding inspection does not read ignored type imports outside the runtime closure', () => {
  workspace({
    'project/entry.ts': "import type { Outside } from '../outside'; import { helper } from './helper'; "
      + 'export function entry() { return helper(); }',
    'project/helper.ts': 'export function helper() { return 1; }',
    'outside.ts': 'export interface Outside { value: number }',
  }, root => {
    const originalRead = ts.sys.readFile
    const reads: string[] = []
    ts.sys.readFile = file => { reads.push(path.resolve(file)); return originalRead(file) }
    try {
      const project = path.join(root, 'project')
      assert.deepEqual(inspectLocalSourceClosure(project, path.join(project, 'entry.ts')), {
        files: ['entry.ts', 'helper.ts'],
      })
      assert.equal(reads.includes(path.join(root, 'outside.ts')), false)
    } finally {
      ts.sys.readFile = originalRead
    }
  })
})
