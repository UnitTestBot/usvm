import { createHash } from 'node:crypto';
import { readFile, realpath, stat } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { register as registerCommonJs } from 'tsx/cjs/api';
import { register } from 'tsx/esm/api';
import { adapterDiagnostic } from './diagnostics.js';
import type { JsConcreteValue } from './js-value.js';
import { ProtocolError, protocolError } from './js-value.js';

// Each adapter process uses one explicit resolver configuration for all nested imports.
let registeredConfig: string | undefined;
function registerTypeScriptLoader(tsconfig: string | undefined): void {
  const config = tsconfig ?? '<ambient>';
  if (registeredConfig !== undefined) {
    if (registeredConfig !== config) throw new Error('Cannot change TypeScript resolution inside an adapter process');
    return;
  }
  if (tsconfig !== undefined) process.env.TSX_TSCONFIG_PATH = tsconfig;
  registerCommonJs();
  register(tsconfig === undefined ? undefined : { tsconfig });
  registeredConfig = config;
}

export type ExecutionKind = 'sync' | 'async';

export interface TypeScriptEntryPointReference {
  module: string;
  exportName: string;
  executionKind: ExecutionKind;
  checkResultExportName?: string;
}

export interface LoadedEntryPoint {
  executionKind: ExecutionKind;
  invoke(args: JsConcreteValue[]): boolean | Promise<boolean>;
}

/** Keeps user-thrown values distinct from adapter contract errors across external runners. */
export class EntryPointInvocationError extends Error {
  constructor(readonly thrownValue: unknown) {
    super('Property entry point threw');
  }
}

/** Failure data from a generated companion, never parsed from user exception messages. */
export class NamedCheckFailure extends Error {
  constructor(readonly checkId: string, readonly actual: unknown, readonly expected: unknown,
    readonly exception?: { value: unknown }) {
    super(`Check ${checkId} failed`);
    this.name = 'NamedCheckFailure';
    namedCheckFailures.add(this);
  }
}

const namedCheckFailures = new WeakSet<object>();

/** Membership avoids invoking Symbol.hasInstance or prototype traps on user-thrown proxies. */
export function isNamedCheckFailure(value: unknown): value is NamedCheckFailure {
  return value !== null && typeof value === 'object' && namedCheckFailures.has(value);
}

type EntryPointFunction = (...args: JsConcreteValue[]) => unknown;

export async function loadEntryPoint(
  reference: TypeScriptEntryPointReference,
  sourceRoots: string[],
  referencePath: string,
  tsconfig?: string,
): Promise<LoadedEntryPoint> {
  registerTypeScriptLoader(tsconfig);
  const modulePath = await resolveModule(reference.module, sourceRoots, referencePath);
  const moduleNamespace = await importTypeScriptModule(modulePath, referencePath);

  if (!(reference.exportName in moduleNamespace)) {
    throw protocolError(
      adapterDiagnostic.entryPointExportNotFound,
      `TypeScript module ${reference.module} does not export ${reference.exportName}`,
      `${referencePath}.exportName`,
    );
  }

  const exportedValue = moduleNamespace[reference.exportName];
  if (typeof exportedValue !== 'function') {
    throw protocolError(
      adapterDiagnostic.entryPointExportNotFunction,
      `TypeScript export ${reference.exportName} is not a function`,
      `${referencePath}.exportName`,
    );
  }

  const entryPoint = exportedValue as EntryPointFunction;

  if (reference.checkResultExportName !== undefined) {
    const companion = moduleNamespace[reference.checkResultExportName];
    if (reference.executionKind !== 'sync' || typeof companion !== 'function') {
      throw protocolError(adapterDiagnostic.entryPointExportNotFunction,
        'A generated check companion must be a synchronous function', `${referencePath}.checkResultExportName`);
    }

    return {
      executionKind: 'sync',
      invoke: (args) => {
        const result = invokeEntryPoint(companion as EntryPointFunction, args);
        if (result === null || typeof result !== 'object' || isThenable(result)) {
          throw protocolError(adapterDiagnostic.entryPointResultInvalid,
            'A generated companion must return a check outcome', `${referencePath}.result`);
        }
        const outcome = result as Record<string, unknown>;
        if (typeof outcome.passed !== 'boolean' || typeof outcome.checkId !== 'string') {
          throw protocolError(adapterDiagnostic.entryPointResultInvalid,
            'Invalid generated check outcome', `${referencePath}.result`);
        }
        if (!outcome.passed) {
          if (outcome.checkId.length === 0) {
            throw protocolError(adapterDiagnostic.entryPointResultInvalid,
              'A failed check must have an identity', `${referencePath}.result`);
          }
          const exception = outcome.exception;
          if (exception !== undefined && (exception === null || typeof exception !== 'object'
            || !Object.hasOwn(exception, 'value'))) {
            throw protocolError(adapterDiagnostic.entryPointResultInvalid,
              'Invalid generated check exception', `${referencePath}.result`);
          }
          throw new NamedCheckFailure(outcome.checkId, outcome.actual, outcome.expected,
            exception as { value: unknown } | undefined);
        }

        return true;
      },
    };
  }

  return {
    executionKind: reference.executionKind,
    invoke: buildInvocation(entryPoint, reference.executionKind, referencePath),
  };
}

