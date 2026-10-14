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

test('local closure follows emitted imports and omits imports used only as types', () => {
  workspace({
    'entry.ts': "import { Nullish } from './types'; import { helper } from './helper'; "
      + 'export function entry(value: unknown): value is Nullish { return helper(value); }',
    'types.ts': 'declare const OPAQUE_TAG: unique symbol; export type Nullish = null | undefined;',
    'helper.ts': 'export function helper(value: unknown): boolean { return value == null; }',
  }, root => {
    assert.deepEqual(inspectLocalSourceClosure(root, path.join(root, 'entry.ts')), {
      files: ['entry.ts', 'helper.ts'],
    })
  })
})

for (const [name, entry, helper, expected] of [
  ['external import', "import { x } from 'node:fs';", '', 'IMPORTED_CALLEES_UNSUPPORTED'],
  ['missing module', "import { helper } from './missing';", '', 'IMPORTED_CALLEES_UNSUPPORTED'],
  ['missing export', "import { missing } from './helper';", 'export const helper = 1;', 'IMPORTED_CALLEES_UNSUPPORTED'],
  ['side effect dependency', "import './helper';", 'console.log(1);', 'TOP_LEVEL_INITIALIZATION_UNSUPPORTED'],
  ['top level call', "import './helper'; const value = Math.random();", '', 'TOP_LEVEL_INITIALIZATION_UNSUPPORTED'],
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

test('single-source captures retain their dedicated validation route', () => {
  workspace({
    'entry.ts': 'declare const unused: unknown; const source = { value: 7 }; const alias = source; '
      + 'export const entry = () => source === alias;',
  }, root => {
    assert.deepEqual(inspectLocalSourceClosure(root, path.join(root, 'entry.ts')), { files: ['entry.ts'] })
  })
})

test('imported class implements an erased interface without a runtime initialization effect', () => {
  workspace({
    'wrapper.ts': "import { HashMap } from './subject'; export function scenario() { return new HashMap<string, number>().size; }",
    'subject.ts': "import { MapContract } from './type-contract'; "
      + 'export class HashMap<K, V> implements MapContract<K, V> { size = 0; set(_key: K, _value: V) { this.size++; } }',
    'type-contract.ts': 'export interface MapContract<K, V> { size: number; set(key: K, value: V): void }',
  }, root => {
    assert.deepEqual(inspectLocalSourceClosure(root, path.join(root, 'wrapper.ts')), {
      files: ['subject.ts', 'wrapper.ts'],
    })
  })
})

for (const [name, declaration] of [
  ['effectful extends expression', 'function base() { throw new Error("must not execute"); } export class Subject extends base() {}'],
  ['plain runtime extends', 'class Base {} export class Subject extends Base {}'],
  ['decorated implements class', 'interface Contract {} function decorate(value: unknown) { return value; } @decorate export class Subject implements Contract {}'],
  ['static initializer with implements', 'interface Contract {} export class Subject implements Contract { static value = Math.random(); }'],
  ['static block with implements', 'interface Contract {} export class Subject implements Contract { static { throw new Error("must not execute"); } }'],
] as const) {
  test(`imported class retains module initialization rejection for ${name}`, () => {
    workspace({
      'wrapper.ts': "import { Subject } from './subject'; export function scenario() { return new Subject(); }",
      'subject.ts': declaration,
    }, root => {
      const result = inspectLocalSourceClosure(root, path.join(root, 'wrapper.ts'))
      assert.equal(result.reasonCode, 'TOP_LEVEL_INITIALIZATION_UNSUPPORTED')
      assert.match(result.diagnostic!, /subject\.ts/)
    })
  })
}


test('frozen aliases preserve inherited paths and compiler settings without importing unrelated files', () => {
  workspace({
    'configs/base.json': '{"compilerOptions":{"strict":true,"target":"ES2020","baseUrl":"..","paths":{"@local/*":["src/*"]}}}',
    'tsconfig.json': '{"extends":"./configs/base.json","include":["unrelated.ts"],"compilerOptions":{"module":"ESNext","moduleResolution":"Bundler"}}',
    'package.json': '{"name":"example","version":"1.0.0","type":"module"}',
    'pnpm-lock.yaml': 'lockfileVersion: 9.0\n',
    'src/entry.ts': "import { helper } from '@local/helper'; import type { Missing } from 'uninstalled'; export function entry() { return helper(); }",
    'src/helper.ts': 'export function helper() { return 7; }',
    'unrelated.ts': 'throw new Error("must not load");',
  }, root => {
    const closure = inspectLocalSourceClosure(root, path.join(root, 'src/entry.ts'))

    assert.equal(closure.reasonCode, undefined, closure.diagnostic)
    assert.deepEqual(closure.files, ['src/entry.ts', 'src/helper.ts'])
    assert.deepEqual(Object.keys(closure.resolution!.inputSha256).sort(),
      ['configs/base.json', 'package.json', 'pnpm-lock.yaml', 'src/entry.ts', 'src/helper.ts', 'tsconfig.json'])
    const frozenConfig = JSON.parse(closure.resolution!.tsconfig)
    assert.equal(frozenConfig.compilerOptions.strict, true)
    assert.equal(frozenConfig.compilerOptions.target, 'ES2020')
    assert.deepEqual(frozenConfig.compilerOptions.paths, { '@local/helper': ['src/helper.ts'] })
    assert.deepEqual(frozenConfig.files, closure.files)
  })
})

test('frozen package self reference selects executable export rather than its declaration', () => {
  workspace({
    'tsconfig.json': '{"compilerOptions":{"module":"ESNext","moduleResolution":"Bundler"}}',
    'package.json': '{"name":"source-package","version":"1.2.3","type":"module","exports":{"./helper":{"types":"./helper.d.ts","default":"./helper.ts"}}}',
    'package-lock.json': '{"lockfileVersion":3,"packages":{"":{"name":"source-package","version":"1.2.3"}}}',
    'entry.ts': "import { helper } from 'source-package/helper'; export function entry() { return helper(); }",
    'helper.d.ts': 'export declare function helper(): number;',
    'helper.ts': 'export function helper() { return 11; }',
  }, root => {
    const closure = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))

    assert.equal(closure.reasonCode, undefined, closure.diagnostic)
    assert.deepEqual(closure.files, ['entry.ts', 'helper.ts'])
    assert.equal('helper.d.ts' in closure.resolution!.inputSha256, false)
    assert.deepEqual(JSON.parse(closure.resolution!.tsconfig).compilerOptions.paths,
      { 'source-package/helper': ['helper.ts'] })
    const previous = closure.resolution!.inputSha256
    writeFileSync(path.join(root, 'helper.ts'), 'export function helper() { return 12; }')
    writeFileSync(path.join(root, 'package.json'), '{"name":"source-package","version":"1.2.3","exports":{"./helper":"./helper.ts"}}')
    writeFileSync(path.join(root, 'tsconfig.json'), '{"compilerOptions":{"strict":true,"module":"ESNext","moduleResolution":"Bundler"}}')
    const changed = inspectLocalSourceClosure(root, path.join(root, 'entry.ts')).resolution!.inputSha256
    for (const file of ['helper.ts', 'package.json', 'tsconfig.json']) assert.notEqual(previous[file], changed[file])
  })
})

