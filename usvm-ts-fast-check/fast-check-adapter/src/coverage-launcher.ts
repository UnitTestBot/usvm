import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const entryPoint = process.argv[2];
const { NODE_V8_COVERAGE: rawDirectory, ...environment } = process.env;
if (entryPoint === undefined || rawDirectory === undefined || rawDirectory.length === 0) {
  throw new Error('Coverage launcher requires an adapter entry point and NODE_V8_COVERAGE directory');
}

// c8 enables Node's native exit collector in this launcher only. The actual
// source executes in a child whose report is written by our passive collector,
// so Node's shutdown serializer cannot invoke user-installed prototype hooks.
const bootstrap = fileURLToPath(new URL('./execution-coverage-bootstrap.js', import.meta.url));
const child = spawn(process.execPath, ['--enable-source-maps', bootstrap, entryPoint], {
  // Node forwards NODE_V8_COVERAGE even when an explicit env omits it.
  // An empty value disables that special inheritance for this main isolate.
  env: { ...environment, NODE_V8_COVERAGE: '', USVM_V8_COVERAGE_DIRECTORY: rawDirectory },
  stdio: 'inherit',
});
const exitCode = await new Promise<number>((resolve, reject) => {
  child.once('error', reject);
  child.once('close', code => resolve(code ?? 1));
});

process.exit(exitCode);
