import { spawn } from 'node:child_process';
import { unlinkSync, writeFileSync } from 'node:fs';
import {
  ProcessGroupShutdown,
  terminateOwnProcessGroup,
  terminateOwnedProcessGroup,
} from './process-group-shutdown.js';

interface CommandExitMessage {
  type: 'command-exit';
  code: number;
}

type Command = [string, ...string[]];

/**
 * Process tree:
 * Kotlin client -> supervisor -> detached group owner -> command -> any descendants.
 * The supervisor stays outside the owned group so it can escalate shutdown. The group owner stays alive over IPC
 * until the command reports its exit, then the supervisor removes every remaining descendant at once.
 */

const commandModeFlag = '--command';
const groupOwnerFlag = '--group-owner';
const MAX_TIMER_DELAY_MILLIS = 2 ** 31 - 1;

runProcess(process.argv.slice(2));

function runProcess(arguments_: string[]): void {
  const mode = requireArgument(arguments_[0], 'supervisor mode');

  if (mode === groupOwnerFlag) {
    runGroupOwner(requireCommand(arguments_.slice(1)));
    return;
  }

  if (mode !== commandModeFlag) fail(`Unknown supervisor mode: ${mode}`);

  const forceKillDelayMillis = requireTimerDelay(arguments_[1], 'force-kill delay');
  const processGroupFile = requireArgument(arguments_[2], 'process-group file');
  const command = requireCommand(arguments_.slice(3));

  runSupervisor(command, forceKillDelayMillis, processGroupFile);
}

function runSupervisor(
  command: Command,
  forceKillDelayMillis: number,
  processGroupFile: string,
): void {
  const shutdown = new ProcessGroupShutdown(forceKillDelayMillis, terminateOwnedProcessGroup);
  installSupervisorSignalHandlers(shutdown);

  const supervisorEntryPoint = requireArgument(process.argv[1], 'supervisor entry point');
  const groupOwner = spawn(
    process.execPath,
    [supervisorEntryPoint, groupOwnerFlag, ...command],
    {
      detached: true,
      stdio: ['pipe', 'pipe', 'pipe', 'ipc'],
    },
  );
  const groupOwnerPid = requirePid(groupOwner.pid, 'group owner');
  const groupOwnerStdin = requireStream(groupOwner.stdin, 'group owner stdin');
  const groupOwnerStdout = requireStream(groupOwner.stdout, 'group owner stdout');
  const groupOwnerStderr = requireStream(groupOwner.stderr, 'group owner stderr');
  let reportedExitCode: number | undefined;

  shutdown.attach(groupOwnerPid);
  writeFileSync(processGroupFile, String(groupOwnerPid));

  process.stdin.pipe(groupOwnerStdin);
  groupOwnerStdout.pipe(process.stdout);
  groupOwnerStderr.pipe(process.stderr);

  groupOwner.on('message', (message: unknown) => {
    if (!isCommandExitMessage(message)) return;

    reportedExitCode = message.code;
    terminateOwnedProcessGroup(groupOwnerPid, 'forceful');
  });
  groupOwner.on('error', (error: Error) => {
    process.stderr.write(`Failed to start process-group owner: ${error.message}\n`);
    reportedExitCode = 1;
  });
  groupOwner.on('close', (code: number | null) => {
    shutdown.cancel();
    removeProcessGroupFile(processGroupFile);

    process.exitCode = reportedExitCode ?? code ?? 1;
  });
}

function installSupervisorSignalHandlers(shutdown: ProcessGroupShutdown): void {
  process.on('SIGINT', () => shutdown.request());
  process.on('SIGTERM', () => shutdown.request());
}

function runGroupOwner(command: Command): void {
  installProcessGroupOwnerHandlers();

  const reportExit = createCommandExitReporter();
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

function createCommandExitReporter(): (code: number) => void {
  let reported = false;

  return (code: number): void => {
    if (reported) return;

    reported = true;
    const message: CommandExitMessage = { type: 'command-exit', code };
    process.send?.(message);
  };
}

function isCommandExitMessage(value: unknown): value is CommandExitMessage {
  if (value === null || typeof value !== 'object') return false;

  const record = value as Record<string, unknown>;

  return record.type === 'command-exit'
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
  if (!(error instanceof Error) || !('code' in error)) return false;

  return error.code === 'ENOENT';
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
  const isInteger = Number.isInteger(parsed);
  const isPositive = parsed > 0;
  const fitsNodeTimer = parsed <= MAX_TIMER_DELAY_MILLIS;
  const valid = isInteger && isPositive && fitsNodeTimer;
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

function fail(message: string): never {
  process.stderr.write(`${message}\n`);
  process.exit(1);
}