test('frozen in-root workspace package symlink resolves to canonical project source', () => {
  workspace({
    'tsconfig.json': '{"compilerOptions":{"module":"ESNext","moduleResolution":"Bundler"}}',
    'package.json': '{"workspaces":["packages/*"]}',
    'pnpm-lock.yaml': 'lockfileVersion: 9.0\n',
    'entry.ts': "import { helper } from '@sample/helpers'; export function entry() { return helper(); }",
    'packages/helpers/package.json': '{"name":"@sample/helpers","version":"1.0.0","exports":"./index.ts"}',
    'packages/helpers/index.ts': 'export function helper() { return 13; }',
  }, root => {
    mkdirSync(path.join(root, 'node_modules/@sample'), { recursive: true })
    symlinkSync(path.join(root, 'packages/helpers'), path.join(root, 'node_modules/@sample/helpers'))
    const closure = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))

    assert.equal(closure.reasonCode, undefined, closure.diagnostic)
    assert.deepEqual(closure.files, ['entry.ts', 'packages/helpers/index.ts'])
    assert.equal('packages/helpers/package.json' in closure.resolution!.inputSha256, true)
  })
})

for (const [name, exports, helper] of [
  ['declaration-only package', '"./helper.d.ts"', 'export declare function helper(): number;'],
  ['JavaScript package', '"./helper.js"', 'export function helper() { return 1; }'],
  ['split import/require package', '{"import":"./helper.ts","require":"./other.ts"}', 'export function helper() { return 1; }'],
  ['import-only package', '{"import":"./helper.ts"}', 'export function helper() { return 1; }'],
] as const) {
  test(`frozen resolution rejects ${name}`, () => {
    workspace({
      'tsconfig.json': '{"compilerOptions":{"module":"NodeNext","moduleResolution":"NodeNext"}}',
      'package.json': `{"name":"example","exports":${exports}}`,
      'entry.ts': "import { helper } from 'example'; export function entry() { return helper(); }",
      'helper.ts': helper, 'helper.d.ts': helper, 'helper.js': helper,
      'other.ts': 'export function helper() { return 2; }',
    }, root => {
      const closure = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))

      assert.equal(closure.reasonCode, 'IMPORTED_CALLEES_UNSUPPORTED')
    })
  })
}


test('package imports cannot bypass the runtime export subset through TypeScript resolution', () => {
  workspace({
    'tsconfig.json': '{"compilerOptions":{"module":"NodeNext","moduleResolution":"NodeNext"}}',
    'package.json': '{"name":"example","type":"module","imports":{"#helper":{"import":"./helper.ts","require":"./other.ts"}}}',
    'entry.ts': "import { helper } from '#helper'; export function entry() { return helper(); }",
    'helper.ts': 'export function helper() { return 1; }',
    'other.ts': 'export function helper() { return 2; }',
  }, root => {
    const closure = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))

    assert.equal(closure.reasonCode, 'IMPORTED_CALLEES_UNSUPPORTED')
  })
})


