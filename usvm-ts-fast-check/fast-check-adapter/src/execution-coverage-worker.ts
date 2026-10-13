import { Session, type Debugger, type Profiler } from 'node:inspector';
import { parentPort } from 'node:worker_threads';

export interface ExecutionCoverageSnapshot {
  kind: 'coverage';
  result: Profiler.ScriptCoverage[];
  scripts: Record<string, { lineLengths: number[]; sourceMapUrl?: string }>;
}

const port = parentPort;
if (port === null) throw new Error('Coverage collector requires a worker');

const session = new Session();
const parsedScripts = new Map<string, Debugger.ScriptParsedEventDataType>();
session.connectToMainThread();
session.on('Debugger.scriptParsed', ({ params }) => parsedScripts.set(params.scriptId, params));

// Keep the port referenced while inspector replies are pending. A one-shot
// listener would let this worker exit before an asynchronous snapshot finishes.
port.on('message', () => {
  void takeSnapshot().then(snapshot => port.postMessage(snapshot)).catch(error => {
    throw error;
  });
});

await post('Profiler.enable');
await post('Debugger.enable');
await post('Profiler.startPreciseCoverage', { callCount: true, detailed: true });

port.postMessage('ready');

async function takeSnapshot(): Promise<ExecutionCoverageSnapshot> {
  const coverage = await post<Profiler.TakePreciseCoverageReturnType>('Profiler.takePreciseCoverage');
  const scripts: ExecutionCoverageSnapshot['scripts'] = Object.create(null);
  for (const script of coverage.result) {
    if (!script.url.startsWith('file:')) continue;

    const { scriptSource } = await post<Debugger.GetScriptSourceReturnType>(
      'Debugger.getScriptSource', { scriptId: script.scriptId },
    );
    const sourceMapUrl = parsedScripts.get(script.scriptId)?.sourceMapURL;
    scripts[script.url] = {
      // V8 offsets are UTF-16 offsets. Keep CR in a CRLF line, like Node's cache.
      lineLengths: scriptSource.split(/[\n\u2028\u2029]/).map(line => line.length),
      ...(sourceMapUrl === undefined || sourceMapUrl.length === 0 ? {} : { sourceMapUrl }),
    };
  }

  await post('Profiler.stopPreciseCoverage');
  session.disconnect();

  return { kind: 'coverage', result: coverage.result, scripts };
}

function post<T = unknown>(method: string, params: object = {}): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    session.post(method, params, (error, result) => {
      if (error !== null) reject(error);
      else resolve(result as T);
    });
  });
}
