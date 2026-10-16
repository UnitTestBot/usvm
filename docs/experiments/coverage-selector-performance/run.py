#!/usr/bin/env python3
"""Run isolated JVM analyses; keep successes, failures and timeouts in the output directory."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import time


def cached_jar(group, artifact, version):
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    matches = list((cache / group / artifact / version).glob(f'*/{artifact}-{version}.jar'))
    if len(matches) != 1:
        raise RuntimeError(f'Pass the jar explicitly: {group}:{artifact}:{version}')
    return matches[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cases', default='codec,gcd,loan')
    parser.add_argument('--modes', default='existing,128,eager,512')
    parser.add_argument('--steps', type=int, default=100000)
    parser.add_argument('--timeout-ms', type=int, default=120000)
    parser.add_argument('--repetitions', type=int, default=2)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--codec-jar', type=Path)
    parser.add_argument('--guava-jar', type=Path)
    args = parser.parse_args()

    experiment = Path(__file__).resolve().parent
    root = experiment.parents[2]
    output = (args.output or root / 'build/coverage-selector-performance').resolve()
    output.mkdir(parents=True, exist_ok=True)
    codec = args.codec_jar or cached_jar('commons-codec', 'commons-codec', '1.16.0')
    guava = args.guava_jar or cached_jar('com.google.guava', 'guava', '32.0.1-jre')
    jars = os.pathsep.join(str(path.resolve()) for path in [codec, guava])
    cases = args.cases.split(',')
    modes = args.modes.split(',')
    results_file = output / 'results.jsonl'
    outcomes_file = output / 'outcomes.jsonl'

    for repetition in range(1, args.repetitions + 1):
        # Alternate mode order to reduce a fixed ordering effect. Runs remain sequential.
        run_modes = modes if repetition % 2 else list(reversed(modes))
        for case in cases:
            for mode in run_modes:
                label = f'{case}-{mode}-r{repetition}'
                print(f'START {label}', flush=True)
                log_file = output / f'{label}.log'
                command = [
                    str(root / 'gradlew'), ':usvm-jvm:coverageSelectorPerformance',
                    '-I', str(experiment / 'performance.gradle'),
                    f'-PcoveragePerfCase={case}', f'-PcoveragePerfMode={mode}',
                    f'-PcoveragePerfSteps={args.steps}',
                    f'-PcoveragePerfTimeoutMillis={args.timeout_ms}',
                    f'-PcoveragePerfRepetition={repetition}',
                    f'-PcoveragePerfResults={results_file}', f'-PcoveragePerfJars={jars}',
                    '--offline', '--console=plain',
                ]
                started = time.monotonic()
                outcome = dict(program=case, mode=mode, repetition=repetition, log=log_file.name)
                try:
                    with log_file.open('w') as log:
                        process = subprocess.run(
                            command, cwd=root, env=os.environ.copy(), stdout=log,
                            stderr=subprocess.STDOUT, timeout=args.timeout_ms / 1000 + 180,
                        )
                    outcome['exitCode'] = process.returncode
                    outcome['status'] = 'completed' if process.returncode == 0 else 'failed'
                except subprocess.TimeoutExpired:
                    outcome['status'] = 'process-timeout'
                outcome['wallSeconds'] = round(time.monotonic() - started, 3)
                with outcomes_file.open('a') as outcomes:
                    outcomes.write(json.dumps(outcome) + '\n')
                print(json.dumps(outcome), flush=True)
                if outcome['status'] != 'completed':
                    print(log_file.read_text()[-4000:], flush=True)


if __name__ == '__main__':
    main()
