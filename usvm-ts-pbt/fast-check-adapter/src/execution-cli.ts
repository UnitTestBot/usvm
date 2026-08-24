import {
  executeProperty,
  FAST_CHECK_EXECUTION_PROTOCOL_VERSION,
  type FastCheckExecutionSuccess,
} from './execute-property.js';
import { ProtocolError, protocolError } from './js-value.js';
import type { ProtocolDiagnostic } from './js-value.js';

interface FastCheckExecutionFailure {
  protocolVersion: number;
  status: 'error';
  diagnostics: ProtocolDiagnostic[];
}

const writeProtocolOutput = process.stdout.write.bind(process.stdout) as typeof process.stdout.write;
process.stdout.write = process.stderr.write.bind(process.stderr) as typeof process.stdout.write;

let response: FastCheckExecutionSuccess | FastCheckExecutionFailure;
try {
  const request = parseRequest(await readStdin());

  response = await executeProperty(request);
} catch (error: unknown) {
  if (!(error instanceof ProtocolError)) throw error;

  response = protocolErrorResponse(error);
}

await writeResponse(response);
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
    throw protocolError('protocol.json.invalid', 'Standard input is not valid JSON', 'request');
  }
}

function protocolErrorResponse(error: ProtocolError): FastCheckExecutionFailure {
  return {
    protocolVersion: FAST_CHECK_EXECUTION_PROTOCOL_VERSION,
    status: 'error',
    diagnostics: [{
      code: error.code,
      message: error.diagnosticMessage,
      path: error.path,
    }],
  };
}

async function writeResponse(response: FastCheckExecutionSuccess | FastCheckExecutionFailure): Promise<void> {
  const document = `${JSON.stringify(response)}\n`;

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
