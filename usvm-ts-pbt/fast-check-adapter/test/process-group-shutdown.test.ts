import assert from 'node:assert/strict';
import test from 'node:test';
import { ProcessGroupShutdown } from '../src/process-group-shutdown.js';

test('starts a shutdown requested before the process group is attached', () => {
  const terminations: Array<{ pid: number; termination: string }> = [];
  const shutdown = new ProcessGroupShutdown(
    1_000,
    (pid, termination) => terminations.push({ pid, termination }),
  );

  shutdown.request();
  assert.deepEqual(terminations, []);

  shutdown.attach(42);
  assert.deepEqual(terminations, [{ pid: 42, termination: 'graceful' }]);

  shutdown.cancel();
});
