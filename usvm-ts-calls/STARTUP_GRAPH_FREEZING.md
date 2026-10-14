# Freeze original class startup graphs

`freeze-class-startup-inputs.py` consumes the unchanged selection ledger and original
project acquisition receipts. It uses the existing `inspect-startup-closure` traversal,
then writes a machine startup manifest with every original class, per-class graph
receipts and shared thin source roots. It does not install dependencies or execute
project code. Build the adapter before invoking the CLI.

```sh
python3 usvm-ts-calls/scripts/freeze-class-startup-inputs.py \
  --ledger /absolute/path/selection-ledger.json \
  --acquisition /absolute/path/manager-retry-attempt \
  --acquisition /absolute/path/priority-attempt \
  --acquisition /absolute/path/secondary-attempt \
  --inspector /absolute/path/fast-check-adapter/dist/src/source-inspector-cli.js \
  --frontend-typescript /absolute/path/pinned-frontend/node_modules/typescript \
  --tool-revision EXACT_40_HEX_BUILD_REVISION \
  --native-frontend-revision bundled:EXACT_FRONTEND_REVISION \
  --output /absolute/path/new-startup-graph-attempt
```

Acquisition arguments are ordered preferences: the first successful frozen receipt
wins; if none succeeded, the first failure remains explicit provenance. A failed
whole devDependency installation does not automatically reject a class. Its graph
can still qualify when every consumed runtime/config/lock input is proven by the
exact original Git revision and all runtime edges are resolved or explicitly recorded
as boundaries. Generated or installed inputs require their immutable acquisition
snapshot. Changed inputs and unresolved static modules retain incomplete rows.

Startup traversal follows actual TS/JS runtime code and literal CommonJS requests.
JSON, native builtins and unresolved dynamic module requests remain explicit boundaries,
including source offsets/expressions where the target cannot be statically resolved.
The graph receipt never claims those boundaries have symbolic models or native replay
support. Checked/native admission remains strict and separate. Initialization and
binding limitations remain diagnostics rather than disappearing from the report.

Original target/import bytes are copied verbatim. Physical installed runtime sources
receive an explicit content-preserving `.usvm-installed` namespace mapping, while
frontend roots contain only the acquired runtime source graph. Project copies are
shared across classes; dependency snapshots are not copied wholesale. Each machine
manifest row freezes its exact frontend config, selected source bytes and a graph
receipt relative to the manifest root. Original installation receipts remain unchanged.

The CLI reads the pinned frontend TypeScript option/lib inventory and compiler SHA.
Startup-only compatibility projects unsupported type-check-only options and newer
library declarations into that frontend environment. Original resolved options,
each before/after boundary and unprojected unknown semantic options are retained.
For example, `lib: ["es2024"]` can use the frontend's `esnext` declarations; this does
not emulate ES2024 runtime operations. Unknown semantic/emission options are preserved
for explicit frontend diagnostics. Checked and native replay configs are unchanged.

`acquisitionComplete` proves the frozen per-class graph, including explicit opaque
boundaries. Genuine machine class entry, useful candidate values and class-only
replay-checked coverage are later, distinct checks. Keep all original rows in those
reports; do not select a new cohort from successful startups.

Focused checks require a built adapter:

```sh
python3 -m unittest discover -s usvm-ts-calls/scripts \
  -p test_freeze_class_startup_inputs.py -v
```
