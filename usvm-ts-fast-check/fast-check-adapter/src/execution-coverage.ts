import { mkdirSync, writeFileSync } from 'node:fs';
import { findSourceMap } from 'node:module';
import path from 'node:path';
import { Worker } from 'node:worker_threads';
import { performance } from 'node:perf_hooks';
import { protocolError } from './js-value.js';
import type { ExecutionCoverageSnapshot } from './execution-coverage-worker.js';
import { stringifyProtocolJson } from './protocol-json.js';

export interface ExecutionCoverage {
  readonly admittedOnly: boolean;
  reset(): void;
  admit(): void;
  write(): Promise<void>;
  close(): Promise<void>;
}

/** Collect coverage without invoking serializers installed by the source under test. */
export async function startExecutionCoverage(admittedOnly = false, timeoutMillis = 60_000): Promise<ExecutionCoverage | undefined> {
  const rawDirectory = process.env.USVM_V8_COVERAGE_DIRECTORY;
  if (rawDirectory === undefined || rawDirectory.length === 0) return undefined;
  const deadline = performance.now() + timeoutMillis;

  // Inspector's protocol serializer also consults inherited toJSON, so its
  // session lives in a worker that never executes the source under test.
  const completion = new Int32Array(new SharedArrayBuffer(Int32Array.BYTES_PER_ELEMENT));
  const worker = new Worker(new URL('./execution-coverage-worker.js', import.meta.url), {
    env: { ...process.env, NODE_V8_COVERAGE: '' },
    workerData: { admittedOnly, completion: completion.buffer },
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
  if (!admittedOnly) process.env.NODE_V8_COVERAGE = rawDirectory;

  async function snapshot(command: 'take'): Promise<ExecutionCoverageSnapshot> {
    const pending = receive();
    worker.postMessage(command);
    const report = await pending;
    if (report === 'ready') throw new Error('Coverage collector did not produce a snapshot');
    return report;
  }

  // A synchronous handshake preserves the uninterrupted precondition/predicate
  // call: yielding here could run user microtasks and change its result.
  function snapshotSync(command: 'reset' | 'admit'): void {
    Atomics.store(completion, 0, 0);
    worker.postMessage(command);
    const remaining = Math.max(0, deadline - performance.now());
    const outcome = Atomics.wait(completion, 0, 0, remaining);
    if (outcome === 'timed-out' || Atomics.load(completion, 0) !== 1) {
      throw protocolError({ kind: 'coverage', code: 'coverage.snapshot.failed' },
        'Admitted coverage snapshot failed or exhausted the run deadline', 'coverage');
    }
  }

  return {
    admittedOnly,
    reset(): void { snapshotSync('reset'); },
    admit(): void { snapshotSync('admit'); },
    async write(): Promise<void> {
      // Descendants already hold their inherited environment. Clear the main
      // process before coverage is drained or any shutdown hook can serialize it.
      delete process.env.NODE_V8_COVERAGE;

      const reportSnapshot = await snapshot('take');

      const sourceMapCache: Record<string, unknown> = Object.create(null);
      for (const [url, script] of Object.entries(reportSnapshot.scripts)) {
        const sourceMap = findSourceMap(url);
        if (sourceMap !== undefined || script.sourceMapUrl !== undefined) {
          sourceMapCache[url] = {
            lineLengths: script.lineLengths,
            data: sourceMap?.payload ?? null,
            url: sourceMap === undefined ? script.sourceMapUrl : null,
          };
        }
      }

      const report = { result: reportSnapshot.result, 'source-map-cache': sourceMapCache };
      mkdirSync(rawDirectory, { recursive: true });
      if (admittedOnly) {
        writeFileSync(path.join(rawDirectory, 'admitted-count.txt'), String(reportSnapshot.admittedInvocations));
      }
      writeFileSync(path.join(rawDirectory, `coverage-${process.pid}-usvm.json`), stringifyProtocolJson(report));
    },
    async close(): Promise<void> {
      delete process.env.NODE_V8_COVERAGE;
      await worker.terminate();
    },
  };
}
