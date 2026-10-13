import assert from 'node:assert/strict'
import { test } from 'node:test'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import { instrumentClassCoverage } from '../src/class-coverage-source.js'

function instrument(source: string, className = 'Target') {
  const file = ts.createSourceFile('target.ts', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
  return instrumentClassCoverage(file, className)
}

function load(source: string, counter: string | null) {
  const exports: Record<string, unknown> = {}
  const sandbox: Record<string, unknown> = { exports, module: { exports } }
  if (counter !== null) sandbox[counter] = []
  const js = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS,
  } }).outputText
  runInNewContext(js, sandbox)
  return { sandbox, Target: exports['Target'] as new (...args: unknown[]) => Record<string, (...args: unknown[]) => unknown> }
}

test('freezes distinct original AST statements on one line and only native hits', () => {
  const source = 'export class Target { run(x: boolean) { if (x) return 1; return 2; } }'
  const result = instrument(source)
  assert.equal(result.reason, null)
  assert.equal(result.statements.length, 3)
  assert.equal(new Set(result.statements.map((statement) => statement.id)).size, 3)
  assert.deepEqual(result.statements.map((statement) => statement.kind), ['IfStatement', 'ReturnStatement', 'ReturnStatement'])

  const runtime = load(result.source!, result.counterGlobalName)
  const receiver = new runtime.Target()
  assert.equal(receiver['run']!(true), 1)
  const covered = runtime.sandbox[result.counterGlobalName] as string[]
  assert.deepEqual(covered, result.statements.slice(0, 2).map((statement) => statement.id))
  assert.equal(covered.includes(result.statements[2]!.id), false)
  assert.deepEqual(instrument(source), result)
})

test('counts initialization and reachable private bodies while excluding foreign and dead code', () => {
  const source = `
    interface TypeOnly { value: number }
    export class Foreign { run() { return 9; } }
    export class Target {
      value = 0;
      constructor(x: number) { this.value = this.helper(x); }
      run() { return this.value; }
      private helper(x: number) { if (x > 0) return x; return -x; }
      private dead() { return 99; }
    }
    export function wrapper() { return 100; }
  `
  const result = instrument(source)
  assert.equal(result.reason, null)
  assert.deepEqual(new Set(result.statements.map((statement) => statement.memberName)),
    new Set(['value', 'constructor', 'run', 'helper']))
  assert.equal(result.statements[0]!.kind, 'PropertyInitializer')

  const original = load(source, null)
  const instrumented = load(result.source!, result.counterGlobalName)
  assert.equal(new original.Target(-2)['run']!(), new instrumented.Target(-2)['run']!())
  const covered = instrumented.sandbox[result.counterGlobalName] as string[]
  assert.equal(covered.length, 5)
  assert.equal(covered.every((id) => result.statements.some((statement) => statement.id === id)), true)
})

test('preserves labelled loops, unbraced nested controls and else binding', () => {
  const source = `export class Target { run(x: number) {
    let value = 0;
    outer: for (let i = 0; i < 4; i++) {
      if (i === x) continue outer;
      if (i < x) if (x > 1) value += i; else value -= i;
    }
    return value;
  } }`
  const result = instrument(source)
  assert.equal(result.reason, null)
  const original = load(source, null)
  const instrumented = load(result.source!, result.counterGlobalName)

  for (const value of [-1, 0, 1, 2, 3, 8]) {
    assert.equal(new instrumented.Target()['run']!(value), new original.Target()['run']!(value))
  }
  assert.equal(result.statements.some((statement) => statement.kind === 'ContinueStatement'), true)
  assert.equal(result.statements.some((statement) => statement.kind === 'LabeledStatement'), false)
})

test('supports private accessors and expression callable properties', () => {
  const result = instrument(`export class Target {
    private value = 2;
    private get doubled() { return this.value * 2; }
    run = () => this.doubled;
  }`)
  assert.equal(result.reason, null)
  assert.deepEqual(result.statements.map((statement) => statement.kind),
    ['PropertyInitializer', 'ReturnStatement', 'CallableExpressionBody'])
  const runtime = load(result.source!, result.counterGlobalName)
  assert.equal(new runtime.Target()['run']!(), 4)
  assert.equal((runtime.sandbox[result.counterGlobalName] as string[]).length, 3)
})

