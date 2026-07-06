#!/usr/bin/env python3
"""Aggregate hybrid-analyzer reports into a mode-comparison table.

Usage:
    python3 aggregate.py report-prefix-*.json [--csv out.csv]

Reads one or more HybridReport JSON files (one per analysis mode) and prints a
markdown comparison table; optionally writes per-method rows as CSV for plotting
(coverage timelines are preserved in the raw reports).
"""
import argparse
import csv
import json
import sys
from pathlib import Path


def load(path):
    with open(path) as f:
        return json.load(f)


def summarize(report):
    ms = report["methods"]
    tb = sum(m["totalBranches"] for m in ms)
    cb = sum(m["coveredBranches"] for m in ms)
    ts = sum(m["totalStmts"] for m in ms)
    cs = sum(m["coveredStmts"] for m in ms)
    targets = [t for m in ms if m.get("symbolic") for t in m["symbolic"]["targets"]]
    pbt_failures = sum(len(m["pbt"]["failures"]) for m in ms if m.get("pbt"))
    unsupported = sum(m["pbt"]["unsupported"] for m in ms if m.get("pbt"))
    return {
        "mode": report["config"]["mode"],
        "methods": len(ms),
        "branchCov": 100.0 * cb / tb if tb else 100.0,
        "stmtCov": 100.0 * cs / ts if ts else 100.0,
        "full100": sum(1 for m in ms if m["branchCoverage"] == 1.0),
        "pbtFailures": pbt_failures,
        "unsupported": unsupported,
        "targets": len(targets),
        "reached": sum(1 for t in targets if t["reached"]),
        "replayOk": sum(1 for t in targets if t["replayConfirmed"]),
        "fallbacks": sum(1 for t in targets if t["fallbackUsed"]),
        "targetWallMs": sum(t["wallMs"] for t in targets),
        "wallS": sum(m["totalWallMs"] for m in ms) / 1000.0,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("reports", nargs="+")
    ap.add_argument("--csv", help="write per-method rows to this CSV")
    args = ap.parse_args()

    reports = [load(p) for p in args.reports]
    rows = [summarize(r) for r in reports]

    cols = [
        ("mode", "Mode"), ("methods", "Methods"), ("branchCov", "Branch %"),
        ("stmtCov", "Stmt %"), ("full100", "100% methods"), ("pbtFailures", "PBT failures"),
        ("unsupported", "Unsupported"), ("targets", "Targets"), ("reached", "Reached"),
        ("replayOk", "Replay OK"), ("fallbacks", "Fallbacks"),
        ("targetWallMs", "Target wall ms"), ("wallS", "Total wall s"),
    ]
    print("| " + " | ".join(h for _, h in cols) + " |")
    print("|" + "---|" * len(cols))
    for row in rows:
        cells = []
        for key, _ in cols:
            v = row[key]
            cells.append(f"{v:.1f}" if isinstance(v, float) else str(v))
        print("| " + " | ".join(cells) + " |")

    if args.csv:
        with open(args.csv, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["mode", "method", "totalBranches", "coveredBranches",
                        "branchCoverage", "stmtCoverage", "wallMs",
                        "pbtExecutions", "pbtFailures", "targets", "targetsReached"])
            for r in reports:
                mode = r["config"]["mode"]
                for m in r["methods"]:
                    targets = m["symbolic"]["targets"] if m.get("symbolic") else []
                    w.writerow([
                        mode, m["method"], m["totalBranches"], m["coveredBranches"],
                        f'{m["branchCoverage"]:.4f}', f'{m["stmtCoverage"]:.4f}',
                        m["totalWallMs"],
                        m["pbt"]["executions"] if m.get("pbt") else 0,
                        len(m["pbt"]["failures"]) if m.get("pbt") else 0,
                        len(targets), sum(1 for t in targets if t["reached"]),
                    ])
        print(f"\nCSV written to {args.csv}", file=sys.stderr)


if __name__ == "__main__":
    main()
