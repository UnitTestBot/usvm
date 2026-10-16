import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'
import { savedInputBindings } from '../src/saved-input-bindings.js'

const prepare = (source: string, moduleBindings: string[] = []) => savedInputBindings(
  ts.createSourceFile('Target.ts', source, ts.ScriptTarget.Latest, true),
  { exportName: '__savedBridge', classes: [{ name: 'Target', staticFields: ['marker'] }], moduleBindings },
)

test('private overlay bridges exact static data and mutable module bindings', () => {
  const source = 'export let count = 17; export class Target { static marker = 23; run() { return count; } }'

  const result = prepare(source, ['count'])

  assert.equal(result.reason, null)
  assert.ok(result.source?.startsWith(source))
  assert.match(result.source!, /Target\["marker"\] = __savedBridgeArguments\[2\]/)
  assert.match(result.source!, /count = __savedBridgeArguments\[2\]/)
})

test('constant, imported and nested bindings are explicit unsupported cases', () => {
  const declarations = [
    'export const count = 17;',
    'import { count } from "./other";',
    'function nested() { let count = 17; }',
  ]
  for (const declaration of declarations) {
    const result = prepare(declaration + ' export class Target { static marker = 23; }', ['count'])

    assert.equal(result.source, null)
    assert.match(result.reason!, /Mutable top-level module binding is unavailable/)
  }
})

test('private native slots, inherited classes, reassigned bindings and static accessors are rejected', () => {
  const sources = [
    'export class Target { #value = 1; static marker = 23; }',
    'export class Target extends Base { static marker = 23; }',
    'export class Target { static marker = 23; } Target = Other;',
    'export class Target { static get marker() { return 23; } }',
    'namespace N { export class Target { static marker = 23; } }',
  ]
  for (const source of sources) {
    const result = prepare(source)

    assert.equal(result.source, null)
    assert.ok(result.reason)
  }
})
