import fc from 'fast-check';
import { adapterDiagnostic } from './diagnostics.js';
import {
  encodeJsValue,
  ProtocolError,
  protocolError,
} from './js-value.js';
import type { ProtocolDiagnostic, TaggedJsValue } from './js-value.js';
import { projectDomain } from './project-domain.js';

interface FastCheckProjectionRequest {
  seed: number;
  numSamples: number;
  domains: unknown[];
}

interface FastCheckProjectionSuccess {
  status: 'ok';
  samples: TaggedJsValue[][];
}

interface FastCheckProjectionFailure {
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
    throw protocolError(
      adapterDiagnostic.protocolJsonInvalid,
      'Standard input is not valid JSON',
      'request',
    );
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

  const validSeed = typeof request.seed === 'number'
    && Number.isInteger(request.seed)
    && request.seed >= -0x80000000
    && request.seed <= 0x7fffffff;

  const validSampleCount = typeof request.numSamples === 'number'
    && Number.isInteger(request.numSamples)
    && request.numSamples >= 1;

  const hasDomains = Array.isArray(request.domains) && request.domains.length > 0;

  if (!validSeed || !validSampleCount || !hasDomains) {
    throw protocolError(
      adapterDiagnostic.protocolRequestInvalid,
      'Request requires domains, an Int seed, and a positive numSamples',
      'request',
    );
  }

  return {
    seed: request.seed as number,
    numSamples: request.numSamples as number,
    domains: request.domains as unknown[],
  };
}

function protocolErrorResponse(error: unknown): FastCheckProjectionFailure {
  const protocolFailure = error instanceof ProtocolError ? error : undefined;
  const fallback = adapterDiagnostic.protocolRequestInvalid;

  return {
    status: 'error',
    diagnostics: [{
      kind: protocolFailure?.kind ?? fallback.kind,
      code: protocolFailure?.code ?? fallback.code,
      message: protocolFailure?.diagnosticMessage ?? (error instanceof Error ? error.message : String(error)),
      path: protocolFailure?.path ?? 'request',
    }],
  };
}

function requireRecord(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    throw protocolError(
      adapterDiagnostic.protocolRequestInvalid,
      'Request must be a JSON object',
      'request',
    );
  }

  return value as Record<string, unknown>;
}
