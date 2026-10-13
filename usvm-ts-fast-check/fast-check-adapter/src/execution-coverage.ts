import { mkdirSync, writeFileSync } from 'node:fs';
import { findSourceMap } from 'node:module';
import path from 'node:path';
import { Worker } from 'node:worker_threads';
import type { ExecutionCoverageSnapshot } from './execution-coverage-worker.js';
import { stringifyProtocolJson } from './protocol-json.js';

export interface ExecutionCoverage {
  write(): Promise<void>;
  close(): Promise<void>;
}

/** Collect coverage without invoking serializers installed by the source under test. */
export async function startExecutionCoverage(): Promise<ExecutionCoverage | undefined> {
  const rawDirectory = process.env.USVM_V8_COVERAGE_DIRECTORY;
  if (rawDirectory === undefined || rawDirectory.length === 0) return undefined;

  // Inspector's protocol serializer also consults inherited toJSON, so its
  // session lives in a worker that never executes the source under test.
  const worker = new Worker(new URL('./execution-coverage-worker.js', import.meta.url), {
    env: { ...process.env, NODE_V8_COVERAGE: '' },
  });
  let workerFailure: Error | undefined;
  let workerExited = false;
  worker.on('error', error => { workerFailure = error; });
  worker.on('exit', () => { workerExited = true; });

  function receive(): Promise<'ready' | ExecutionCoverageSnapshot> {
    if (workerFailure !== undefined) return Promise.reject(workerFailure);
    if (workerExited) return Promise.reject(new Error('Coverage collector exited before producing a report'));

    return new Promise((resolve, reject) => {
      const cleanUp = (): void => {
        worker.off('message', onMessage);
        worker.off('error', onError);
        worker.off('exit', onExit);
      };
      const onMessage = (message: 'ready' | ExecutionCoverageSnapshot): void => {
        cleanUp();
        resolve(message);
      };
      const onError = (error: Error): void => {
        cleanUp();
        reject(error);
      };
      const onExit = (): void => onError(new Error('Coverage collector exited before producing a report'));
      worker.once('message', onMessage);
      worker.once('error', onError);
      worker.once('exit', onExit);
    });
  }

  try {
    const ready = await receive();
    if (ready !== 'ready') throw new Error('Unexpected coverage collector startup response');
  } catch (error) {
    await worker.terminate();
    throw error;
  }

  // Native coverage is configured when a Node isolate starts. Restoring only
  // the environment here enables user-created workers and subprocesses without
  // enabling an exit collector in this already-running main isolate.
  process.env.NODE_V8_COVERAGE = rawDirectory;

  return {
    async write(): Promise<void> {
      const pending = receive();
      worker.postMessage('take');
      const snapshot = await pending;
      if (snapshot === 'ready') throw new Error('Coverage collector did not produce a snapshot');

      const sourceMapCache: Record<string, unknown> = Object.create(null);
      for (const [url, script] of Object.entries(snapshot.scripts)) {
        const sourceMap = findSourceMap(url);
        if (sourceMap !== undefined || script.sourceMapUrl !== undefined) {
          sourceMapCache[url] = {
            lineLengths: script.lineLengths,
            data: sourceMap?.payload ?? null,
            url: sourceMap === undefined ? script.sourceMapUrl : null,
          };
        }
      }

      const report = { result: snapshot.result, 'source-map-cache': sourceMapCache };
      mkdirSync(rawDirectory, { recursive: true });
      writeFileSync(path.join(rawDirectory, `coverage-${process.pid}-usvm.json`), stringifyProtocolJson(report));
    },
    async close(): Promise<void> {
      await worker.terminate();
    },
  };
}
