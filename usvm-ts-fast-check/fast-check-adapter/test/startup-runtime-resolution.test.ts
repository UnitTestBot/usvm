import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdirSync, mkdtempSync, realpathSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { FrozenModuleResolver } from '../src/frozen-module-resolution.js'

function workspace(files: Record<string, string>, action: (root: string, entry: string) => void): void {
  const root = realpathSync(mkdtempSync(path.join(tmpdir(), 'usvm-runtime-')))
  try {
    for (const [name, text] of Object.entries({ 'entry.ts': 'export class Entry {}', ...files })) {
      const file = path.join(root, name)
      mkdirSync(path.dirname(file), { recursive: true })
      writeFileSync(file, text)
    }
    action(root, path.join(root, 'entry.ts'))
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
}

test('startup chooses actual package runtime rather than its declaration and checked mode still rejects it', () => {
  workspace({
    'node_modules/example/package.json': JSON.stringify({ name: 'example', types: './types.d.ts',
      exports: { types: './types.d.ts', import: './runtime.mjs', require: './runtime.cjs' } }),
    'node_modules/example/types.d.ts': 'export declare const value: 1;',
    'node_modules/example/runtime.mjs': 'export const value = 2;',
    'node_modules/example/runtime.cjs': 'exports.value = 3;',
  }, (root, entry) => {
    const imported = new FrozenModuleResolver(root, entry)
    const required = new FrozenModuleResolver(root, entry)

    assert.deepEqual(imported.resolveStartup('example', entry),
      { kind: 'source', file: path.join(root, 'node_modules/example/runtime.mjs') })
    assert.deepEqual(required.resolveStartup('example', entry, 'require'),
      { kind: 'source', file: path.join(root, 'node_modules/example/runtime.cjs') })
    assert.throws(() => imported.resolve('example', entry), /Split import\/require/)
    const frozen = imported.freeze(['entry.ts', 'node_modules/example/runtime.mjs'])!
    assert.ok(frozen.inputSha256['node_modules/example/package.json'])
    assert.equal(frozen.inputSha256['node_modules/example/runtime.mjs'],
      createHash('sha256').update('export const value = 2;').digest('hex'))
    assert.equal(frozen.inputSha256['node_modules/example/types.d.ts'], undefined)
    assert.equal(JSON.parse(frozen.tsconfig).compilerOptions.allowJs, true)
  })
})

test('startup freezes JSON bytes and explicitly records native builtins without pretending they are source', () => {
  workspace({ 'data.json': '{"answer":42}' }, (root, entry) => {
    const resolver = new FrozenModuleResolver(root, entry)

    assert.equal(resolver.resolveStartup('./data.json', entry, 'require').kind, 'boundary')
    assert.equal(resolver.resolveStartup('node:fs', entry).kind, 'boundary')
    const frozen = resolver.freeze(['entry.ts'])!

    assert.equal(frozen.inputSha256['data.json'], createHash('sha256').update('{"answer":42}').digest('hex'))
    assert.deepEqual(frozen.runtimeBoundaries, [
      { sourceFile: 'entry.ts', specifier: './data.json', importKind: 'require', kind: 'json', runtimeFile: 'data.json' },
      { sourceFile: 'entry.ts', specifier: 'node:fs', importKind: 'import', kind: 'node-builtin' },
    ])
    assert.throws(() => resolver.resolveStartup('node:missing_builtin', entry), /Unresolved/)
  })
})

test('startup follows package export patterns and preserves exact exported runtime paths', () => {
  workspace({
    'node_modules/example/package.json': JSON.stringify({ name: 'example', exports: { './*': './lib/*.js' } }),
    'node_modules/example/lib/helper.js': 'export const value = 2;',
    'node_modules/example/lib/missing.ts': 'export const value = 3;',
  }, (root, entry) => {
    const resolver = new FrozenModuleResolver(root, entry)

    assert.deepEqual(resolver.resolveStartup('example/helper', entry),
      { kind: 'source', file: path.join(root, 'node_modules/example/lib/helper.js') })
    assert.throws(() => resolver.resolveStartup('example/missing', entry), /Missing acquired/)
  })
})

test('startup retains alias precedence and actual generated JavaScript bytes', () => {
  workspace({
    'tsconfig.json': JSON.stringify({ compilerOptions: { baseUrl: '.', paths: { example: ['./generated.js'] } } }),
    'generated.js': 'export const value = 5;',
    'rt.wat.js': 'export const value = 7;',
    'generated.ts': 'export const value = 6;',
  }, (root, entry) => {
    const resolver = new FrozenModuleResolver(root, entry)

    assert.deepEqual(resolver.resolveStartup('example', entry), { kind: 'source', file: path.join(root, 'generated.js') })
    assert.deepEqual(resolver.resolveStartup('./generated.js', entry), { kind: 'source', file: path.join(root, 'generated.js') })
    assert.deepEqual(resolver.resolveStartup('./rt.wat', entry, 'require'), { kind: 'source', file: path.join(root, 'rt.wat.js') })
    assert.throws(() => resolver.resolve('./generated.js', entry), /JavaScript runtime source is unsupported/)
  })
})

test('startup rejects declaration-only entries and dependencies escaping the acquired root', () => {
  workspace({
    'node_modules/example/package.json': '{"name":"example","main":"./types.d.ts"}',
    'node_modules/example/types.d.ts': 'export declare const value: 1;',
  }, (root, entry) => {
    const resolver = new FrozenModuleResolver(root, entry)
    symlinkSync(path.join(root, '..'), path.join(root, 'escape'))

    assert.throws(() => resolver.resolveStartup('example', entry), /declaration-only/)
    assert.throws(() => resolver.resolveStartup('../outside', entry), /Unresolved/)
    assert.throws(() => resolver.resolveStartup('./escape/anything.ts', entry), /Unresolved/)
  })
})
