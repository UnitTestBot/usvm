#!/usr/bin/env python3
"""Write per-program/per-mode medians from benchmark JSONL to CSV."""

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path
from statistics import median
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('results', type=Path)
    args = parser.parse_args()
    groups = defaultdict(list)
    for line in args.results.read_text().splitlines():
        row = json.loads(line)
        groups[row['program'], row['mode']].append(row)

    fields = ['program', 'mode', 'runs', 'stops', 'steps', 'elapsedMs', 'selectorMs',
              'selectorPercent', 'refreshMs', 'refreshes', 'distanceCalls', 'peakQueue',
              'covered', 'goals', 'allocatedBytes', 'gcMs']
    writer = csv.DictWriter(sys.stdout, fieldnames=fields, lineterminator='\n')
    writer.writeheader()
    for (program, mode), rows in sorted(groups.items()):
        summary = dict(program=program, mode=mode, runs=len(rows))
        summary['stops'] = ','.join(sorted({row['stop'] for row in rows}))
        for field in fields[4:]:
            if field == 'selectorPercent':
                summary[field] = median(100 * row['selectorMs'] / row['elapsedMs'] for row in rows)
            else:
                summary[field] = median(row[field] for row in rows)
        writer.writerow(summary)


if __name__ == '__main__':
    main()
