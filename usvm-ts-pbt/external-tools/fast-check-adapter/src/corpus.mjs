import { encodeInput } from "./value-codec.mjs";

export const SCHEMA_VERSION = 1;

export function makeCase({ id, methodId, receiver = undefined, args, metadata = {} }) {
  const encoded = encodeInput(receiver, args);
  return {
    id,
    methodId,
    receiver: encoded.receiver,
    arguments: encoded.arguments,
    metadata: Object.fromEntries(Object.entries(metadata).map(([key, value]) => [key, String(value)])),
  };
}

export function encodeCorpus(corpus, jsonLines = false) {
  if (!jsonLines) return `${JSON.stringify(corpus, null, 2)}\n`;
  const header = JSON.stringify({ schemaVersion: corpus.schemaVersion, producer: corpus.producer });
  return `${[header, ...corpus.cases.map((testCase) => JSON.stringify(testCase))].join("\n")}\n`;
}
