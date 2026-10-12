import assert from 'node:assert/strict'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import test from 'node:test'
import { inspectSourceCaptures } from '../src/source-captures.js'

function inspect(source: string, names: string[]): ReturnType<typeof inspectSourceCaptures> {
  const directory = mkdtempSync(join(tmpdir(), 'usvm-source-captures-'))
  try {
    const path = join(directory, 'fixture.ts')
    writeFileSync(path, source)
    return inspectSourceCaptures(path, 'fixture', names)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
}

test('source constants named Infinity and NaN preserve their own numeric values', () => {
  const captures = inspect('const Infinity = 7; const NaN = 8; export const fixture = () => Infinity + NaN;', ['Infinity', 'NaN'])
  assert.deepEqual(captures.map(({ name, kind, value }) => ({ name, kind, value })), [
    { name: 'Infinity', kind: 'NUMBER', value: '7' },
    { name: 'NaN', kind: 'NUMBER', value: '8' },
  ])
})

test('genuine SDK globals and primitive globals have default-library provenance', () => {
  const captures = inspect('export const fixture = () => Math.floor(7) + Date.now() + Infinity + NaN;',
    ['Math', 'Date', 'Infinity', 'NaN'])
  assert.deepEqual(captures.map(({ name, kind, value }) => ({ name, kind, value })), [
    { name: 'Math', kind: 'BUILTIN', value: 'Math' },
    { name: 'Date', kind: 'BUILTIN', value: 'Date' },
    { name: 'Infinity', kind: 'NUMBER', value: 'Infinity' },
    { name: 'NaN', kind: 'NUMBER', value: 'NaN' },
  ])
})

test('project Math method keeps its source object and function rather than builtin identity', () => {
  const captures = inspect('const Math = { floor: (value: number) => 7 }; export const fixture = () => Math.floor(1);', ['Math'])
  assert.equal(captures[0]?.kind, 'OBJECT')
  assert.equal(captures[0]?.fields[0]?.name, 'floor')
  assert.equal(captures[0]?.fields[0]?.kind, 'FUNCTION')
})

test('immutable source aliases keep builtin provenance and negative zero payloads', () => {
  const captures = inspect('const clock = Date; const zero = -0; export const fixture = () => clock.now() + zero;', ['clock', 'zero'])
  assert.equal(captures[0]?.kind, 'BUILTIN')
  assert.equal(captures[0]?.value, 'Date')
  assert.equal(captures[1]?.value, '-0')
})

test('builtin value observations are explicit until real runtime values can be initialized', () => {
  const captures = inspect('export const fixture = () => typeof Date;', ['Date'])
  assert.equal(captures[0]?.kind, 'UNSUPPORTED')
})


test('object aliases retain canonical identity', () => {
  const captures = inspect('const object = { value: 7 }; const alias = object; export const fixture = () => object === alias;',
    ['object', 'alias'])
  assert.equal(captures[0]?.kind, 'OBJECT')
  assert.equal(captures[1]?.origin, captures[0]?.origin)
  assert.notEqual(captures[0]?.origin, null)
})

test('module mutations and escapes reject stale object reconstruction', () => {
  const mutated = inspect('const object = { value: 7 }; const alias = object; alias.value = 8; export const fixture = () => object.value;', ['object'])
  assert.equal(mutated[0]?.kind, 'UNSUPPORTED')

  const escaped = inspect('const object = { value: 7 }; consume(object); export const fixture = () => object.value;', ['object'])
  assert.equal(escaped[0]?.kind, 'UNSUPPORTED')
})
