import { readFile, writeFile } from 'node:fs/promises';
import { loadCallable, type TypeScriptEntryPointReference } from './entry-point.js';
import { decodeJsValue, type TaggedJsValue } from './js-value.js';

interface ReplayWorkerRequest {
  sourceRoots: string[];
  entryPoint: TypeScriptEntryPointReference;
  inputs: TaggedJsValue[];
  hitKey: string;
  resultPath: string;
}

interface ReplayWorkerResult {
  invocation: 'returned' | 'threw';
  targetHit: boolean;
  errorName?: string;
  errorMessage?: string;
}

async function main(): Promise<void> {
  const requestPath = process.argv[2];
  if (requestPath === undefined) throw new Error('Expected a replay request path');

  const request = JSON.parse(await readFile(requestPath, 'utf8')) as ReplayWorkerRequest;
  Object.defineProperty(globalThis, request.hitKey, {
    configurable: true,
    enumerable: false,
    value: false,
    writable: true,
  });
  const callable = await loadCallable(request.entryPoint, request.sourceRoots, 'entryPoint');
  const inputs = request.inputs.map((value, index) => decodeJsValue(value, `inputs[${index}]`));
  (globalThis as Record<string, unknown>)[request.hitKey] = false;
  let result: ReplayWorkerResult;

  try {
    await callable.invoke(inputs);
    result = {
      invocation: 'returned',
      targetHit: globalThis[request.hitKey as keyof typeof globalThis] === true,
    };
  } catch (error: unknown) {
    result = {
      invocation: 'threw',
      targetHit: globalThis[request.hitKey as keyof typeof globalThis] === true,
      errorName: error instanceof Error ? error.name : typeof error,
      errorMessage: error instanceof Error ? error.message : String(error),
    };
  }

  await writeFile(request.resultPath, `${JSON.stringify(result)}\n`, 'utf8');
}

main().catch((error: unknown) => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
