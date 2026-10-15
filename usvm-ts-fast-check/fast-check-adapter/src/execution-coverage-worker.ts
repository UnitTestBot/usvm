import { Session, type Debugger, type Profiler } from 'node:inspector';
import { parentPort, workerData } from 'node:worker_threads';
import { mergeProcessCovs } from '@bcoe/v8-coverage';

export interface ExecutionCoverageSnapshot {
  kind: 'coverage';
  admittedInvocations: number;
  result: Profiler.ScriptCoverage[];
  scripts: Record<string, { lineLengths: number[]; sourceMapUrl?: string }>;
}

const port = parentPort;
if (port === null) throw new Error('Coverage collector requires a worker');

const session = new Session();
const parsedScripts = new Map<string, Debugger.ScriptParsedEventDataType>();
const scriptIds = new Map<string, string>();
session.connectToMainThread();
session.on('Debugger.scriptParsed', ({ params }) => parsedScripts.set(params.scriptId, params));

// Keep the port referenced while inspector replies are pending. A one-shot
// listener would let this worker exit before an asynchronous snapshot finishes.
let admitted: Profiler.ScriptCoverage[] = [];
let admittedInvocations = 0;

const completion = new Int32Array(workerData.completion);
port.on('message', (command: 'reset' | 'admit' | 'take') => {
  void takeSnapshot(command).then(snapshot => {
    if (command === 'take') port.postMessage(snapshot);
    else {
      Atomics.store(completion, 0, 1);
      Atomics.notify(completion, 0);
    }
  }).catch(error => {
    Atomics.store(completion, 0, -1);
    Atomics.notify(completion, 0);
    throw error;
  });
});

await post('Profiler.enable');
await post('Debugger.enable');
await post('Profiler.startPreciseCoverage', { callCount: true, detailed: true });

port.postMessage('ready');

async function takeSnapshot(command: 'reset' | 'admit' | 'take'): Promise<ExecutionCoverageSnapshot> {
  const coverage = await post<Profiler.TakePreciseCoverageReturnType>('Profiler.takePreciseCoverage');
  for (const script of coverage.result) scriptIds.set(script.url, script.scriptId);
  if (command === 'admit') {
    admitted = mergeProcessCovs([{ result: admitted }, { result: coverage.result }]).result;
    admittedInvocations += 1;
  }
  const result = command === 'take' && workerData.admittedOnly === true ? admitted : coverage.result;
  const scripts: ExecutionCoverageSnapshot['scripts'] = Object.create(null);
  for (const script of command === 'take' ? result : []) {
    if (!script.url.startsWith('file:')) continue;

    const { scriptSource } = await post<Debugger.GetScriptSourceReturnType>(
      'Debugger.getScriptSource', { scriptId: scriptIds.get(script.url) ?? script.scriptId },
    );
    const sourceMapUrl = parsedScripts.get(scriptIds.get(script.url) ?? script.scriptId)?.sourceMapURL;
    scripts[script.url] = {
      // V8 offsets are UTF-16 offsets. Keep CR in a CRLF line, like Node's cache.
      lineLengths: scriptSource.split(/[\n\u2028\u2029]/).map(line => line.length),
      ...(sourceMapUrl === undefined || sourceMapUrl.length === 0 ? {} : { sourceMapUrl }),
    };
  }

  if (command === 'take') {
    await post('Profiler.stopPreciseCoverage');
    session.disconnect();
  }

  return { kind: 'coverage', result, scripts, admittedInvocations };
}

function post<T = unknown>(method: string, params: object = {}): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    session.post(method, params, (error, result) => {
      if (error !== null) reject(error);
      else resolve(result as T);
    });
  });
}
