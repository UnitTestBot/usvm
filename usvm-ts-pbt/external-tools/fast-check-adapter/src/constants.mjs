export const SCHEMA_VERSION = 2;
export const TOOL_NAME = "fast-check";
export const TOOL_VERSION = "4.9.0";
export const TOOL_COMMIT = "0d3c2547dce556f72413607849377530d18ea283";
export const ADAPTER_VERSION = "0.2.0";
export const PRODUCER_LABEL = `${TOOL_NAME}@${TOOL_VERSION}`;
export const DEFAULT_LOG_CAP_BYTES = 16 * 1024 * 1024;
export const RAW_ARTIFACT_NAMES = Object.freeze([
  "corpus.etc.jsonl",
  "native-coverage.json",
  "run-meta.json",
  "stderr.log",
]);
