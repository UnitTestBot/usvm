# TypeScript PBT artifact contract v2

This directory freezes the boundary between target discovery, external
generators, and the single Kotlin replay pipeline. All JSON Schemas use draft
2020-12. The Kotlin validator additionally checks cross-field invariants that
JSON Schema cannot express conveniently.

## Canonical encodings

| Artifact | Canonical v2 encoding | Schema |
|---|---|---|
| `target-manifest.json` | one JSON object | `target-manifest.schema.json` |
| `source-targets.jsonl` | one edge object per non-empty line; no header | `source-target-record.schema.json` |
| `method-ids.txt` | one exact stable method ID per line | text validator |
| `run-config.json` | one JSON object | `run-config.schema.json` |
| `*.etc.jsonl` | one header, then one case per non-empty line | `external-test-corpus-record.schema.json` |
| `native-coverage.json` | one JSON object | `native-coverage.schema.json` |
| `run-meta.json` | one JSON object | `run-meta.schema.json` |

A raw adapter output directory contains exactly `corpus.etc.jsonl`,
`native-coverage.json`, `run-meta.json`, and bounded `stderr.log`. It never
contains replay-derived coverage.

## Compatibility and unknown-field policy

- `schemaVersion` is mandatory and must be the JSON integer `2`. Missing,
  malformed, and unknown versions are terminal validation errors.
- Unknown object members are accepted at every nesting level as additive v2
  extensions. The Kotlin decoder ignores them and does not preserve them when
  it re-encodes an artifact. A producer must not rely on unknown-field
  round-trip preservation.
- Unknown discriminator values (`kind`) and contract enums such as
  `mappingStatus`, `cacheMode`, and `exitStatus` are rejected. This prevents an
  extension from being silently interpreted with the wrong semantics.
- Missing required fields and malformed known fields are rejected. Semantic
  identities, ranges, deadlines, aliases, and cross-file producer/timestamp
  consistency are checked by `ArtifactValidator`.
- ETC v2 has only the header-plus-cases JSONL encoding. A JSON document with a
  `cases` array is legacy v1 even when it looks structurally similar; labelling
  that document as v2 is rejected.
- The shared ETC reader accepts v1 documents, arrays, and JSONL for replay
  compatibility. `ExternalTestCorpusV1Converter` is the only writer-side
  migration path. It emits canonical v2 JSONL, uses preserved timing/seed/path
  metadata where present, otherwise sets `generatedAtMs=0` and the explicit
  synthetic path `legacy-v1:<case-id>`. A legacy stream without a versioned
  producer label becomes `legacy-v1@unknown`; its original source label is
  retained in case metadata.

## ETC values

Numbers are strings so `NaN`, `Infinity`, `-Infinity`, and `-0` survive JSON.
Arrays preserve holes. Objects, arrays, maps, sets, callable references, and
alias identity are structured. A receiver/object can carry a constructor plan.
An arbitrary function or cycle that cannot be materialized is represented as
`kind=unrepresentable` with both a reason and an explicit classification.

## CLI

`org.usvm.ts.pbt.external.ArtifactContractCliKt` exposes:

```text
validate <target-manifest|source-targets|method-ids|run-config|etc|native-coverage|run-meta|raw-run> <path>
convert-v1-etc <input> <output>
```

Validation prints one JSON report and exits `0` for valid input, `2` for a
contract rejection, `64` for CLI misuse, and `1` for an unexpected I/O/runtime
failure. Gradle and unified-launcher wiring deliberately remain outside this
work package.
