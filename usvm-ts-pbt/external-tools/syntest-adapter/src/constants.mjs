export const ADAPTER_NAME = "syntest-javascript";
export const ADAPTER_VERSION = "0.1.0";
export const UPSTREAM_COMMIT = "53547145f16c3ce4eefb8b479b124ba581d70e2d";
export const UPSTREAM_REPOSITORY = "https://github.com/syntest-framework/syntest-javascript";
export const SEARCH_ALGORITHM = "DynaMOSA";

export const RAW_RUN_FILES = Object.freeze([
  "corpus.etc.jsonl",
  "native-coverage.json",
  "run-meta.json",
  "stderr.log",
]);

export const CLASSIFICATION_REASONS = Object.freeze([
  "method-not-in-manifest",
  "no-branches",
  "unsupported-entry-kind",
  "unsupported-parameter-type",
  "unsupported-rest-parameter",
  "source-mapping-missing",
  "source-mapping-not-exact",
  "source-origin-ambiguous",
  "callable-origin-mismatch",
]);

export const RESULT_STATUSES = Object.freeze([
  "success",
  "timeout",
  "failure",
  "unsupported_configuration",
]);

export const DEFAULT_LOG_CAP_BYTES = 1024 * 1024;
