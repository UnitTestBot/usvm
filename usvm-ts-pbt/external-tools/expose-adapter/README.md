# ExpoSE → External Test Corpus adapter

This package generates a Jalangi-instrumented CommonJS entry point from the
USVM target manifest, runs ExpoSE with a fixed wall-time budget, and converts
ExpoSE's structured `EXPOSE_JSON_PATH` output to ETC. Console text is kept only
as an optional diagnostic log; inputs come from `done[].input`.

ExpoSE is pinned by commit, not by a release (upstream publishes no releases):

```bash
npm run run -- \
  --expose-dir /tmp/ExpoSE \
  --node /tmp/node-v21.7.2/bin/node \
  --z3-library /opt/homebrew/lib/libz3.dylib \
  --commit ec03edf85f883248612b1d498c6a7d9189d16d6f \
  --manifest /tmp/targets.json \
  --method 'HybridSamples.ts::HybridSamples::magic/1' \
  --harness ../jazzer-adapter/fixtures/magic.cjs \
  --harness-env USVM_FIXTURE_METHOD=magic \
  --workdir /tmp/expose-magic --seconds 10 \
  --raw /tmp/expose-magic/raw.json \
  --out /tmp/expose-magic/etc.json --log /tmp/expose-magic/expose.log
```

The harness contract is the same as the Jazzer adapter: export `invoke(args)`
and optionally `toCorpusCase(args)`. Jalangi instruments local modules required
by the generated target, so the target logic may remain in the harness.
`--harness-env NAME=value` is repeatable for a shared dispatching harness.

An ETC corpus from fast-check, Jazzer.js, internal PBT, or USVM can provide the
initial concrete type/value assignment:

```bash
npm run run -- ... --initial-external-inputs /tmp/seeds.jsonl
```

Only the first matching case is used by unmodified ExpoSE because its
Distributor accepts one initial input. Importing a complete seed worklist is a
small upstream-fork candidate; running multiple isolated campaigns is the
correct fallback until that patch is validated.

## Reproducible upstream caveats

The tested upstream commit is
`ec03edf85f883248612b1d498c6a7d9189d16d6f` on Node 21.7.2. Its default
installer is not currently headless/portable on macOS ARM:

- `Analyser/scripts/postinstall` clones `z3javascript` through GitHub SSH;
- setup always installs Browser/Electron 2.0.18, which has no Darwin ARM build;
- `z3javascript` invokes whichever `python3` is first; Python 3.12+ lacks
  `distutils`, while the system Python may need a writable
  `PYTHONPYCACHEPREFIX`;
- the bundled old Z3 snapshot fails under current Apple Clang, while generated
  bindings work with the Homebrew `libz3.dylib` used in the validated run.

Therefore the supported pilot setup is a headless install (top level,
`lib/S$`, `lib/Stats`, `Analyser`, `Distributor` only), HTTPS clone of
`z3javascript`, generated bindings, and an explicit `--z3-library`. These are
the first patches to carry in a fork; Browser/Electron is unrelated to the
Node.js DSE baseline.
