# Coverage selector performance on real JVM bytecode

This experiment drives the production `JcInterpreter` through `UMachine.run`, with the coverage-guided selector installed directly in the execution loop. It does not use mock states. It does not call the public `JcMachine.analyze` wrapper or concretely replay generated inputs.

## Recorded results (2026-10-16)

The periodic selector remains expensive on these programs. Most measured selector time is outside full refreshes: every ordinary state update still scans the entire goal snapshot through the per-target distance callback. Increasing the interval to 512 does not remove that cost.

The retained dataset contains 16 runs and 1,328,129 interpreter steps. Commons Codec has two fresh-process repetitions per mode; Guava and LoanExam have one run per mode. Numeric entries below are per-metric medians where there are two runs.

| Program | Mode | Steps | Stop | Analysis seconds | Selector seconds | Selector share | Refresh ms |
| --- | --- | ---: | --- | ---: | ---: | ---: | ---: |
| Codec | existing | 100,000 | steps | 17.145 | 0.134 | 0.78% | 0 |
| Codec | eager | 100,000 | steps | 15.300 | 4.284 | 27.96% | 13.86 |
| Codec | 128 | 100,000 | steps | 21.468 | 6.319 | 29.48% | 90.32 |
| Codec | 512 | 100,000 | steps | 19.110 | 5.842 | 30.61% | 146.87 |
| Guava gcd | existing | 61,538 | timeout | 120.167 | 0.235 | 0.20% | 0 |
| Guava gcd | eager | 57,141 | timeout | 120.024 | 10.822 | 9.02% | 5.36 |
| Guava gcd | 128 | 63,808 | timeout | 120.189 | 14.249 | 11.86% | 5.73 |
| Guava gcd | 512 | 51,566 | timeout | 120.269 | 8.584 | 7.14% | 3.48 |
| LoanExam | existing | 73,519 | exhausted | 2.227 | 0.069 | 3.08% | 0 |
| LoanExam | eager | 73,519 | exhausted | 2.003 | 0.248 | 12.38% | 29.85 |
| LoanExam | 128 | 73,519 | exhausted | 2.333 | 0.266 | 11.39% | 27.08 |
| LoanExam | 512 | 73,519 | exhausted | 2.319 | 0.244 | 10.51% | 16.97 |

For interval 128:

| Program | Initial goals | Covered goals | Peak queued states | Per-target distance calls | Analysis-thread allocated GB (decimal) |
| --- | ---: | ---: | ---: | ---: | ---: |
| Codec | 273 | 74 | 3,364 | 23,826,680 | 5.952 |
| Guava gcd | 673 | 17 | 3,022 | 43,862,759 | 10.762 |
| LoanExam | 126 | 123 | 4,378 | 886,279 | 0.717 |

All modes reached the same final covered-goal count within each program in these runs. Queue order and solver work still differed. For example, Guava interval 128 made more steps than `existing` within the same time budget despite spending much more time in the selector. The measurements do not establish that the complete analysis is always slower.

Codec whole-analysis times varied substantially between repetitions: interval 128 took 15.007–27.930 seconds, and `existing` took 14.066–20.224 seconds. Its per-target callback count was identical in both repetitions, and its selector share was 29.33–29.62%. Do not treat the median wall-time ratio as a stable speedup estimate.

Coverage-guided runs produced only 1–26 peeks with distance refresh work per analysis. They do not establish the worst-case cost of continuously discovering new goals or changing coverage while the queue is large. Initial goals are static; dynamic `addTargets` performance is not exercised.

A useful next change is a caller-supplied batched minimum-distance function over the snapshot, allowing domain-specific caches such as the aggregate `CallStackDistanceCalculator`. A generic per-target callback may depend on arbitrary mutable state, so the selector cannot safely introduce a statement/call-stack-only cache for every caller. The proposed batched API itself has not been implemented or benchmarked here.

The production selector files match the recorded source hashes throughout the retained runs. The manifest records the final reproducible harness; setup-helper extraction and report formatting were refined between exploratory runs and the repeated Codec runs, without changes to the measured execution loop, selector algorithms or settings.

Raw data: [results.jsonl](results.jsonl). Full median summary: [summary.csv](summary.csv). Source/settings checksums: [manifest.json](manifest.json).

