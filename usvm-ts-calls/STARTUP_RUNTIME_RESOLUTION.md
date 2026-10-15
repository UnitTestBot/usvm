# Resolve acquired startup runtime bytes

`FrozenModuleResolver.resolveStartup(specifier, from, importKind)` is an additive
acquisition API. The existing checked/native-replay `resolve` contract is unchanged.
It selects actual in-root TypeScript or JavaScript runtime source, including installed
packages, original generated JavaScript and package export patterns. Import and
require conditions are selected separately; declaration entries never replace runtime.
An explicit package export must exist exactly: missing `.js` exports are not guessed
as `.ts` sources. Relative source imports retain the existing absent-JS/source-TS
mapping, while present JavaScript always wins.

The result is either a source edge or an explicit runtime boundary. Node builtins
are recorded by name, containing source and import kind; JSON boundaries also verify
JSON syntax and freeze the actual JSON bytes. Boundary acquisition does not implement
JSON evaluation, native builtins or their symbolic models. Missing packages, unsupported
formats, declaration-only entries, escaping paths and incompatible context-dependent
aliases still fail explicitly.

`freeze` adds `runtimeBoundaries` only when present, keeps consumed runtime/package/
config/lock hashes, and enables JavaScript in the derived startup frontend configuration.
The config is derived from explicit original compiler settings, rather than defaults
computed by a newer TypeScript version. No imports or original source bytes are edited.

Graph consumers must follow each source edge and retain every boundary diagnostic.
Installed source paths are acquisition identities: they need a content-preserving,
explicit original-to-materialized namespace mapping before frontend lowering, because
physical `node_modules` paths have unstable frontend identities. Do not turn the full
installation snapshot into frontend roots. A complete acquired graph is separate from
engine support, successful class entry and replay-checked coverage.