/** Verifies one selected source module's exact bytes before importing user code. */
export async function verifySourceHash(
  module: string,
  expectedSha256: string,
  sourceRoots: string[],
): Promise<void> {
  const modulePath = await resolveModule(module, sourceRoots, 'observationRequest.sources');
  const actual = createHash('sha256').update(await readFile(modulePath)).digest('hex');
  if (actual !== expectedSha256) {
    throw protocolError(
      adapterDiagnostic.entryPointSourceHashMismatch,
      `Selected TypeScript source changed: ${module}`,
      'observationRequest.sources',
    );
  }
}

async function resolveModule(
  module: string,
  sourceRoots: string[],
  referencePath: string,
): Promise<string> {
  if (sourceRoots.length === 0) {
    throw protocolError(
      adapterDiagnostic.sourceRootInvalid,
      'At least one TypeScript source root is required',
      'sourceRoots',
    );
  }

  const matches: string[] = [];
  for (let index = 0; index < sourceRoots.length; index += 1) {
    const sourceRoot = sourceRoots[index];

    if (sourceRoot === undefined || !path.isAbsolute(sourceRoot)) {
      throw protocolError(
        adapterDiagnostic.sourceRootInvalid,
        'TypeScript source roots must be absolute paths',
        `sourceRoots[${index}]`,
      );
    }

    const realSourceRoot = await requireDirectory(sourceRoot, index);
    const candidate = path.resolve(realSourceRoot, module);

    if (!isWithin(candidate, realSourceRoot)) {
      throw protocolError(
        adapterDiagnostic.entryPointModuleOutsideRoot,
        `TypeScript module ${module} escapes source root ${realSourceRoot}`,
        `${referencePath}.module`,
      );
    }

    const realCandidate = await realpathOrUndefined(candidate);
    if (realCandidate === undefined) continue;

    if (!isWithin(realCandidate, realSourceRoot)) {
      throw protocolError(
        adapterDiagnostic.entryPointModuleOutsideRoot,
        `TypeScript module ${module} resolves outside source root ${realSourceRoot}`,
        `${referencePath}.module`,
      );
    }

    const candidateStat = await stat(realCandidate);
    if (candidateStat.isFile()) matches.push(realCandidate);
  }

  if (matches.length === 0) {
    throw protocolError(
      adapterDiagnostic.entryPointModuleNotFound,
      `TypeScript module ${module} was not found in any source root`,
      `${referencePath}.module`,
    );
  }

  if (matches.length > 1) {
    throw protocolError(
      adapterDiagnostic.entryPointModuleAmbiguous,
      `TypeScript module ${module} exists in multiple source roots`,
      `${referencePath}.module`,
    );
  }

  return matches[0] as string;
}

