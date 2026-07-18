# Pilot: `usvm-ts` vs ExpoSE on a common numeric subset

Date: 2026-07-18.

This is the first runnable external symbolic-engine comparison, not the final
multi-project claim. Both engines analyze the five exported functions in
`usvm-ts-pbt/external-tools/shared-fixtures/symbolic-suite.ts`. Every credited
branch is measured after replay by the concrete EtsIR interpreter.

## Versions and protocol

- EtsIR frontend: jacodb `ba0425006c6942002ff5cb46d0be007b2e57757f`.
- ExpoSE: `ec03edf85f883248612b1d498c6a7d9189d16d6f`, Node 21.7.2,
  system Z3 4.13.3, one worker, five-second per-method cap.
- USVM: current `ts_pbt` worktree, `SYMBOLIC_ONLY`, five-second per-target cap.
- Non-inferiority margin fixed by the plan: -2 absolute percentage points.
- Primary metric: replay-confirmed EtsIR branch-edge coverage.

| Method | EtsIR edges | ExpoSE replay | ExpoSE paths/errors | USVM replay | USVM reached/confirmed |
|---|---:|---:|---:|---:|---:|
| `magic` | 4 | 4 | 3 / 0 | 4 | 3 / 3 |
| `nested` | 4 | 3 | 3 / 1 | 4 | 3 / 3 |
| `interval` | 4 | 4 | 3 / 0 | 4 | 3 / 3 |
| `conjunction` | 2 | 2 | 3 / 0 | 2 | 2 / 2 |
| `quadratic` | 2 | 2 | 2 / 0 | 2 | 2 / 2 |
| **Total** | **16** | **15 (93.75%)** | **14 / 1** | **16 (100%)** | **13 / 13** |

Pilot difference is **+6.25 percentage points for USVM**, so it passes the
predeclared -2 point non-inferiority margin on this subset.

## Concrete findings

1. Both engines solve `magic`: ExpoSE emits `49382`; USVM synthesizes the same
   branch witness and concrete replay confirms it.
2. ExpoSE misses the inner true edge of `nested`. Its solver path attempts the
   rational solution, but the structured input becomes
   `{usvm_arg_0:null, usvm_arg_1:null}` and that path ends with an empty analyser
   JSON/error. USVM emits `33.333333333333336` and `66.66666666666667`; replay
   reaches the edge.
3. ExpoSE/Jalangi + TypeScript source map maps 14 of the 16 manifest edges
   one-to-one. The two `conjunction` edges remain deliberately ambiguous:
   Jalangi exposes two conditional IIDs for `x >= 7 && x <= 9`, both remapped to
   the same TypeScript expression, while the frontend normalizes this to one
   EtsIR `if`. Input replay still confirms both EtsIR edges.
4. USVM reports 13/13 reached targets as replay-confirmed. There are no solver
   or replay losses in this micro-suite.

The recorded engine times were ExpoSE 7.890 s summed across five independent
processes and USVM 1.067 s summed method analysis time. They are **not used for
the decision**: those timers exclude different startup/frontend portions and
need a unified cold/warm runner before publication.

## What this establishes

The current engine is not worse than ExpoSE on the initial numeric common
subset and is strictly better on one edge. It does not yet establish the same
for strings, heap-shaped objects, arrays, exceptions, or real projects. The
next comparison must preserve the same replay metric, use at least ten seeds
where scheduling is stochastic, and report feature gaps separately from
timeouts/misses.
