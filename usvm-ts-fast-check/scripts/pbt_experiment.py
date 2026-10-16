#!/usr/bin/env python3
"""Run frozen four-mode PBT comparisons and regenerate tables from saved evidence."""

import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import random
import signal
import subprocess
import time


MODES = ("PBT_ONLY", "SYMBOLIC_ONLY", "SEQUENTIAL", "COVERAGE_FEEDBACK")
STOP_CRITERION = "first original fault, PBT successful-run quota, symbolic exhaustion, or deadline"
SCRIPT_ROOT = Path(__file__).resolve().parent


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def write_json(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def run_process(command, directory, stem, timeout_seconds):
    """Retain process failures and overruns; never credit their late native output."""
    started = time.monotonic()
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
    timed_out = False
    try:
        stdout, stderr = process.communicate(timeout=max(0.001, timeout_seconds))
    except subprocess.TimeoutExpired:
        timed_out = True
        os.killpg(process.pid, signal.SIGKILL)
        stdout, stderr = process.communicate()
    (directory / f"{stem}.stdout").write_bytes(stdout)
    (directory / f"{stem}.stderr").write_bytes(stderr)
    return {"command": [str(part) for part in command], "exitCode": process.returncode,
            "timedOut": timed_out, "elapsedMillis": int((time.monotonic() - started) * 1000)}


def verify_sources(case):
    root = Path(case["sourceRoot"])
    for relative, expected in case["sourceSha256"].items():
        selected = root / relative
        if Path(relative).is_absolute() or selected.resolve() != selected or root not in selected.parents:
            raise ValueError(f"Unsafe frozen source path: {relative}")
        if digest(selected) != expected:
            raise ValueError(f"Frozen source changed: {relative}")


def verify_runtime(frozen):
    for file, expected in frozen["runtimeSha256"].items():
        if digest(file) != expected:
            raise ValueError(f"Frozen experiment implementation changed: {file}")


def validate_manifest(manifest):
    if manifest["schemaVersion"] != 1 or not manifest["experimentId"]:
        raise ValueError("Experiment identity or schema version is invalid")
    if manifest.get("comparisonKind") not in ("functional-quota", "equal-budget"):
        raise ValueError("Declare whether this is a functional quota check or an equal-budget comparison")
    seeds = manifest["seeds"]
    valid_seeds = seeds and len(set(seeds)) == len(seeds) and all(-(2 ** 31) <= seed < 2 ** 31 for seed in seeds)
    if not valid_seeds:
        raise ValueError("Independent seeds must be distinct signed 32-bit integers")
    budget = manifest["totalBudgetMillis"]
    replay_reserve = manifest["classReplayReserveMillis"]
    validation_reserve = manifest["validationReserveMillis"]
    valid_budget = 0 < replay_reserve < budget and 0 < validation_reserve < budget - replay_reserve \
        and 0 < manifest["pbtAllocationMillis"] < budget - replay_reserve - validation_reserve
    if not valid_budget:
        raise ValueError("PBT, runner validation and class replay allocations must fit the total budget")
    valid_limits = manifest["numRuns"] > 0 and 0 < manifest["pbtInputLimit"] <= 100_000 \
        and manifest["symbolicInputLimit"] > 0 and 1 <= manifest["maxArrayElements"] <= 64 \
        and 1024 <= manifest["maxObservationBytes"] <= 2 * 1024 * 1024
    if not valid_limits:
        raise ValueError("Frozen limits must fit the compact export's 100000-record/64-element/2MiB contract")
    unique_cases = manifest["cases"] and len({case["id"] for case in manifest["cases"]}) == len(manifest["cases"])
    if not unique_cases:
        raise ValueError("The manifest must contain distinct case identities")
    for case in manifest["cases"]:
        safe_id = case["id"] and all(character.isalnum() or character in "-_" for character in case["id"])
        if not safe_id:
            raise ValueError("Case identities must be safe directory names")
        if case["population"] not in ("real", "artificial", "mutant", "correct-control"):
            raise ValueError("Case population must separate real, artificial, mutant and correct controls")
        has_provenance = all(case[key] for key in ("project", "revision", "license", "provenance", "sourceSha256"))
        if not has_provenance:
            raise ValueError("Every case must freeze source hashes, revision, license and provenance")


def freeze(manifest_path, output, distribution, adapter, node):
    manifest = read_json(manifest_path)
    validate_manifest(manifest)
    output.mkdir(parents=True, exist_ok=False)
    runtime_started = time.monotonic()
    runtime_files = sorted((distribution / "lib").glob("*.jar"))
    runtime_files += sorted(path for path in (distribution / "bin").rglob("*") if path.is_file())
    runtime_files += sorted(path for path in (adapter / "dist/src").rglob("*") if path.is_file())
    runtime_files += sorted(path for path in (adapter / "node_modules").rglob("*") if path.is_file())
    runtime_files += [adapter / "package-lock.json", SCRIPT_ROOT / "class-coverage-replay.mjs", Path(__file__).resolve()]
    runtime_hashes = {str(path): digest(path) for path in runtime_files}
    runtime_elapsed = int((time.monotonic() - runtime_started) * 1000)
    cases = []
    for original in manifest["cases"]:
        started = time.monotonic()
        case = dict(original, sourceRoot=str((manifest_path.parent / original["sourceRoot"]).resolve()))
        verify_sources(case)
        directory = output / case["id"]
        directory.mkdir()
        write_json(directory / "prepare-request.json", case)
        process = run_process(
            [node, SCRIPT_ROOT / "class-coverage-replay.mjs", "prepare", adapter,
             directory / "prepare-request.json", directory / "prepared.json"], directory, "prepare", 30,
        )
        prepared = read_json(directory / "prepared.json") if (directory / "prepared.json").exists() else {}
        native_supported = False
        smoke_inputs = case.get("smokeInputs", [])
        if prepared.get("supported") and smoke_inputs:
            smoke_request = dict(case, prepared=prepared, inputs=smoke_inputs)
            write_json(directory / "smoke-request.json", smoke_request)
            smoke_process = run_process(
                [node, SCRIPT_ROOT / "class-coverage-replay.mjs", "replay", adapter,
                 directory / "smoke-request.json", directory / "smoke.json"], directory, "smoke", 30,
            )
            smoke = read_json(directory / "smoke.json") if (directory / "smoke.json").exists() else {}
            native_supported = smoke_process["exitCode"] == 0 and not smoke_process["timedOut"] \
                and len(smoke.get("records", [])) == len(smoke_inputs) \
                and all(record["verified"] for record in smoke.get("records", []))
        frontend_process = run_process(
            [distribution / "bin/usvm-ts-fast-check", "campaign-preflight", directory / "prepare-request.json",
             directory / "frontend.json"], directory, "frontend", 35,
        )
        frontend = read_json(directory / "frontend.json") if (directory / "frontend.json").exists() else {}
        frontend_supported = frontend_process["exitCode"] == 0 and not frontend_process["timedOut"] \
            and frontend.get("supported", False)
        case["preparation"] = {"process": process, "supported": prepared.get("supported", False),
                               "nativeSupported": native_supported, "frontendSupported": frontend_supported,
                               "frontendProcess": frontend_process,
                               "elapsedMillis": int((time.monotonic() - started) * 1000) + runtime_elapsed}
        if "universe" in prepared:
            case["universeSha256"] = prepared["universe"]["sha256"]
            case["totalStatements"] = len(prepared["universe"]["statements"])
        cases.append(case)
    frozen = dict(manifest, cases=cases, runtimeSha256=runtime_hashes, nodeCommand=node,
                  distribution=str(distribution), adapter=str(adapter), stopCriterion=STOP_CRITERION)
    write_json(output / "frozen.json", frozen)
    return frozen


def native_coverage_matches(candidate, native):
    coverage = native.get("coverage") or {}
    identity = coverage.get("identity") or {}
    return native.get("admission") == "ADMITTED" and not native.get("diagnostic") \
        and coverage.get("propertyId") == candidate.get("propertyId") \
        and coverage.get("admittedInvocations") == 1 \
        and coverage.get("provenance", {}).get("request", {}).get("admittedOnly") is True \
        and bool(identity) and identity.get("manifestSha256") == candidate.get("manifestSha256")


def confirmed_symbolic_input(artifact):
    """Consume the existing original-property confirmation; do not confirm raw faults here."""
    result = artifact["result"]
    confirmation = result.get("confirmation") or {}
    candidate = confirmation.get("candidate") or {}
    native = confirmation.get("originalReplay") or {}
    target = candidate.get("target") or {}
    symbolic = result.get("symbolic") or {}
    assertions = artifact["property"].get("assertions", [])
    named_ids = {assertion["id"] for assertion in assertions}
    target_id = target.get("id")
    original_check_matches = target_id in named_ids and (native.get("failure") or {}).get("checkId") == target_id \
        if assertions else target_id == artifact["property"]["predicate"]["exportName"]
    valid = confirmation.get("status") == "CONFIRMED_PROPERTY_VIOLATION" \
        and not confirmation.get("diagnostic") and candidate.get("propertyId") == result.get("propertyId") \
        and target.get("role", "PROPERTY") == "PROPERTY" \
        and original_check_matches and native.get("outcome") in ("FALSE", "THREW") \
        and target.get("expectedOutcome") == native.get("outcome") \
        and native.get("targetMatched") is True and candidate.get("inputs") == symbolic.get("inputs") \
        and candidate.get("inputs") is not None and native_coverage_matches(candidate, native)
    if not valid:
        return None
    return {"inputs": candidate["inputs"], "admission": "ADMITTED", "outcome": native["outcome"]}


def replay_inputs(artifact):
    result = artifact["result"]
    concrete = result.get("concrete")
    omissions = {"pbtDroppedInvocations": 0, "pbtUnavailableInputs": 0, "pbtUnsupportedInputs": 0,
                 "pbtPendingInvocations": 0, "symbolicOmittedInputs": 0,
                 "symbolicUncheckedInputs": 0}
    batches = []
    if concrete is not None:
        export = concrete.get("inputExport")
        observation = concrete.get("observations")
        capture = export if export is not None else observation
        omissions["pbtDroppedInvocations"] = capture.get("droppedInvocations", 0) if capture else 1
        if export is not None:
            omissions["pbtUnsupportedInputs"] = export["unsupportedInputs"]
            omissions["pbtPendingInvocations"] = export["pendingInvocations"]
        records = (export or {}).get("records", []) if export is not None else (observation or {}).get("invocations", [])
        invocations = []
        for invocation in records:
            if invocation["phase"] == "shrink":
                continue
            value = invocation["input"]
            eligible = invocation["phase"] in ("generation", "explicit") and value["status"] == "captured"
            if not eligible or value.get("value", {}).get("kind") != "array":
                omissions["pbtUnavailableInputs"] += 1
                continue
            admission = {"admitted": "ADMITTED", "rejected": "PRECONDITION_REJECTED"}.get(invocation["admission"])
            outcome = {"holds": "HOLDS", "false": "FALSE", "threw": "THREW", "skipped": "SKIPPED"}.get(invocation["outcome"])
            if admission is None or outcome is None:
                omissions["pbtUnavailableInputs"] += 1
                continue
            invocations.append({"inputs": value["value"]["elements"], "admission": admission, "outcome": outcome})
        batches.append({"id": "pbt", "inputs": invocations})
    symbolic = result.get("symbolic")
    if symbolic is not None:
        omissions["symbolicOmittedInputs"] = symbolic.get("omittedCompletedInputs", 0)
        replays = result.get("symbolicInputReplays", [])
        input_key = lambda inputs: json.dumps(inputs, sort_keys=True)
        completed_keys = {input_key(item["inputs"]) for item in symbolic.get("completedInputs", [])}
        replay_keys = {input_key(item["candidate"]["inputs"]) for item in replays}
        omissions["symbolicUncheckedInputs"] = len(completed_keys - replay_keys)
        confirmed = confirmed_symbolic_input(artifact)
        invocations = []
        for replay in replays:
            native = replay["replay"]
            inputs = replay["candidate"]["inputs"]
            if confirmed is not None and inputs == confirmed["inputs"]:
                continue
            eligible = replay["completedWithinDeadline"] and replay.get("predictedOutcomes") == ["HOLDS"] \
                and native["outcome"] == "HOLDS" and native_coverage_matches(replay["candidate"], native)
            if not eligible:
                omissions["symbolicUncheckedInputs"] += 1
                continue
            invocations.append({"inputs": replay["candidate"]["inputs"],
                                "admission": native["admission"], "outcome": native["outcome"]})
        if confirmed is not None:
            invocations.append(confirmed)
        elif symbolic.get("target") is not None and input_key(symbolic.get("inputs")) not in replay_keys:
            omissions["symbolicUncheckedInputs"] += 1
        batches.append({"id": "symbolic", "inputs": invocations, "isolateInputs": True})
    if concrete is None and result["mode"] != "SYMBOLIC_ONLY":
        omissions["pbtUnavailableInputs"] += 1
    return batches, omissions


def full_budget_eligible(summary):
    quota_stopped_pbt = summary["mode"] == "PBT_ONLY" and summary.get("pbtQuotaReached") \
        and not summary["confirmedViolation"]
    return summary["comparisonKind"] == "equal-budget" and summary["primaryEligible"] and not quota_stopped_pbt


def run_cell(frozen, case, seed, mode, output):
    directory = output / case["id"] / str(seed) / mode
    directory.mkdir(parents=True)
    started = time.monotonic()
    preparation_cost = case["preparation"]["elapsedMillis"]
    remaining_budget = max(0, frozen["totalBudgetMillis"] - preparation_cost)
    deadline = started + remaining_budget / 1000
    runner_budget = remaining_budget - frozen["classReplayReserveMillis"]
    summary = {"caseId": case["id"], "project": case["project"], "population": case["population"],
               "seed": seed, "mode": mode, "stopCriterion": STOP_CRITERION, "preparationChargedMillis": preparation_cost,
               "comparisonKind": frozen["comparisonKind"],
               "declaredTotalBudgetMillis": frozen["totalBudgetMillis"], "pbtRunQuota": frozen["numRuns"],
               "totalStatements": case.get("totalStatements"), "universeSha256": case.get("universeSha256"),
               "coveredStatementIds": [], "coverageComplete": False, "confirmedViolation": False}
    try:
        verify_sources(case)
        verify_runtime(frozen)
        if runner_budget <= frozen["validationReserveMillis"] + frozen["pbtAllocationMillis"]:
            raise ValueError("Preparation exhausted the fixed campaign allocation")
        sources = [{"module": relative, "sha256": sha} for relative, sha in case["sourceSha256"].items()]
        configuration = {"seed": seed, "numRuns": frozen["numRuns"], "timeoutMillis": runner_budget,
                         "coverageRequest": {"admittedOnly": True},
                         "inputExportRequest": {"sources": sources,
                                                "maxInvocations": frozen["pbtInputLimit"],
                                                "maxArrayElements": frozen["maxArrayElements"],
                                                "maxBytes": frozen["maxObservationBytes"]}}
        request = {"sourceRoot": case["sourceRoot"], "property": case["property"], "mode": mode,
                   "configuration": configuration, "validationReserveMillis": frozen["validationReserveMillis"],
                   "completedInputLimit": 0 if mode == "PBT_ONLY" else frozen["symbolicInputLimit"],
                   "nodeExecutable": frozen["nodeCommand"], "shrinkWitness": False}
        if mode in ("SEQUENTIAL", "COVERAGE_FEEDBACK"):
            request["pbtAllocationMillis"] = frozen["pbtAllocationMillis"]
        write_json(directory / "request.json", request)
        summary["runnerProcess"] = run_process(
            [Path(frozen["distribution"]) / "bin/usvm-ts-fast-check", "campaign-file",
             directory / "request.json", directory / "campaign.json"], directory, "runner",
            max(0, deadline - time.monotonic() - frozen["classReplayReserveMillis"] / 1000),
        )
        if not (directory / "campaign.json").exists():
            raise ValueError("Runner produced no completed artifact; raw process output is retained")
        artifact = read_json(directory / "campaign.json")
        result = artifact["result"]
        summary["campaignElapsedMillis"] = result["elapsedMillis"]
        confirmation = result.get("confirmation") or {}
        failure = (result.get("concrete") or {}).get("failure") or {}
        summary["confirmedViolation"] = failure.get("kind") == "property" or confirmation.get("status") == "CONFIRMED_PROPERTY_VIOLATION"
        summary["campaignStop"] = result["stop"]
        summary["symbolicStatus"] = (result.get("symbolic") or {}).get("status")
        summary["symbolicDiagnostics"] = (result.get("symbolic") or {}).get("diagnostics", [])
        summary["campaignDiagnostic"] = result.get("diagnostic")
        summary["concreteFailureKind"] = failure.get("kind")
        concrete = result.get("concrete") or {}
        summary["compactCaptureMillis"] = (concrete.get("inputExport") or {}).get("captureTimeMillis", 0)
        summary["pointCaptureMillis"] = (concrete.get("observations") or {}).get("captureTimeMillis", 0)
        summary["pbtRuns"] = concrete.get("numRuns")
        summary["pbtSkips"] = concrete.get("numSkips")
        summary["pbtQuotaReached"] = concrete.get("status") == "success" and concrete.get("numRuns") == frozen["numRuns"]
        symbolic_supported = summary["symbolicStatus"] in (
            "VIOLATION_REACHED", "NO_VIOLATION_REACHED", "PRECONDITION_REJECTED", "TIMEOUT",
        )
        stopped_on_pbt_fault = result.get("symbolic") is None and failure.get("kind") == "property"
        summary["analysisSupported"] = (mode == "PBT_ONLY" or symbolic_supported or stopped_on_pbt_fault) \
            and not summary["symbolicDiagnostics"]
        if summary["confirmedViolation"]:
            summary["findingReturnedByMillis"] = int((time.monotonic() - started) * 1000) + preparation_cost
            summary["confirmedViolationWithinBudget"] = summary["findingReturnedByMillis"] <= frozen["totalBudgetMillis"] \
                and not summary["runnerProcess"]["timedOut"]
        batches, omissions = replay_inputs(artifact)
        summary["omissions"] = omissions
        prepared = read_json(output / case["id"] / "prepared.json")
        if not prepared.get("supported"):
            raise ValueError("Frozen class universe/preflight is unsupported")
        summary["verificationBoundary"] = prepared["verificationBoundary"]
        covered = set()
        verified = True
        replay_processes = []
        for batch in batches:
            if time.monotonic() >= deadline:
                verified = False
                break
            replay_request = dict(case, prepared=prepared, inputs=batch["inputs"],
                                  isolateInputs=batch.get("isolateInputs", False))
            stem = batch["id"]
            write_json(directory / f"{stem}.request.json", replay_request)
            process = run_process(
                [frozen["nodeCommand"], SCRIPT_ROOT / "class-coverage-replay.mjs", "replay", frozen["adapter"],
                 directory / f"{stem}.request.json", directory / f"{stem}.replay.json"],
                directory, stem, deadline - time.monotonic(),
            )
            timely = time.monotonic() < deadline and not process["timedOut"] and process["exitCode"] == 0
            process["batchId"] = stem
            process["creditedWithinDeadline"] = timely
            replay_processes.append(process)
            if not timely:
                verified = False
                break
            receipts = read_json(directory / f"{stem}.replay.json")["records"]
            verified = verified and len(receipts) == len(batch["inputs"]) and all(record["verified"] for record in receipts)
            for receipt in receipts:
                covered.update(receipt["creditedStatementIds"])
        summary["replayProcesses"] = replay_processes
        summary["coveredStatementIds"] = sorted(covered)
        summary["coverageComplete"] = verified and not any(omissions.values()) and not summary["runnerProcess"]["timedOut"]
        preflight_supported = case["preparation"]["nativeSupported"] and case["preparation"]["frontendSupported"]
        summary["preflightSupported"] = preflight_supported
        summary["primaryEligible"] = summary["coverageComplete"] and summary["analysisSupported"] \
            and preflight_supported and prepared["verificationBoundary"] == "SCENARIO_OBSERVATIONS"
        verify_sources(case)
        verify_runtime(frozen)
    except (ValueError, KeyError, OSError) as error:
        summary["diagnostic"] = str(error)
        summary["primaryEligible"] = False
        summary["coverageComplete"] = False
    summary["accountableElapsedMillis"] = int((time.monotonic() - started) * 1000) + preparation_cost
    summary["overrunMillis"] = max(0, summary["accountableElapsedMillis"] - frozen["totalBudgetMillis"])
    if summary["overrunMillis"]:
        summary["primaryEligible"] = False
        summary["coverageComplete"] = False
    summary["nativeReplayMillis"] = sum(process["elapsedMillis"] for process in summary.get("replayProcesses", []))
    summary["fullBudgetEligible"] = full_budget_eligible(summary)
    write_json(directory / "cell.json", summary)
    return summary


def summarize(output):
    frozen = read_json(output / "frozen.json")
    rows = []
    for case in frozen["cases"]:
        for seed in frozen["seeds"]:
            for mode in MODES:
                file = output / case["id"] / str(seed) / mode / "cell.json"
                if not file.exists():
                    rows.append({"caseId": case["id"], "seed": seed, "mode": mode, "diagnostic": "MISSING_CELL"})
                    continue
                row = read_json(file)
                universe_file = output / case["id"] / "prepared.json"
                universe = read_json(universe_file).get("universe", {}) if universe_file.exists() else {}
                known_ids = {statement["id"] for statement in universe.get("statements", [])}
                checked = set()
                for process in row.get("replayProcesses", []):
                    if not process["creditedWithinDeadline"]:
                        continue
                    replay_file = file.parent / f"{process['batchId']}.replay.json"
                    for receipt in read_json(replay_file)["records"]:
                        ids = set(receipt["creditedStatementIds"])
                        if not ids.issubset(known_ids) or (ids and not receipt["verified"]):
                            raise ValueError(f"Native replay extends beyond its checked frozen universe: {replay_file}")
                        checked.update(ids)
                if checked != set(row.pop("coveredStatementIds")):
                    raise ValueError(f"Cached cell count differs from raw native receipts: {file}")
                row["coveredStatements"] = len(checked)
                row.pop("runnerProcess", None)
                row.pop("replayProcesses", None)
                row["omissions"] = json.dumps(row.get("omissions", {}), sort_keys=True)
                row["symbolicDiagnostics"] = json.dumps(row.get("symbolicDiagnostics", []), sort_keys=True)
                rows.append(row)
    columns = sorted(set().union(*(row.keys() for row in rows)))
    with (output / "summary.csv").open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="operation", required=True)
    run = subparsers.add_parser("run")
    run.add_argument("manifest", type=Path)
    run.add_argument("output", type=Path)
    run.add_argument("--distribution", type=Path, required=True)
    run.add_argument("--adapter", type=Path, required=True)
    run.add_argument("--node", default="node")
    table = subparsers.add_parser("summarize")
    table.add_argument("output", type=Path)
    options = parser.parse_args()
    if options.operation == "summarize":
        summarize(options.output.resolve())
        return
    frozen = freeze(options.manifest.resolve(), options.output.resolve(), options.distribution.resolve(),
                    options.adapter.resolve(), options.node)
    for case in frozen["cases"]:
        for seed in frozen["seeds"]:
            modes = list(MODES)
            random.Random(seed).shuffle(modes)
            for mode in modes:
                result = run_cell(frozen, case, seed, mode, options.output.resolve())
                print(case["id"], seed, mode, result.get("symbolicStatus"), len(result["coveredStatementIds"]), flush=True)
    summarize(options.output.resolve())


if __name__ == "__main__":
    main()