test('retains explicit unsupported dynamic reachability and closure outcomes', () => {
  assert.match(instrument('export class Target { run(name: string) { return this[name]; } }').reason!, /Computed receiver/)
  assert.match(instrument('export class Target { run() { return [1].map(x => x + 1); } }').reason!, /Nested closures/)
  assert.match(instrument('export class Target { run() { const alias = this; return alias.hidden(); } private hidden() { return 1; } }').reason!, /Aliased or escaping/)
  assert.match(instrument('export class Target { run() { return eval("this.hidden()"); } private hidden() { return 1; } }').reason!, /Dynamic evaluation/)
  assert.match(instrument('export class Target extends Base { run() { return 1; } }').reason!, /Inherited/)
  assert.match(instrument('export class Target { static value = 1; static run() { return 1; } }').reason!, /Static/)
  assert.match(instrument('export class Target { run(globalThis: unknown) { return 1; } }').reason!, /globalThis binding/)
})


test('includes static private helpers reachable through the class or static receiver', () => {
  const source = `export class Target {
    static run(x: number) { return this.helper(x) + Target.helper(x); }
    private static helper(x: number) { if (x > 0) return x; return -x; }
    private helper() { return 99; }
    private static dead() { return 100; }
  }`
  const result = instrument(source)
  assert.equal(result.reason, null)
  assert.equal(result.statements.length, 4)
  assert.equal(result.statements.some((statement) => source.slice(statement.startOffset, statement.endOffset) === 'return 99;'), false)

  const runtime = load(result.source!, result.counterGlobalName)
  const target = runtime.Target as unknown as Record<string, (...args: unknown[]) => unknown>
  assert.equal(target['run']!(-2), 4)
  const covered = runtime.sandbox[result.counterGlobalName] as string[]
  assert.equal(new Set(covered).size, 3)
})


test('includes exact bodies of direct-called local functions without adding unused local bodies', () => {
  const source = `export class Target {
    static run(value: number) {
      function pad(input: number) {
        let result = String(input);
        while (result.length < 2) result = "0" + result;
        return result;
      }
      function dead() { return 99; }
      let declarationOnly: string;
      return pad(value);
    }
  }`
  const result = instrument(source)
  assert.equal(result.reason, null)
  assert.equal(result.statements.length, 5)
  assert.equal(result.statements.some((statement) => source.slice(statement.startOffset, statement.endOffset) === 'return 99;'), false)

  const original = load(source, null)
  const instrumented = load(result.source!, result.counterGlobalName)
  const originalClass = original.Target as unknown as Record<string, (...args: unknown[]) => unknown>
  const instrumentedClass = instrumented.Target as unknown as Record<string, (...args: unknown[]) => unknown>
  assert.equal(instrumentedClass['run']!(1), originalClass['run']!(1))
  assert.equal(new Set(instrumented.sandbox[result.counterGlobalName] as string[]).size, 5)
  assert.match(instrument('export class Target { run() { function local() { return 1; } return [1].map(local); } }').reason!, /escaped/)
})


test('freezes the complete constructor callable and receiver data inventory', () => {
  const source = `export class Target {
    private flag = false;
    constructor(public value: number) { this.extra = 1; }
    run(x: number) { this.value += x; return this; }
    get current() { return this.value; }
    set current(value: number) { this.value = value; }
    callback = (input: number) => input + 1;
    static utility(value: number) { return value + 1; }
  }`
  const result = instrument(source)
  assert.equal(result.reason, null)
  assert.deepEqual(result.inventory!.constructorParameterNames, ['value'])
  assert.deepEqual(result.inventory!.instanceDataFields.map((field) => field.name), ['extra', 'flag', 'value'])
  assert.deepEqual(result.inventory!.publicMembers.map((member) => [member.name, member.kind, member.isStatic, member.parameterNames]),
    [['run', 'METHOD', false, ['x']], ['current', 'GETTER', false, []], ['current', 'SETTER', false, ['value']],
      ['callback', 'FUNCTION_FIELD', false, ['input']], ['utility', 'METHOD', true, ['value']]])
})
