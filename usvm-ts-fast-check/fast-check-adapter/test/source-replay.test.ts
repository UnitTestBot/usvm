import assert from 'node:assert/strict'
import test from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'
import { instrumentSourceReplay } from '../src/source-replay.js'
import type { SourceReplayTarget } from '../src/source-replay.js'

function replay(source: string, statement: string, mode: SourceReplayTarget['mode'], input: number,
  expression: string | null = null): { value: unknown; marker: boolean } {
  const file = ts.createSourceFile('fixture.ts', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  const start = source.indexOf(statement)
  const expressionStart = expression === null ? null : source.indexOf(expression, start)
  const result = instrumentSourceReplay(file, {
    mode, start, end: start + statement.length, marker: 'hit',
    expressionStart, expressionEnd: expressionStart === null ? null : expressionStart + expression!.length,
  })
  assert.equal(result.reason, null)
  assert.notEqual(result.source, null)
  const context = vm.createContext({ hit: false })
  const compiled = ts.transpileModule(result.source!, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
  vm.runInContext(compiled, context)
  const value = vm.runInContext(`fixture(${input})`, context)
  return { value, marker: vm.runInContext('hit', context) as boolean }
}

test('unbraced if entry keeps untaken and taken statements under their condition', () => {
  const source = 'function fixture(value: number) { if (value > 0) return 1; return 0; }'
  assert.deepEqual(replay(source, 'return 1;', 'ENTRY', 0), { value: 0, marker: false })
  assert.deepEqual(replay(source, 'return 1;', 'ENTRY', 1), { value: 1, marker: true })
})

test('labeled iteration statement retains its continue target', () => {
  const source = 'function fixture(value: number) { outer: for (let i = 0; i < value; i++) continue outer; return value; }'
  const target = 'for (let i = 0; i < value; i++) continue outer;'
  assert.deepEqual(replay(source, target, 'ENTRY', 2), { value: 2, marker: true })
})

test('completed return evaluates its operand once and preserves normal finally', () => {
  const source = `function fixture(value: number) {
    let calls = 0;
    const once = () => { calls++; return value; };
    try { return once(); } finally {
      while (true) { break; }
      try { throw 1; } catch {}
      if (calls !== 1) throw 2;
    }
  }`
  assert.deepEqual(replay(source, 'return once();', 'COMPLETED_RETURN', 7, 'once()'), { value: 7, marker: true })
})

test('finally competing return overrides pending completion', () => {
  const source = 'function fixture(value: number) { try { return value; } finally { return 2; } }'
  assert.deepEqual(replay(source, 'return value;', 'COMPLETED_RETURN', 1, 'value'), { value: 2, marker: false })
})

test('finally break cancels return before implicit completion', () => {
  const source = 'function fixture(value: number) { while (true) { try { return value; } finally { break; } } }'
  assert.deepEqual(replay(source, 'return value;', 'COMPLETED_RETURN', 1, 'value'), { value: undefined, marker: false })
})

test('finally escaping throw cancels return even if an outer catch resumes', () => {
  const source = 'function fixture(value: number) { try { try { return value; } finally { throw 2; } } catch {} }'
  assert.deepEqual(replay(source, 'return value;', 'COMPLETED_RETURN', 1, 'value'), { value: undefined, marker: false })
})

test('entry on a strict directive preserves directive semantics', () => {
  const source = `function fixture(value: number) { 'use strict'; return this === undefined; }`
  assert.deepEqual(replay(source, "'use strict';", 'ENTRY', 0), { value: true, marker: true })
})

test('completed expression arrow retains lexical this and arguments', () => {
  const source = 'function outer(value: number) { return () => arguments[0]; } const fixture = outer(7);'
  const target = 'arguments[0]'
  assert.deepEqual(replay(source, target, 'COMPLETED_RETURN', 1, target), { value: 7, marker: true })
})
