import { realpath, stat } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { tsImport } from 'tsx/esm/api';
import type { JsConcreteValue } from './js-value.js';
import { ProtocolError, protocolError } from './js-value.js';

export type ExecutionKind = 'sync' | 'async';

export interface TypeScriptEntryPointReference {
  module: string;
  exportName: string;
  executionKind: ExecutionKind;
}

export interface LoadedEntryPoint {
  executionKind: ExecutionKind;
  invoke(args: JsConcreteValue[]): boolean | Promise<boolean>;
}

type EntryPointFunction = (...args: JsConcreteValue[]) => unknown;

export async function loadEntryPoint(
  reference: TypeScriptEntryPointReference,
  sourceRoots: string[],
  referencePath: string,
): Promise<LoadedEntryPoint> {
  const modulePath = await resolveModule(reference.module, sourceRoots, referencePath);
  const moduleNamespace = await importTypeScriptModule(modulePath, referencePath);
  if (!(reference.exportName in moduleNamespace)) {
    throw protocolError(
      'entrypoint.export.not-found',
      `TypeScript module ${reference.module} does not export ${reference.exportName}`,
      `${referencePath}.exportName`,
    );
  }
  const exportedValue = moduleNamespace[reference.exportName];
  if (typeof exportedValue !== 'function') {
    throw protocolError(
      'entrypoint.export.not-function',
      `TypeScript export ${reference.exportName} is not a function`,
      `${referencePath}.exportName`,
    );
  }
  const entryPoint = exportedValue as EntryPointFunction;
  return {
    executionKind: reference.executionKind,
    invoke: buildInvocation(entryPoint, reference.executionKind, referencePath),
  };
}

async function resolveModule(
  module: string,
  sourceRoots: string[],
  referencePath: string,
): Promise<string> {
  if (sourceRoots.length === 0) {
    throw protocolError(
      'source-root.invalid',
      'At least one TypeScript source root is required',
      'sourceRoots',
    );
  }
  const matches: string[] = [];
  for (let index = 0; index < sourceRoots.length; index += 1) {
    const sourceRoot = sourceRoots[index];
    if (sourceRoot === undefined || !path.isAbsolute(sourceRoot)) {
      throw protocolError(
        'source-root.invalid',
        'TypeScript source roots must be absolute paths',
        `sourceRoots[${index}]`,
      );
    }
    const realSourceRoot = await requireDirectory(sourceRoot, index);
    const candidate = path.resolve(realSourceRoot, module);
    if (!isWithin(candidate, realSourceRoot)) {
      throw protocolError(
        'entrypoint.module.outside-root',
        `TypeScript module ${module} escapes source root ${realSourceRoot}`,
        `${referencePath}.module`,
      );
    }
    const realCandidate = await realpathOrUndefined(candidate);
    if (realCandidate === undefined) continue;
    if (!isWithin(realCandidate, realSourceRoot)) {
      throw protocolError(
        'entrypoint.module.outside-root',
        `TypeScript module ${module} resolves outside source root ${realSourceRoot}`,
        `${referencePath}.module`,
      );
    }
    const candidateStat = await stat(realCandidate);
    if (candidateStat.isFile()) matches.push(realCandidate);
  }
  if (matches.length === 0) {
    throw protocolError(
      'entrypoint.module.not-found',
      `TypeScript module ${module} was not found in any source root`,
      `${referencePath}.module`,
    );
  }
  if (matches.length > 1) {
    throw protocolError(
      'entrypoint.module.ambiguous',
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
      'source-root.invalid',
      `TypeScript source root does not exist: ${sourceRoot}`,
      `sourceRoots[${index}]`,
    );
  }
  if (!(await stat(realSourceRoot)).isDirectory()) {
    throw protocolError(
      'source-root.invalid',
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
    return await tsImport(pathToFileURL(modulePath).href, import.meta.url) as Record<string, unknown>;
  } catch (error: unknown) {
    if (error instanceof ProtocolError) throw error;
    const message = error instanceof Error ? error.message : String(error);
    throw protocolError(
      'entrypoint.module.import-failed',
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
      const result = entryPoint(...args);
      if (isThenable(result)) {
        void Promise.resolve(result).catch(() => undefined);
        throw protocolError(
          'entrypoint.execution-kind.mismatch',
          'A synchronous entry point returned an awaitable value',
          `${referencePath}.executionKind`,
        );
      }
      return requireBoolean(result, referencePath);
    };
  }
  return async (args: JsConcreteValue[]): Promise<boolean> => {
    const result = entryPoint(...args);
    if (!isThenable(result)) {
      throw protocolError(
        'entrypoint.execution-kind.mismatch',
        'An asynchronous entry point returned a direct value',
        `${referencePath}.executionKind`,
      );
    }
    return requireBoolean(await result, referencePath);
  };
}

function requireBoolean(result: unknown, referencePath: string): boolean {
  if (typeof result !== 'boolean') {
    throw protocolError(
      'entrypoint.result.invalid',
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
