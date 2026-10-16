import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir, mkdtemp, rm, realpath } from 'node:fs/promises';
import { createRequire } from 'node:module';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const [operation, adapterDirectory, requestFile, outputFile] = process.argv.slice(2);
const adapter = await realpath(adapterDirectory);
const require = createRequire(path.join(adapter, 'package.json'));
const ts = require('typescript');
const runtimeModule = name => import(pathToFileURL(path.join(adapter, 'dist/src', name)).href);
const { instrumentClassCoverage } = await runtimeModule('class-coverage-source.js');
const { inspectLocalSourceClosure } = await runtimeModule('local-source-closure.js');
const { decodeJsValue, encodeJsValue } = await runtimeModule('js-value.js');
const { stringifyProtocolJson } = await runtimeModule('protocol-json.js');
const sha256 = bytes => createHash('sha256').update(bytes).digest('hex');
const request = JSON.parse(await readFile(requestFile, 'utf8'));

function inside(root, relative) {
  const selected = path.resolve(root, relative);
  if (path.isAbsolute(relative) || path.relative(root, selected).startsWith('..')) {
    throw new Error(`Source escapes its frozen root: ${relative}`);
  }
  return selected;
}

async function verifySources(root, sources) {
  for (const [relative, expected] of Object.entries(sources)) {
    const file = inside(root, relative);
    if (await realpath(file) !== file || sha256(await readFile(file)) !== expected) {
      throw new Error(`Frozen source changed or uses a symlink: ${relative}`);
    }
  }
}

async function prepare() {
  const root = await realpath(request.sourceRoot);
  await verifySources(root, request.sourceSha256);
  const entries = [request.classSource, request.property.predicate.module,
    request.property.precondition?.module, request.diagnosticScenario?.module].filter(Boolean);
  const sources = {};
  let frozenConfig;
  for (const entry of new Set(entries)) {
    const closure = inspectLocalSourceClosure(root, inside(root, entry));
    if (closure.reasonCode) return { supported: false, diagnostic: closure.diagnostic, reasonCode: closure.reasonCode };
    const resolution = closure.resolution;
    if (resolution) {
      const config = JSON.parse(resolution.tsconfig);
      if (Object.keys(config.compilerOptions?.paths ?? {}).length > 0) {
        return { supported: false, reasonCode: 'REPLAY_ALIASES_UNSUPPORTED',
          diagnostic: 'The checked batch replay currently supports relative TypeScript imports only' };
      }
      frozenConfig ??= resolution.tsconfig;
      Object.assign(sources, resolution.inputSha256);
    }
    for (const relative of closure.files) sources[relative] = sha256(await readFile(inside(root, relative)));
  }
  const missingHashes = Object.keys(sources).filter(relative => request.sourceSha256[relative] !== sources[relative]);
  if (missingHashes.length > 0) throw new Error(`Manifest omits frozen closure hashes: ${missingHashes.join(', ')}`);

  const original = await readFile(inside(root, request.classSource), 'utf8');
  const source = ts.createSourceFile(request.classSource, original, ts.ScriptTarget.Latest, true);
  const instrumentation = instrumentClassCoverage(source, request.className);
  if (instrumentation.source === null || instrumentation.statements.length === 0) {
    return { supported: false, reasonCode: 'CLASS_UNIVERSE_UNSUPPORTED', diagnostic: instrumentation.reason };
  }
  const universe = { sourceFile: request.classSource, className: request.className,
    sourceSha256: instrumentation.sourceSha256, instrumenterVersion: instrumentation.instrumenterVersion,
    statements: instrumentation.statements };
  return { supported: true, sourceSha256: sources, frozenConfig,
    universe: { ...universe, sha256: sha256(stringifyProtocolJson(universe)) },
    instrumentedSource: instrumentation.source, instrumentedSha256: instrumentation.instrumentedSha256,
    counterGlobalName: instrumentation.counterGlobalName,
    excludedExecutionScopes: ['module-loading', 'preconditions', 'diagnostic-scenarios'],
    verificationBoundary: request.diagnosticScenario ? 'SCENARIO_OBSERVATIONS' : 'ORACLE_ONLY' };
}

function errorIdentity(error) {
  return { name: typeof error?.name === 'string' ? error.name : typeof error,
    message: typeof error?.message === 'string' ? error.message : String(error) };
}

