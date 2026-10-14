# Frozen source resolution regression

`Stats.ts` is copied byte for byte from cubing/cubing.js, revision
`d1d482e2d67f1d21cf3d13d7b32283e84eba7331`, path
`src/sites/experiments.cubing.net/cubing.js/linear-fmc/vendor/timer.cubing.net/Stats.ts`.
Covered by the original repository's [MPL-2.0 license](https://github.com/cubing/cubing.js/blob/d1d482e2d67f1d21cf3d13d7b32283e84eba7331/LICENSE-MPL.md). The test creates an alias/package
consumer in the generated harness, leaving the class and its source offsets unchanged.
This is a source-linked resolution/replay regression, not an admission or representative
coverage claim for the 200-class corpus. The type-only Timer import remains erased.
