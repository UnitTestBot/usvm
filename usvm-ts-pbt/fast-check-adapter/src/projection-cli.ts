import fc from 'fast-check';
import {
  encodeJsValue,
  ProtocolError,
  protocolError,
} from './js-value.js';
import type { ProtocolDiagnostic, TaggedJsValue } from './js-value.js';
import { projectDomain } from './project-domain.js';

const PROTOCOL_VERSION = 1;

interface FastCheckProjectionRequest {
  protocolVersion: 1;
  seed: number;
  numSamples: number;
  domains: unknown[];
}

interface FastCheckProjectionSuccess {
  protocolVersion: 1;
  status: 'ok';
  samples: TaggedJsValue[][];
}

interface FastCheckProjectionFailure {
  protocolVersion: 1;
  status: 'error';
  diagnostics: ProtocolDiagnostic[];
}

type FastCheckProjectionWireResponse = FastCheckProjectionSuccess | FastCheckProjectionFailure;

let response: FastCheckProjectionWireResponse;
try {
  const input = await readStdin();
  let parsedRequest: unknown;
  try {
    parsedRequest = JSON.parse(input) as unknown;
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
    status: 'ok',
    samples: tuples.map((tuple) => tuple.map(encodeJsValue)),
  };
} catch (error: unknown) {
  response = protocolErrorResponse(error);
}

process.stdout.write(`${JSON.stringify(response)}\n`);

async function readStdin(): Promise<string> {
  process.stdin.setEncoding('utf8');
  let input = '';
  for await (const chunk of process.stdin) input += chunk;
  return input;
}

function validateRequest(value: unknown): FastCheckProjectionRequest {
  const request = requireRecord(value);
  if (request.protocolVersion !== PROTOCOL_VERSION) {
    throw protocolError(
      'protocol.version.unsupported',
      `Unsupported protocol version: ${String(request.protocolVersion)}`,
      'protocolVersion',
    );
  }
  const validSeed = typeof request.seed === 'number'
    && Number.isInteger(request.seed)
    && request.seed >= -0x80000000
    && request.seed <= 0x7fffffff;
  const validSampleCount = typeof request.numSamples === 'number'
    && Number.isInteger(request.numSamples)
    && request.numSamples >= 1
    && request.numSamples <= 10_000;
  const hasDomains = Array.isArray(request.domains) && request.domains.length > 0;
  if (!validSeed || !validSampleCount || !hasDomains) {
    throw protocolError(
      'protocol.request.invalid',
      'Request requires domains, an Int seed, and numSamples in 1..10000',
      'request',
    );
  }
  return {
    protocolVersion: PROTOCOL_VERSION,
    seed: request.seed as number,
    numSamples: request.numSamples as number,
    domains: request.domains as unknown[],
  };
}

function protocolErrorResponse(error: unknown): FastCheckProjectionFailure {
  const protocolFailure = error instanceof ProtocolError ? error : undefined;
  return {
    protocolVersion: PROTOCOL_VERSION,
    status: 'error',
    diagnostics: [{
      code: protocolFailure?.code ?? 'protocol.request.invalid',
      message: protocolFailure?.diagnosticMessage ?? (error instanceof Error ? error.message : String(error)),
      path: protocolFailure?.path ?? 'request',
    }],
  };
}

function requireRecord(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw protocolError('protocol.request.invalid', 'Request must be a JSON object', 'request');
  }
  return value as Record<string, unknown>;
}