test('paths preserve baseUrl fallback for bare imports that do not match an alias', () => {
  workspace({
    'tsconfig.json': '{"compilerOptions":{"baseUrl":"src","paths":{"@foo/*":["foo/*"]}}}',
    'src/entry.ts': "import { helper } from 'utils'; export function entry() { return helper(); }",
    'src/utils.ts': 'export function helper() { return 17; }',
  }, root => {
    const closure = inspectLocalSourceClosure(root, path.join(root, 'src/entry.ts'))

    assert.equal(closure.reasonCode, undefined, closure.diagnostic)
    assert.deepEqual(closure.files, ['src/entry.ts', 'src/utils.ts'])
    assert.deepEqual(JSON.parse(closure.resolution!.tsconfig).compilerOptions.paths,
      { utils: ['src/utils.ts'] })
  })
})

test('child baseUrl override also rebases inherited textual paths as TypeScript does', () => {
  workspace({
    'configs/base.json': '{"compilerOptions":{"baseUrl":"../old","paths":{"@foo/*":["foo/*"]}}}',
    'tsconfig.json': '{"extends":"./configs/base.json","compilerOptions":{"baseUrl":"new"}}',
    'entry.ts': "import { helper } from '@foo/helper'; export function entry() { return helper(); }",
    'old/foo/helper.ts': 'export function wrong() { return 1; }',
    'new/foo/helper.ts': 'export function helper() { return 19; }',
  }, root => {
    const config = ts.readConfigFile(path.join(root, 'tsconfig.json'), ts.sys.readFile)
    const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, root)
    const expected = ts.resolveModuleName('@foo/helper', path.join(root, 'entry.ts'), parsed.options, ts.sys).resolvedModule
    const closure = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))

    assert.equal(expected?.resolvedFileName, path.join(root, 'new/foo/helper.ts'))
    assert.equal(closure.reasonCode, undefined, closure.diagnostic)
    assert.deepEqual(closure.files, ['entry.ts', 'new/foo/helper.ts'])
  })
})

test('paths without baseUrl do not invent fallback resolution for unrelated bare imports', () => {
  workspace({
    'tsconfig.json': '{"compilerOptions":{"paths":{"@foo/*":["src/*"]}}}',
    'entry.ts': "import { helper } from 'utils'; export function entry() { return helper(); }",
    'utils.ts': 'export function helper() { return 23; }',
  }, root => {
    const closure = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))

    assert.equal(closure.reasonCode, 'IMPORTED_CALLEES_UNSUPPORTED')
  })
})

test('module closure attests nested literal allocations and const aliases across reexports', () => {
  workspace({
    'entry.ts': "import { alias } from './barrel'; const local = alias; "
      + 'export function entry() { return local; }',
    'barrel.ts': "export { alias } from './data';",
    'data.ts': 'const positive = 3; const count = -positive; const items = [count]; const box = { items, nested: [items, items] }; '
      + 'export const alias = box;',
  }, root => {
    assert.deepEqual(inspectLocalSourceClosure(root, path.join(root, 'entry.ts')), {
      files: ['barrel.ts', 'data.ts', 'entry.ts'],
    })
  })
})

for (const [name, initialization] of [
  ['temporal dead zone', 'const alias = items; const items = [];'],
  ['mutable binding alias', 'let items = []; const alias = items;'],
  ['self reference', 'const items = [items];'],
  ['spread effect', 'const items = [...external];'],
  ['array holes', 'const items = [, 1];'],
  ['computed property', 'const items = { [effect()]: 1 };'],
  ['getter', 'const items = { get value() { return 1; } };'],
  ['prototype override', 'const items = { "__proto__": {} };'],
  ['environment call in literal', 'const items = [Math.random()];'],
  ['object numeric coercion', 'const items = +{ valueOf: () => Math.random() };'],
  ['array numeric coercion', 'const items = +[{ toString: () => Date.now() }];'],
  ['cast object numeric coercion', 'const items = +({ valueOf: () => Math.random() } as unknown as number);'],
  ['aliased numeric coercion', 'const object = { valueOf: () => Math.random() }; const items = ~object;'],
  ['escaped prototype override', String.raw`const items = { \u005f\u005fproto__: {} };`],
  ['unknown imported alias', "import { absent } from './empty'; const items = [absent];"],
] as const) {
  test(`module initialization rejects ${name}`, () => {
    workspace({
      'entry.ts': "import './data'; export function entry() { return 1; }",
      'data.ts': initialization,
      'empty.ts': 'export {};',
    }, root => {
      const result = inspectLocalSourceClosure(root, path.join(root, 'entry.ts'))
      assert.equal(result.reasonCode, 'TOP_LEVEL_INITIALIZATION_UNSUPPORTED', result.diagnostic)
    })
  })
}
