import { realpath, stat } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { tsImport } from 'tsx/esm/api';

export interface TypeScriptEntryPointReference {
  module: string;
  exportName: string;
  executionKind: 'sync' | 'async';
}

interface LoadedCallable {
  invoke(args: unknown[]): unknown | Promise<unknown>;
}

type EntryPointFunction = (...args: unknown[]) => unknown;

export async function loadCallable(
  reference: TypeScriptEntryPointReference,
  sourceRoots: string[],
  referencePath: string,
): Promise<LoadedCallable> {
  const modulePath = await resolveModule(reference.module, sourceRoots, referencePath);
  const moduleNamespace = await tsImport(pathToFileURL(modulePath).href, import.meta.url) as Record<string, unknown>;
  const exportedValue = moduleNamespace[reference.exportName];
  if (typeof exportedValue !== 'function') {
    throw new Error(`${referencePath}.exportName must identify a function export`);
  }

  return { invoke: buildInvocation(exportedValue as EntryPointFunction, reference, referencePath) };
}

async function resolveModule(module: string, sourceRoots: string[], referencePath: string): Promise<string> {
  const matches: string[] = [];
  for (const sourceRootValue of sourceRoots) {
    if (!path.isAbsolute(sourceRootValue)) throw new Error('sourceRoots must contain absolute paths');

    const sourceRoot = await realpath(sourceRootValue);
    const candidate = path.resolve(sourceRoot, module);
    if (!isWithin(candidate, sourceRoot)) throw new Error(`${referencePath}.module escapes its source root`);

    try {
      const resolved = await realpath(candidate);
      if (!isWithin(resolved, sourceRoot)) throw new Error(`${referencePath}.module resolves outside its source root`);
      if ((await stat(resolved)).isFile()) matches.push(resolved);
    } catch (error: unknown) {
      if (!isMissingPath(error)) throw error;
    }
  }

  if (matches.length !== 1) throw new Error(`${referencePath}.module resolved to ${matches.length} files`);

  return matches[0] as string;
}

function buildInvocation(
  entryPoint: EntryPointFunction,
  reference: TypeScriptEntryPointReference,
  referencePath: string,
): (args: unknown[]) => unknown | Promise<unknown> {
  if (reference.executionKind === 'sync') {
    return (args: unknown[]): unknown => {
      const result = entryPoint(...args);
      if (isThenable(result)) throw new Error(`${referencePath}.executionKind expected a synchronous result`);

      return result;
    };
  }

  return async (args: unknown[]): Promise<unknown> => {
    const result = entryPoint(...args);
    if (!isThenable(result)) throw new Error(`${referencePath}.executionKind expected an asynchronous result`);

    return await result;
  };
}

function isWithin(candidate: string, root: string): boolean {
  const relative = path.relative(root, candidate);

  return relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}

function isThenable(value: unknown): value is PromiseLike<unknown> {
  if (value === null) return false;
  if (typeof value !== 'object' && typeof value !== 'function') return false;

  return typeof (value as { then?: unknown }).then === 'function';
}

function isMissingPath(error: unknown): boolean {
  return error instanceof Error
    && 'code' in error
    && (error.code === 'ENOENT' || error.code === 'ENOTDIR');
}
