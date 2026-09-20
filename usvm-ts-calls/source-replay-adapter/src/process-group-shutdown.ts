import { spawnSync } from 'node:child_process';

export type ProcessGroupTermination = 'graceful' | 'forceful';
export type ProcessGroupTerminator = (pid: number, termination: ProcessGroupTermination) => void;

/** Coordinates a two-phase shutdown even when the signal arrives before spawn returns a PID. */
export class ProcessGroupShutdown {
  private processGroupPid: number | undefined;
  private shutdownRequested = false;
  private shutdownStarted = false;
  private forceKillTimer: NodeJS.Timeout | undefined;

  constructor(
    private readonly forceKillDelayMillis: number,
    private readonly terminate: ProcessGroupTerminator,
  ) {}

  attach(processGroupPid: number): void {
    if (this.processGroupPid !== undefined) throw new Error('Process group is already attached');

    this.processGroupPid = processGroupPid;
    this.startIfReady();
  }

  request(): void {
    this.shutdownRequested = true;
    this.startIfReady();
  }

  cancel(): void {
    if (this.forceKillTimer !== undefined) clearTimeout(this.forceKillTimer);
  }

  private startIfReady(): void {
    if (!this.shutdownRequested || this.shutdownStarted || this.processGroupPid === undefined) return;

    const processGroupPid = this.processGroupPid;
    this.shutdownStarted = true;
    this.terminate(processGroupPid, 'graceful');
    this.forceKillTimer = setTimeout(() => {
      this.terminate(processGroupPid, 'forceful');
    }, this.forceKillDelayMillis);
  }
}

/** Terminates a detached worker together with every process that it owns. */
export function terminateOwnedProcessGroup(pid: number, termination: ProcessGroupTermination): void {
  const force = termination === 'forceful';

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

export function terminateOwnProcessGroup(): void {
  terminateOwnedProcessGroup(process.pid, 'forceful');
}

function isMissingProcess(error: unknown): boolean {
  return error instanceof Error
    && 'code' in error
    && error.code === 'ESRCH';
}
