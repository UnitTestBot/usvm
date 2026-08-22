import fc from 'fast-check';
import {
  encodeJsValue,
  protocolError,
} from './js-value.mjs';
import { projectDomain } from './project-domain.mjs';

const PROTOCOL_VERSION = 1;

let parsedRequest;
let response;
try {
  const input = await readStdin();
  try {
    parsedRequest = JSON.parse(input);
  } catch {
    throw protocolError('protocol.json.invalid', 'Standard input is not valid JSON', 'request');
  }
  const request = validateRequest(parsedRequest);
  const arbitrary = fc.tuple(
    ...request.domains.map((domain, index) => projectDomain(domain, `domains[${index}]`)),
  );
  const tuples = fc.sample(arbitrary, {
    seed: request.seed,
    numRuns: request.numSamples,
  });
  response = {
    protocolVersion: PROTOCOL_VERSION,
    requestId: request.requestId,
    status: 'ok',
    samples: tuples.map((tuple) => tuple.map(encodeJsValue)),
  };
} catch (error) {
  response = protocolErrorResponse(error, parsedRequest);
}

process.stdout.write(`${JSON.stringify(response)}\n`);

async function readStdin() {
  process.stdin.setEncoding('utf8');
  let input = '';
  for await (const chunk of process.stdin) input += chunk;
  return input;
}

function validateRequest(request) {
  if (request === null || typeof request !== 'object' || Array.isArray(request)) {
    throw protocolError('protocol.request.invalid', 'Request must be a JSON object', 'request');
  }
  if (request.protocolVersion !== PROTOCOL_VERSION) {
    throw protocolError(
      'protocol.version.unsupported',
      `Unsupported protocol version: ${String(request.protocolVersion)}`,
      'protocolVersion',
    );
  }
  if (request.operation !== 'sample') {
    throw protocolError(
      'protocol.operation.unsupported',
      `Unsupported protocol operation: ${String(request.operation)}`,
      'operation',
    );
  }
  const valid = typeof request.requestId === 'string'
    && request.requestId.length > 0
    && Number.isInteger(request.seed)
    && request.seed >= -0x80000000
    && request.seed <= 0x7fffffff
    && Number.isInteger(request.numSamples)
    && request.numSamples >= 1
    && request.numSamples <= 10_000
    && Array.isArray(request.domains)
    && request.domains.length > 0;
  if (!valid) {
    throw protocolError(
      'protocol.request.invalid',
      'Request requires a non-empty ID and domains, an Int seed, and numSamples in 1..10000',
      'request',
    );
  }
  return request;
}

function protocolErrorResponse(error, request) {
  const code = typeof error?.code === 'string' ? error.code : 'protocol.request.invalid';
  const rawMessage = error instanceof Error ? error.message : String(error);
  const message = rawMessage.startsWith(`${code}: `) ? rawMessage.slice(code.length + 2) : rawMessage;
  const result = {
    protocolVersion: PROTOCOL_VERSION,
    status: 'error',
    diagnostics: [{
      code,
      message,
      path: typeof error?.path === 'string' ? error.path : 'request',
    }],
  };
  if (request !== null && typeof request === 'object' && typeof request.requestId === 'string') {
    result.requestId = request.requestId;
  }
  return result;
}
