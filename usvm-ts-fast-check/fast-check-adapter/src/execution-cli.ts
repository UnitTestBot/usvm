import { adapterDiagnostic } from './diagnostics.js';
import { startExecutionCoverage } from './execution-coverage.js';
import { stringifyProtocolJson } from './protocol-json.js';
import { executeProperty, type FastCheckExecutionSuccess } from './execute-property.js';
import { executeWitness, type WitnessExecutionSuccess } from './external-witness.js';
import { ProtocolError, protocolError } from './js-value.js';
import type { ProtocolDiagnostic } from './js-value.js';

interface FastCheckExecutionFailure {
  status: 'error';
  diagnostics: ProtocolDiagnostic[];
}

const writeProtocolOutput = process.stdout.write.bind(process.stdout) as typeof process.stdout.write;
process.stdout.write = process.stderr.write.bind(process.stderr) as typeof process.stdout.write;

let request: unknown;
let inputError: unknown;
try { request = parseRequest(await readStdin()); } catch (error) { inputError = error; }
const admittedOnly = request !== null && typeof request === 'object' &&
  (request as Record<string, unknown>).admittedCoverage === true;
const timeoutMillis = request !== null && typeof request === 'object'
  ? (request as Record<string, unknown>).timeoutMillis : undefined;
const coverage = await startExecutionCoverage(admittedOnly, typeof timeoutMillis === 'number' ? timeoutMillis : 60_000);
try {
  let response: FastCheckExecutionSuccess | WitnessExecutionSuccess | FastCheckExecutionFailure;
  try {
    if (inputError !== undefined) throw inputError;
    const isWitness = request !== null && typeof request === 'object' && 'witness' in request;
    response = isWitness ? await executeWitness(request, coverage) : await executeProperty(request, coverage);
  } catch (error: unknown) {
    if (!(error instanceof ProtocolError)) throw error;

    response = protocolErrorResponse(error);
  }

  await coverage?.write();
  await writeResponse(response);
} finally {
  await coverage?.close();
}

process.exit(0);

async function readStdin(): Promise<string> {
  process.stdin.setEncoding('utf8');

  let input = '';
  for await (const chunk of process.stdin) input += chunk;

  return input;
}

function parseRequest(input: string): unknown {
  try {
    return JSON.parse(input) as unknown;
  } catch {
    throw protocolError(
      adapterDiagnostic.protocolJsonInvalid,
      'Standard input is not valid JSON',
      'request',
    );
  }
}

function protocolErrorResponse(error: ProtocolError): FastCheckExecutionFailure {
  return {
    status: 'error',
    diagnostics: [{
      kind: error.kind,
      code: error.code,
      message: error.diagnosticMessage,
      path: error.path,
    }],
  };
}

async function writeResponse(response: FastCheckExecutionSuccess | WitnessExecutionSuccess | FastCheckExecutionFailure): Promise<void> {
  const document = `${stringifyProtocolJson(response)}\n`;

  await new Promise<void>((resolve, reject) => {
    writeProtocolOutput(document, (error?: Error | null) => {
      if (error !== undefined && error !== null) {
        reject(error);
      } else {
        resolve();
      }
    });
  });
}