async function requireDirectory(sourceRoot: string, index: number): Promise<string> {
  let realSourceRoot: string;
  try {
    realSourceRoot = await realpath(sourceRoot);
  } catch {
    throw protocolError(
      adapterDiagnostic.sourceRootInvalid,
      `TypeScript source root does not exist: ${sourceRoot}`,
      `sourceRoots[${index}]`,
    );
  }

  if (!(await stat(realSourceRoot)).isDirectory()) {
    throw protocolError(
      adapterDiagnostic.sourceRootInvalid,
      `TypeScript source root is not a directory: ${sourceRoot}`,
      `sourceRoots[${index}]`,
    );
  }

  return realSourceRoot;
}

async function realpathOrUndefined(candidate: string): Promise<string | undefined> {
  try {
    return await realpath(candidate);
  } catch (error: unknown) {
    if (isNodeError(error) && (error.code === 'ENOENT' || error.code === 'ENOTDIR')) return undefined;

    throw error;
  }
}

async function importTypeScriptModule(
  modulePath: string,
  referencePath: string,
): Promise<Record<string, unknown>> {
  try {
    return await import(pathToFileURL(modulePath).href) as Record<string, unknown>;
  } catch (error: unknown) {
    if (error instanceof ProtocolError) throw error;

    const message = error instanceof Error ? error.message : String(error);

    throw protocolError(
      adapterDiagnostic.entryPointModuleImportFailed,
      `Failed to import TypeScript module: ${message}`,
      `${referencePath}.module`,
    );
  }
}

function buildInvocation(
  entryPoint: EntryPointFunction,
  executionKind: ExecutionKind,
  referencePath: string,
): (args: JsConcreteValue[]) => boolean | Promise<boolean> {
  if (executionKind === 'sync') {
    return (args: JsConcreteValue[]): boolean => {
      const result = invokeEntryPoint(entryPoint, args);

      if (isThenable(result)) {
        void Promise.resolve(result).catch(() => undefined);
        throw protocolError(
          adapterDiagnostic.entryPointExecutionKindMismatch,
          'A synchronous entry point returned an awaitable value',
          `${referencePath}.executionKind`,
        );
      }

      return requireBoolean(result, referencePath);
    };
  }

  return async (args: JsConcreteValue[]): Promise<boolean> => {
    const result = invokeEntryPoint(entryPoint, args);

    if (!isThenable(result)) {
      throw protocolError(
        adapterDiagnostic.entryPointExecutionKindMismatch,
        'An asynchronous entry point returned a direct value',
        `${referencePath}.executionKind`,
      );
    }

    return requireBoolean(await resolveEntryPoint(result), referencePath);
  };
}

function invokeEntryPoint(entryPoint: EntryPointFunction, args: JsConcreteValue[]): unknown {
  try {
    return entryPoint(...args);
  } catch (error: unknown) {
    throw new EntryPointInvocationError(error);
  }
}

async function resolveEntryPoint(result: PromiseLike<unknown>): Promise<unknown> {
  try {
    return await result;
  } catch (error: unknown) {
    throw new EntryPointInvocationError(error);
  }
}

function requireBoolean(result: unknown, referencePath: string): boolean {
  if (typeof result !== 'boolean') {
    throw protocolError(
      adapterDiagnostic.entryPointResultInvalid,
      'A property entry point must return a boolean',
      `${referencePath}.result`,
    );
  }

  return result;
}

function isThenable(value: unknown): value is PromiseLike<unknown> {
  if (value === null) return false;
  if (typeof value !== 'object' && typeof value !== 'function') return false;

  return typeof (value as { then?: unknown }).then === 'function';
}

function isWithin(candidate: string, root: string): boolean {
  const relative = path.relative(root, candidate);

  return relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}

function isNodeError(error: unknown): error is NodeJS.ErrnoException {
  return error instanceof Error && 'code' in error;
}
