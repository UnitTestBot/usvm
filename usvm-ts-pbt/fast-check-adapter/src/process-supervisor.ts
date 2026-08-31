import { spawn } from 'node:child_process';
import { unlinkSync, writeFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { isMainThread, Worker, workerData } from 'node:worker_threads';
import {
  ProcessGroupShutdown,
  terminateOwnProcessGroup,
  terminateOwnedProcessGroup,
} from './process-group-shutdown.js';

interface WorkerExitMessage {
  type: 'worker-exit';
  code: number;
}

interface AdapterWorkerData {
  adapterEntryPoint: string;
}

interface SupervisedWorker {
  label: string;
  arguments: string[];
}

type Command = [string, ...string[]];

/**
 * Process tree:
 * Kotlin client -> supervisor -> detached group owner -> adapter/command -> any descendants.
 * The supervisor stays outside the owned group so it can escalate shutdown. The group owner stays alive over IPC
 * until the adapter or command reports its exit, then the supervisor removes every remaining descendant at once.
 */

const adapterModeFlag = '--adapter';
const commandModeFlag = '--command';
const adapterWorkerFlag = '--adapter-worker';
const commandWorkerFlag = '--command-worker';
const MAX_TIMER_DELAY_MILLIS = 2 ** 31 - 1;

if (!isMainThread) {
  const data = requireAdapterWorkerData(workerData);

  await runAdapterThread(data.adapterEntryPoint);
} else {
  runProcess(process.argv.slice(2));
}

function runProcess(arguments_: string[]): void {
  const mode = requireArgument(arguments_[0], 'supervisor mode');

  if (mode === adapterWorkerFlag) {
    runAdapterWorker(requireArgument(arguments_[1], 'adapter entry point'));
    return;
  }

  if (mode === commandWorkerFlag) {
    runCommandWorker(requireCommand(arguments_.slice(1)));
    return;
  }

  const forceKillDelayMillis = requireTimerDelay(arguments_[1], 'force-kill delay');
  const processGroupFile = requireArgument(arguments_[2], 'process-group file');

  if (mode === adapterModeFlag) {
    const adapterEntryPoint = requireArgument(arguments_[3], 'adapter entry point');
    runSupervisor(
      {
        label: 'adapter worker',
        arguments: [adapterWorkerFlag, adapterEntryPoint],
      },
      forceKillDelayMillis,
      processGroupFile,
    );
    return;
  }

  if (mode === commandModeFlag) {
    runSupervisor(
      {
        label: 'command worker',
        arguments: [commandWorkerFlag, ...requireCommand(arguments_.slice(3))],
      },
      forceKillDelayMillis,
      processGroupFile,
    );
    return;
  }

  fail(`Unknown supervisor mode: ${mode}`);
}

function runSupervisor(
  workerSpec: SupervisedWorker,
  forceKillDelayMillis: number,
  processGroupFile: string,
): void {
  const shutdown = new ProcessGroupShutdown(forceKillDelayMillis, terminateOwnedProcessGroup);
  installSupervisorSignalHandlers(shutdown);

  const supervisorEntryPoint = requireArgument(process.argv[1], 'supervisor entry point');
  const worker = spawn(
    process.execPath,
    [supervisorEntryPoint, ...workerSpec.arguments],
    {
      detached: true,
      stdio: ['pipe', 'pipe', 'pipe', 'ipc'],
    },
  );
  const workerPid = requirePid(worker.pid, workerSpec.label);
  const workerStdin = requireStream(worker.stdin, `${workerSpec.label} stdin`);
  const workerStdout = requireStream(worker.stdout, `${workerSpec.label} stdout`);
  const workerStderr = requireStream(worker.stderr, `${workerSpec.label} stderr`);
  let reportedExitCode: number | undefined;

  shutdown.attach(workerPid);
  writeFileSync(processGroupFile, String(workerPid));

  process.stdin.pipe(workerStdin);
  workerStdout.pipe(process.stdout);
  workerStderr.pipe(process.stderr);

  worker.on('message', (message: unknown) => {
    if (!isWorkerExitMessage(message)) return;

    reportedExitCode = message.code;
    terminateOwnedProcessGroup(workerPid, 'forceful');
  });
  worker.on('error', (error: Error) => {
    process.stderr.write(`Failed to start ${workerSpec.label}: ${error.message}\n`);
    reportedExitCode = 1;
  });
  worker.on('close', (code: number | null) => {
    shutdown.cancel();
    removeProcessGroupFile(processGroupFile);

    process.exitCode = reportedExitCode ?? code ?? 1;
  });
}

function installSupervisorSignalHandlers(shutdown: ProcessGroupShutdown): void {
  process.on('SIGINT', () => shutdown.request());
  process.on('SIGTERM', () => shutdown.request());
}

function runAdapterWorker(adapterEntryPoint: string): void {
  installProcessGroupOwnerHandlers();

  const supervisorEntryPoint = requireArgument(process.argv[1], 'supervisor entry point');
  const reportExit = createWorkerExitReporter();
  const adapter = new Worker(supervisorEntryPoint, {
    workerData: { adapterEntryPoint } satisfies AdapterWorkerData,
    stdin: true,
  });
  const adapterStdin = requireStream(adapter.stdin, 'adapter stdin');

  process.stdin.pipe(adapterStdin);

  adapter.on('error', (error: Error) => {
    process.stderr.write(`Failed to start projection adapter: ${error.message}\n`);
    reportExit(1);
  });
  adapter.on('exit', reportExit);
}

function runCommandWorker(command: Command): void {
  installProcessGroupOwnerHandlers();

  const reportExit = createWorkerExitReporter();
  const child = spawn(command[0], command.slice(1), {
    // Direct inheritance avoids a user-space forwarding buffer that could be truncated when the group is removed.
    stdio: 'inherit',
  });

  child.on('error', (error: Error) => {
    process.stderr.write(`Failed to start supervised command: ${error.message}\n`);
    reportExit(1);
  });
  child.on('exit', (code: number | null) => reportExit(code ?? 1));
}

function installProcessGroupOwnerHandlers(): void {
  // Keep the process-group identity stable while shutdown propagates through the group. If the supervisor disappears,
  // the IPC disconnect is the last reliable opportunity to remove the entire owned group.
  process.on('SIGINT', () => undefined);
  process.on('SIGTERM', () => undefined);
  process.on('disconnect', terminateOwnProcessGroup);
}

function createWorkerExitReporter(): (code: number) => void {
  let reported = false;

  return (code: number): void => {
    if (reported) return;

    reported = true;
    const message: WorkerExitMessage = { type: 'worker-exit', code };
    process.send?.(message);
  };
}

async function runAdapterThread(adapterEntryPoint: string): Promise<void> {
  try {
    await import(pathToFileURL(adapterEntryPoint).href);
  } finally {
    process.stdin.destroy();
  }
}

function isWorkerExitMessage(value: unknown): value is WorkerExitMessage {
  if (value === null || typeof value !== 'object') return false;

  const record = value as Record<string, unknown>;

  return record.type === 'worker-exit'
    && typeof record.code === 'number'
    && Number.isInteger(record.code);
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

function requireCommand(command: string[]): Command {
  const executable = requireArgument(command[0], 'command executable');

  return [executable, ...command.slice(1)];
}

function requireTimerDelay(value: string | undefined, name: string): number {
  const parsed = value === undefined ? Number.NaN : Number(value);
  const valid = Number.isInteger(parsed)
    && parsed > 0
    && parsed <= MAX_TIMER_DELAY_MILLIS;
  if (!valid) fail(`Invalid ${name}: ${value ?? '<missing>'}`);

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