Validation after the HashSet change: `:usvm-core:check` passed (274 tests passed, one existing skipped test). Strict Detekt on the changed core main/tests and the manual JVM experiment reported no findings. All 16 retained experiment processes completed successfully; Guava analyses ended at the explicit time limit. The experiment is excluded from ordinary JVM test runs.

## Programs and setup

- Apache Commons Codec 1.16.0: `org.apache.commons.codec.binary.BinaryCodec.toAsciiBytes(byte[])`.
- Guava 32.0.1-jre: `com.google.common.math.IntMath.gcd(int, int)`.
- Repository sample: `org.usvm.samples.psbenchmarks.LoanExam.getCreditPercent(CreditRequest)`.

Coverage goals are the statements of all declared, non-abstract, non-native, non-constructor methods in the entry point's class, including static initialization. Coverage is reported by the standard `CoverageStatistics` observer for terminated reachable symbolic states. Class-wide goals intentionally include methods not visited by a particular entry point.

Each analysis allows 100,000 interpreter steps or 120 seconds, whichever comes first. The timeout is checked between steps, so an in-flight solver operation may overshoot. Reaching the time limit is recorded as `stop=timeout`, not treated as exhaustive analysis. There is no early stop for coverage saturation or coverage stagnation, and no loop-iteration cap.

Settings: Yices, 250 ms solver/type-operation timeout, implicit exceptions enabled, 1,500-element array bound, soft constraints disabled, 4 GiB Java heap, assertions disabled. Each run uses a fresh test JVM. JacoDB/classpath setup and Gradle startup are outside the analysis timer; initial queue insertion and the complete execution loop are inside it. These are cold-start analysis measurements, not JMH steady-state measurements.

The recorded host was an Apple M3 Pro (arm64), 18 GiB RAM, Zulu JDK 11.0.32.1+1. Runs were sequential; other host activity was not controlled.

## Selectors

- `existing`: the aggregate `CallStackDistanceCalculator` / `WeightedPathSelector` strategy used by `CLOSEST_TO_UNCOVERED`. Coverage reports remove goals from that calculator. Queued state weights are refreshed on normal updates, without a global rebuild.
- `eager`: the original #526 algorithm, refreshing via `drain().toList()` and re-adding states after changed coverage. It uses the same cached-ordering fix and queue implementation as the periodic variants.
- `128` and `512`: the new `CoverageGuidedPathSelector`, using `HashSet` snapshots and the respective refresh interval.

The variants may explore different paths. Whole-analysis times, allocations and step counts therefore include differences in solver work and exploration order; they are not isolated measurements of the queue implementation.

`selectorMs` measures add/update/remove/peek and coverage-report handling, including distance evaluation. `refreshMs` identifies distance work inside `peek` and includes heap rebuilding plus selecting its root. `distanceCalls` counts per-target callbacks in eager/periodic modes, but aggregate calculator calls in `existing`; those are different units of work. `allocatedBytes` is all allocation on the analysis thread, not retained heap, native memory, or allocation attributed specifically to the selector.

## Reproduction

Use JDK 11 and cached project dependencies. The runner locates the two library jars in the Gradle cache, or accepts explicit `--codec-jar` and `--guava-jar` paths. It runs Gradle offline and retains per-run logs, raw JSONL measurements and process outcomes.

```sh
JAVA_HOME=/path/to/jdk11 python3 docs/experiments/coverage-selector-performance/run.py \
  --cases codec,gcd,loan --modes existing,128,eager,512 \
  --steps 100000 --timeout-ms 120000 --repetitions 1 \
  --output build/coverage-selector-performance

python3 docs/experiments/coverage-selector-performance/summarize.py \
  build/coverage-selector-performance/results.jsonl
```

The experiment is tagged `manual` and excluded from ordinary test runs. The runner's dedicated Gradle task selects only this experiment and disables assertions, avoiding the debug duplicate-element scan in queue insertion.

Library SHA-256:

- Commons Codec: `56595fb20b0b85bc91d0d503dad50bb7f1b9afc0eed5dffa6cbb25929000484d`.
- Guava: `bd7fa227591fb8509677d0d1122cf95158f3b8a9f45653f58281d879f6dc48c5`.
