import { spawn, spawnSync } from 'node:child_process';
import { unlinkSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { isMainThread, Worker, workerData } from 'node:worker_threads';

interface AdapterExitMessage {
  type: 'adapter-exit';
  code: number;
}

interface AdapterWorkerData {
  adapterEntryPoint: string;
}

const workerFlag = '--worker';

if (!isMainThread) {
  const data = requireAdapterWorkerData(workerData);

  await runAdapterWorker(data.adapterEntryPoint);
} else {
  const mode = process.argv[2];
  if (mode === workerFlag) {
    runWorker(requireArgument(process.argv[3], 'adapter entry point'));
  } else {
    const adapterEntryPoint = requireArgument(mode, 'adapter entry point');
    const forceKillDelayMillis = requirePositiveInteger(process.argv[3], 'force-kill delay');
    const processGroupFile = requireArgument(process.argv[4], 'process-group file');

    runSupervisor(adapterEntryPoint, forceKillDelayMillis, processGroupFile);
  }
}

function runSupervisor(
  adapterEntryPoint: string,
  forceKillDelayMillis: number,
  processGroupFile: string,
): void {
  const supervisorEntryPoint = requireArgument(process.argv[1], 'supervisor entry point');
  const worker = spawn(
    process.execPath,
    [supervisorEntryPoint, workerFlag, adapterEntryPoint],
    {
      detached: true,
      stdio: ['pipe', 'pipe', 'pipe', 'ipc'],
    },
  );
  const workerPid = requirePid(worker.pid, 'projection worker');
  const workerStdin = requireStream(worker.stdin, 'projection worker stdin');
  const workerStdout = requireStream(worker.stdout, 'projection worker stdout');
  const workerStderr = requireStream(worker.stderr, 'projection worker stderr');
  let adapterExitCode: number | undefined;
  let shutdownStarted = false;
  let forceKillTimer: NodeJS.Timeout | undefined;

  writeFileSync(processGroupFile, String(workerPid));

  process.stdin.pipe(workerStdin);
  workerStdout.pipe(process.stdout);
  workerStderr.pipe(process.stderr);

  worker.on('message', (message: unknown) => {
    if (!isAdapterExitMessage(message)) return;

    adapterExitCode = message.code;
    terminateOwnedProcessGroup(workerPid, true);
  });
  worker.on('error', (error: Error) => {
    process.stderr.write(`Failed to start projection worker: ${error.message}\n`);
    adapterExitCode = 1;
  });
  worker.on('close', (code: number | null) => {
    if (forceKillTimer !== undefined) clearTimeout(forceKillTimer);
    removeProcessGroupFile(processGroupFile);

    process.exitCode = adapterExitCode ?? code ?? 1;
  });

  const shutdown = (): void => {
    if (shutdownStarted) return;

    shutdownStarted = true;
    terminateOwnedProcessGroup(workerPid, false);
    forceKillTimer = setTimeout(() => {
      terminateOwnedProcessGroup(workerPid, true);
    }, forceKillDelayMillis);
  };

  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

function runWorker(adapterEntryPoint: string): void {
  const supervisorEntryPoint = requireArgument(process.argv[1], 'supervisor entry point');
  let reported = false;
  const adapter = new Worker(supervisorEntryPoint, {
    workerData: { adapterEntryPoint } satisfies AdapterWorkerData,
    stdin: true,
    stdout: true,
    stderr: true,
  });
  const adapterStdin = requireStream(adapter.stdin, 'projection adapter stdin');
  const adapterStdout = requireStream(adapter.stdout, 'projection adapter stdout');
  const adapterStderr = requireStream(adapter.stderr, 'projection adapter stderr');

  process.stdin.pipe(adapterStdin);
  adapterStdout.pipe(process.stdout);
  adapterStderr.pipe(process.stderr);

  const reportExit = (code: number): void => {
    if (reported) return;

    reported = true;
    const message: AdapterExitMessage = { type: 'adapter-exit', code };
    process.send?.(message);
  };

  adapter.on('error', (error: Error) => {
    process.stderr.write(`Failed to start projection adapter: ${error.message}\n`);
    reportExit(1);
  });
  adapter.on('exit', (code: number) => {
    reportExit(code);
  });

  process.on('SIGINT', () => undefined);
  process.on('SIGTERM', () => undefined);
  process.on('disconnect', terminateOwnProcessGroup);
}

async function runAdapterWorker(adapterEntryPoint: string): Promise<void> {
  try {
    await import(pathToFileURL(adapterEntryPoint).href);
  } finally {
    process.stdin.destroy();
  }
}

function terminateOwnedProcessGroup(pid: number, force: boolean): void {
  if (process.platform === 'win32') {
    const arguments_ = ['/PID', String(pid), '/T'];
    if (force) arguments_.push('/F');

    spawnSync('taskkill', arguments_, {
      stdio: 'ignore',
      windowsHide: true,
    });

    return;
  }

  try {
    process.kill(-pid, force ? 'SIGKILL' : 'SIGTERM');
  } catch (error: unknown) {
    if (!isMissingProcess(error)) throw error;
  }
}

function terminateOwnProcessGroup(): void {
  terminateOwnedProcessGroup(process.pid, true);
}

function isAdapterExitMessage(value: unknown): value is AdapterExitMessage {
  if (value === null || typeof value !== 'object') return false;

  const record = value as Record<string, unknown>;

  return record.type === 'adapter-exit'
    && typeof record.code === 'number'
    && Number.isInteger(record.code);
}

function isMissingProcess(error: unknown): boolean {
  return error instanceof Error
    && 'code' in error
    && error.code === 'ESRCH';
}

function removeProcessGroupFile(processGroupFile: string): void {
  try {
    unlinkSync(processGroupFile);
  } catch (error: unknown) {
    if (!isMissingFile(error)) throw error;
  }
}

function isMissingFile(error: unknown): boolean {
  return error instanceof Error
    && 'code' in error
    && error.code === 'ENOENT';
}

function requireArgument(value: string | undefined, name: string): string {
  if (value === undefined || value.length === 0) fail(`Missing ${name}`);

  return value;
}

function requirePositiveInteger(value: string | undefined, name: string): number {
  const parsed = value === undefined ? Number.NaN : Number(value);
  if (!Number.isInteger(parsed) || parsed <= 0) fail(`Invalid ${name}: ${value ?? '<missing>'}`);

  return parsed;
}

function requirePid(value: number | undefined, name: string): number {
  if (value === undefined) fail(`Missing ${name} PID`);

  return value;
}

function requireStream<T>(value: T | null, name: string): T {
  if (value === null) fail(`Missing ${name}`);

  return value;
}

function requireAdapterWorkerData(value: unknown): AdapterWorkerData {
  if (value === null || typeof value !== 'object') fail('Missing projection adapter worker data');

  const record = value as Record<string, unknown>;
  const adapterEntryPoint = requireArgument(
    typeof record.adapterEntryPoint === 'string' ? record.adapterEntryPoint : undefined,
    'adapter entry point',
  );

  return { adapterEntryPoint };
}

function fail(message: string): never {
  process.stderr.write(`${message}\n`);
  process.exit(1);
}
