# Acquire frozen class projects

Run `scripts/acquire-class-projects.py` before class startup or coverage measurement.
It reads the unchanged selection ledger, creates independent detached clones at
its exact original revisions, installs locked dependencies, and snapshots the
acquired bytes. It never edits the original cached checkouts.

```sh
python3 usvm-ts-calls/scripts/acquire-class-projects.py \
  --ledger /absolute/path/selection-ledger.json \
  --output /absolute/path/new-acquisition-attempt \
  --jobs 3
```

The output must be a new attempt directory. `--project-id` may select projects for
an explicit retry; keep previous receipts and include every original cohort class
in the final startup report. A failed installation is retained in `projects.json`
and its per-project `acquisition.json`; the process exits nonzero after collecting
all results. Command receipts preserve exit/timeout outcome and hashed logs.

The declared `packageManager` version wins. Otherwise, use the unique original
lockfile, an explicit `--manager-version NAME=VERSION`, an exact corresponding
`engines` version, or the documented acquisition default in the script. Version
ranges are not interpreted as exact pins. Modern Yarn uses the immutable lock
and `--mode=skip-build`; npm, pnpm and Bun disable dependency lifecycle scripts.
The Bun bootstrap extracts only the ordinary binary from its pinned official
platform package and records package, archive and executable hashes.

Generated assets may be acquired using an existing original package script:

```sh
python3 usvm-ts-calls/scripts/acquire-class-projects.py \
  --ledger /absolute/path/selection-ledger.json \
  --output /absolute/path/new-acquisition-attempt \
  --project-id samuelmtimbo--unit \
  --build-script samuelmtimbo--unit=build:system
```

Only explicitly requested script names from the original `package.json` run.
For an original workspace recipe, use `PROJECT=packages/helper#build`; the command
runs in that in-root workspace and records its working directory. Arguments can
follow the declared script, for example `PROJECT=packages/vest-utils#build --package vest-utils`;
they are forwarded as command arguments rather than executed as a separate command. Missing scripts
and escaping workspace paths fail rather than inventing or editing package scripts.
They execute before measurement. Tracked source and lockfile changes fail the
attempt even when a package manager or build script reports success. Generated
untracked files and installed dependencies are captured in the snapshot. Symlinks
must resolve inside the acquired project root.

`phase=FROZEN` proves acquisition and byte freezing. It deliberately leaves
`acquisitionComplete=false`: startup graph resolution must still prove the
consumed configuration/runtime graph and its explicit external boundaries.
The full installation snapshot is shared acquisition evidence, **not** a frontend
root list or a request to lower all devDependencies. A class startup manifest must
select the original target plus its actual runtime source closure, verify every
consumed hash against the snapshot, and record qualification separately.

Installation, generated-asset builds, and package-manager bootstrap are prohibited
inside timed measurement. A missing package, certificate error, changed lock,
unsupported runtime or incomplete graph remains an explicit failure outcome.

Focused CLI checks:

```sh
python3 -m unittest discover -s usvm-ts-calls/scripts \
  -p test_acquire_class_projects.py -v
```