async function replay() {
  const root = await realpath(request.sourceRoot);
  const prepared = request.prepared;
  await verifySources(root, prepared.sourceSha256);
  if (!prepared.supported || sha256(prepared.instrumentedSource) !== prepared.instrumentedSha256) {
    throw new Error('Frozen class instrumentation is unavailable or changed');
  }
  const directory = await mkdtemp(path.join(os.tmpdir(), 'usvm-pbt-class-replay-'));
  try {
    const configPath = path.join(directory, 'tsconfig.json');
    await writeFile(configPath, prepared.frozenConfig ?? '{}');
    const { loadEntryPoint, EntryPointInvocationError, isNamedCheckFailure } = await runtimeModule('entry-point.js');
    const { invokePropertyOnce } = await runtimeModule('execute-property.js');

    async function copyVariant(name, instrumented) {
      const variant = path.join(directory, name);
      for (const relative of Object.keys(prepared.sourceSha256)) {
        const destination = inside(variant, relative);
        await mkdir(path.dirname(destination), { recursive: true });
        const content = instrumented && relative === request.classSource
          ? prepared.instrumentedSource : await readFile(inside(root, relative));
        await writeFile(destination, content);
      }
      return variant;
    }

    async function runBatch(name, instrumented, inputs) {
      const variant = await copyVariant(`${name}-oracle`, instrumented);
      const property = request.property;
      const predicate = await loadEntryPoint({ executionKind: 'sync', ...property.predicate }, [variant], 'predicate', configPath);
      const precondition = property.precondition
        ? await loadEntryPoint({ executionKind: 'sync', ...property.precondition }, [variant], 'precondition', configPath) : undefined;
      let diagnostic;
      let diagnosticPrecondition;
      if (request.diagnosticScenario) {
        const diagnosticRoot = await copyVariant(`${name}-diagnostic`, instrumented);
        const entry = request.diagnosticScenario;
        const namespace = await import(pathToFileURL(inside(diagnosticRoot, entry.module)).href);
        diagnostic = namespace[entry.exportName];
        if (typeof diagnostic !== 'function') throw new Error('Diagnostic scenario export is not a function');
        if (property.precondition) {
          diagnosticPrecondition = await loadEntryPoint({ executionKind: 'sync', ...property.precondition },
            [diagnosticRoot], 'precondition', configPath);
        }
      }

      const records = [];
      for (const input of inputs) {
        const values = input.inputs.map(value => decodeJsValue(value));
        let oracle = { admission: 'ADMITTED', outcome: 'HOLDS' };
        let scenario;
        let admitted = false;
        globalThis[prepared.counterGlobalName] = new Set();
        try {
          const holds = await invokePropertyOnce(values, predicate, precondition, undefined, 'replay', undefined, () => {
            admitted = true;
            globalThis[prepared.counterGlobalName] = new Set();
          });
          oracle = holds === null ? { admission: 'PRECONDITION_REJECTED', outcome: 'SKIPPED' }
            : { admission: 'ADMITTED', outcome: holds ? 'HOLDS' : 'FALSE' };
        } catch (error) {
          if (admitted && isNamedCheckFailure(error)) {
            oracle = { ...oracle, outcome: error.exception ? 'THREW' : 'FALSE',
              failure: { checkId: error.checkId, ...errorIdentity(error.exception?.value ?? error) } };
          } else if (admitted && error instanceof EntryPointInvocationError) {
            oracle = { ...oracle, outcome: 'THREW', failure: errorIdentity(error.thrownValue) };
          } else {
            oracle = { admission: 'ERROR', outcome: 'ERROR', failure: errorIdentity(error) };
          }
        }
        const hits = admitted ? [...globalThis[prepared.counterGlobalName]].sort() : [];
        globalThis[prepared.counterGlobalName] = new Set();
        if (diagnostic) {
          try {
            const callback = { executionKind: 'sync', invoke: args => {
              const result = diagnostic(...args);
              if (result && typeof result.then === 'function') throw new Error('Async diagnostic scenarios are unsupported');
              scenario = { value: encodeJsValue(result) };
              return true;
            } };
            const admittedScenario = await invokePropertyOnce(values, callback, diagnosticPrecondition, undefined, 'replay');
            if ((admittedScenario !== null) !== admitted) scenario = { error: 'Diagnostic admission differs' };
          } catch (error) {
            scenario = { error: errorIdentity(error) };
          }
        }
        records.push({ oracle, scenario, hits });
      }
      return records;
    }

    async function runVariant(name, instrumented) {
      if (!request.isolateInputs) return runBatch(name, instrumented, request.inputs);
      const records = [];
      for (const [index, input] of request.inputs.entries()) {
        records.push(...await runBatch(`${name}-${index}`, instrumented, [input]));
      }
      return records;
    }

    const original = await runVariant('original-1', false);
    const repeated = await runVariant('original-2', false);
    const counted = await runVariant('counted-1', true);
    const countedRepeated = await runVariant('counted-2', true);
    const knownIds = new Set(prepared.universe.statements.map(statement => statement.id));
    const signature = record => stringifyProtocolJson({ oracle: record.oracle, scenario: record.scenario });
    const records = request.inputs.map((input, index) => {
      const baseline = original[index];
      const variants = [repeated[index], counted[index], countedRepeated[index]];
      const unknownHits = counted[index].hits.some(id => !knownIds.has(id));
      const matches = variants.every(record => signature(record) === signature(baseline));
      const expected = baseline.oracle.admission === input.admission && baseline.oracle.outcome === input.outcome;
      const repeatableHits = stringifyProtocolJson(counted[index].hits) === stringifyProtocolJson(countedRepeated[index].hits);
      const observable = !baseline.scenario?.error;
      const verified = matches && expected && repeatableHits && !unknownHits && observable;
      return { input, original: baseline, repeated: repeated[index], counted: counted[index],
        countedRepeated: countedRepeated[index], verified,
        creditedStatementIds: verified && baseline.oracle.admission === 'ADMITTED' ? counted[index].hits : [],
        diagnostic: verified ? null : { matches, expected, repeatableHits, unknownHits, observable } };
    });
    await verifySources(root, prepared.sourceSha256);
    return { verificationBoundary: prepared.verificationBoundary, records };
  } finally {
    delete globalThis[prepared.counterGlobalName];
    await rm(directory, { recursive: true, force: true });
  }
}

try {
  const result = operation === 'prepare' ? await prepare() : operation === 'replay' ? await replay() : null;
  if (result === null) throw new Error(`Unknown class replay operation: ${operation}`);
  await writeFile(outputFile, stringifyProtocolJson(result));
} catch (error) {
  await writeFile(outputFile, stringifyProtocolJson({ error: errorIdentity(error) }));
  process.exitCode = 2;
}
