import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const supervisorPath = fileURLToPath(new URL('../src/projection-supervisor.js', import.meta.url));

test('adapter runs inside the stable process-group owner', { timeout: 3_000 }, async () => {
  const workspace = await mkdtemp(path.join(tmpdir(), 'usvm-projection-supervisor-'));
  const adapterPath = path.join(workspace, 'adapter.mjs');
  const adapterPidFile = path.join(workspace, 'adapter.pid');
  const processGroupFile = path.join(workspace, 'process-group.pid');
  await writeFile(
    adapterPath,
    `import { writeFileSync } from 'node:fs';\n`
      + `writeFileSync(${JSON.stringify(adapterPidFile)}, String(process.pid));\n`
      + `setInterval(() => undefined, 1000);\n`,
  );
  const supervisor = spawn(
    process.execPath,
    [supervisorPath, adapterPath, '25', processGroupFile],
    { stdio: 'ignore' },
  );
  const supervisorExit = new Promise<void>((resolve) => supervisor.once('close', () => resolve()));

  try {
    const [adapterPid, processGroupPid] = await Promise.all([
      readTextEventually(adapterPidFile),
      readTextEventually(processGroupFile),
    ]);

    assert.equal(adapterPid, processGroupPid);
  } finally {
    supervisor.kill('SIGTERM');
    await Promise.race([
      supervisorExit,
      delay(2_000).then(() => supervisor.kill('SIGKILL')),
    ]);
    await rm(workspace, { recursive: true, force: true });
  }
});

test('command mode owns descendants that retain inherited pipes', { timeout: 10_000 }, async () => {
  const workspace = await mkdtemp(path.join(tmpdir(), 'usvm-command-supervisor-'));
  const adapterPath = path.join(workspace, 'adapter.mjs');
  const childPidFile = path.join(workspace, 'child.pid');
  const processGroupFile = path.join(workspace, 'process-group.pid');
  await writeFile(
    adapterPath,
    `import { spawn } from 'node:child_process';\n`
      + `import { writeFileSync } from 'node:fs';\n`
      + `const child = spawn(process.execPath, ['-e', 'setInterval(() => undefined, 1000)'], `
      + `{ stdio: ['ignore', 'inherit', 'inherit'] });\n`
      + `writeFileSync(${JSON.stringify(childPidFile)}, String(child.pid));\n`
      + `child.unref();\n`,
  );
  const supervisor = spawn(
    process.execPath,
    [supervisorPath, '--command', '25', processGroupFile, process.execPath, adapterPath],
    { stdio: 'ignore' },
  );
  const supervisorExit = new Promise<void>((resolve) => supervisor.once('close', () => resolve()));
  let childPid: number | undefined;

  try {
    const [childPidText, processGroupPidText] = await Promise.all([
      readTextEventually(childPidFile),
      readTextEventually(processGroupFile),
    ]);
    childPid = Number(childPidText);

    assert.notEqual(childPidText, processGroupPidText);
    assert.equal(supervisor.exitCode, null);

    supervisor.kill('SIGTERM');
    await supervisorExit;

    assert.equal(isProcessAlive(childPid), false);
  } finally {
    supervisor.kill('SIGKILL');
    if (childPid !== undefined) terminateProcess(childPid);
    await rm(workspace, { recursive: true, force: true });
  }
});

async function readTextEventually(file: string): Promise<string> {
  const deadline = Date.now() + 2_000;

  while (true) {
    try {
      return await readFile(file, 'utf8');
    } catch (error: unknown) {
      if (!isMissingFile(error) || Date.now() >= deadline) throw error;
    }

    await delay(10);
  }
}

function isMissingFile(error: unknown): boolean {
  return error instanceof Error
    && 'code' in error
    && error.code === 'ENOENT';
}

function isProcessAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);

    return true;
  } catch (error: unknown) {
    if (isMissingProcess(error)) return false;

    throw error;
  }
}

function terminateProcess(pid: number): void {
  try {
    process.kill(pid, 'SIGKILL');
  } catch (error: unknown) {
    if (!isMissingProcess(error)) throw error;
  }
}

function isMissingProcess(error: unknown): boolean {
  return error instanceof Error
    && 'code' in error
    && error.code === 'ESRCH';
}
