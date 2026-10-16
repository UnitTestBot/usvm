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
        and 1024 <= manifest["maxObservationBytes"] <= 64 * 1024 * 1024
    if not valid_limits:
        raise ValueError("Frozen limits must fit the compact export's 100000-record/64-element/64MiB contract")
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


def _same_auxiliary_registration(artifact, candidate):
    property = artifact.get("property") or {}
    property_id = property.get("id")
    manifest = dict(property)
    manifest.pop("id", None)
    manifest["propertyId"] = property_id
    metadata = artifact.get("metadata") or {}
    configured = ((artifact.get("configuration") or {}).get("observationRequest") or {}).get("sources")
    sources = candidate.get("sources")
    if not property_id or candidate.get("propertyId") != property_id \
            or artifact["result"].get("propertyId") != property_id or candidate.get("manifest") != manifest \
            or not isinstance(candidate.get("inputs"), list) or len(candidate["inputs"]) != len(property.get("inputs", [])) \
            or not isinstance(sources, list) or not sources or sources != configured:
        return False
    valid_sources = all(isinstance(source, dict) and isinstance(source.get("module"), str)
                        and isinstance(source.get("sha256"), str) for source in sources)
    if not valid_sources:
        return False
    pins = {source["module"]: source["sha256"] for source in sources}
    selected = metadata.get("sources") or []
    return len(pins) == len(sources) and bool(selected) and isinstance(metadata.get("sourceRoot"), str) \
        and bool(metadata["sourceRoot"]) \
        and all(isinstance(source, dict) and pins.get(source.get("module")) == source.get("sha256") for source in selected)


def _same_auxiliary_target(artifact, attempt, candidate):
    trace = artifact["result"].get("relationGuidance") or {}
    selection = trace.get("selection") or {}
    inference = selection.get("inference") or {}
    selected = attempt.get("selected") or {}
    relation = selected.get("relation") or {}
    pair = relation.get("pair") or {}
    controls = artifact.get("relationGuidanceControls") or {}
    left, right = pair.get("left") or {}, pair.get("right") or {}
    target = candidate.get("target") or {}
    binding = target.get("binding") or {}
    target_id = f'{left.get("assertionId")}:{left.get("id")}:{relation.get("template")}:{right.get("id")}'
    exact = attempt.get("bindingStatus") == "EXACT" and not attempt.get("bindingDiagnostics") \
        and selected in selection.get("candidates", []) and relation in inference.get("relations", []) \
        and relation.get("status") == "SAMPLED" and pair in controls.get("pairs", []) \
        and inference.get("property") == artifact.get("property") \
        and inference.get("propertyId") == candidate.get("propertyId") \
        and inference.get("sources") == candidate.get("sources") \
        and target.get("role") == "HYPOTHESIS" and bool(binding) and target.get("id") == target_id \
        and binding.get("pointId") == target_id and binding.get("occurrence") == 0 \
        and binding.get("revision") == candidate.get("manifestSha256") and bool(binding.get("revision"))
    if not exact:
        return False
    if left.get("kind") == right.get("kind") == "entry-argument":
        expected = {"leftInputIndex": left.get("inputIndex"), "rightInputIndex": right.get("inputIndex"),
                    "template": relation.get("template")}
        return target.get("entryRelation") == expected and target.get("dslNumericLocalRelation") is None \
            and binding.get("contextId") == "original-entry"
    result, original = (left, right) if left.get("compilerLocal") else (right, left)
    compiler = result.get("compilerLocal")
    expected = {"compiler": compiler, "inputIndex": original.get("inputIndex"),
                "resultIsLeft": result == left, "template": relation.get("template")}
    return result.get("kind") == "return" and original.get("kind") == "entry-argument" and bool(compiler) \
        and controls.get("compilerRetained") is True \
        and target.get("dslNumericLocalRelation") == expected and target.get("entryRelation") is None \
        and binding.get("contextId") == "original-compiler-local"


def _confirmed_auxiliary_replay(artifact, attempt):
    confirmation = attempt.get("confirmation") or {}
    candidate = confirmation.get("candidate") or {}
    native = confirmation.get("originalReplay") or {}
    target = candidate.get("target") or {}
    binding = target.get("binding")
    context_matches = bool(binding) and any(
        event.get("binding") == binding and event.get("guard") is True and event.get("holds") is False
        for event in confirmation.get("contextualRefutations", [])
    ) and any(event.get("binding") == binding and event.get("guard") is True and event.get("holds") is False
              for event in native.get("events", []))
    declared_checks = {assertion["id"] for assertion in artifact["property"].get("assertions", [])}
    original_check_matches = not declared_checks or (native.get("failure") or {}).get("checkId") in declared_checks
    original_fault = confirmation.get("status") == "CONFIRMED_PROPERTY_VIOLATION" \
        and native.get("outcome") in ("FALSE", "THREW") and original_check_matches
    hypothesis_refuted = confirmation.get("status") == "CONFIRMED_HYPOTHESIS_REFUTATION" and native.get("outcome") == "HOLDS"
    eligible = attempt.get("completedWithinDeadline") is True and attempt.get("searchStatus") == "REFUTATION_REACHED" \
        and candidate == attempt.get("witness") and candidate.get("inputs") is not None \
        and _same_auxiliary_registration(artifact, candidate) and _same_auxiliary_target(artifact, attempt, candidate) \
        and native.get("admission") == "ADMITTED" and native.get("targetMatched") is True \
        and not native.get("diagnostic") and context_matches and (hypothesis_refuted or original_fault)
    return (candidate, native) if eligible else None


def confirmed_auxiliary_input(artifact, attempt):
    """Use the original confirmed input; the later original/counter replay measures its class coverage."""
    confirmed = _confirmed_auxiliary_replay(artifact, attempt)
    if confirmed is None or (attempt.get("confirmation") or {}).get("diagnostic"):
        return None
    candidate, native = confirmed
    coverage = native.get("coverage") or {}
    metadata = artifact["metadata"]
    source_root = str(Path(metadata["sourceRoot"]).resolve())
    hashes = (coverage.get("identity") or {}).get("sourceSha256") or {}
    sources_match = all(hashes.get(str((Path(source_root) / source["module"]).resolve())) == source["sha256"]
                        for source in candidate["sources"])
    roots_match = coverage.get("provenance", {}).get("sourceRoots") == [source_root]
    if not native_coverage_matches(candidate, native) or not sources_match or not roots_match:
        return None
    return {"inputs": candidate["inputs"], "admission": "ADMITTED", "outcome": native["outcome"]}


def confirmed_original_violation(artifact):
    result = artifact["result"]
    if ((result.get("concrete") or {}).get("failure") or {}).get("kind") == "property":
        return True
    canonical = result.get("confirmation") or {}
    if canonical.get("status") != "CONFIRMED_PROPERTY_VIOLATION":
        return False
    if (canonical.get("candidate") or {}).get("target", {}).get("role") != "HYPOTHESIS":
        return True  # Existing ordinary-control confirmation contract.
    return any(attempt.get("confirmation") == canonical and _confirmed_auxiliary_replay(artifact, attempt) is not None
               for attempt in (result.get("relationGuidance") or {}).get("attempts", []))


def replay_inputs(artifact):
    result = artifact["result"]
    concrete = result.get("concrete")
    omissions = {"pbtDroppedInvocations": 0, "pbtUnavailableInputs": 0, "pbtUnsupportedInputs": 0,
                 "pbtPendingInvocations": 0, "symbolicOmittedInputs": 0,
                 "symbolicUncheckedInputs": 0, "auxiliaryUncheckedInputs": 0}
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
    auxiliary = (result.get("relationGuidance") or {}).get("attempts", [])
    if auxiliary:
        invocations = []
        for attempt in auxiliary:
            has_candidate = attempt.get("witness") is not None or attempt.get("searchStatus") == "REFUTATION_REACHED" \
                or (attempt.get("confirmation") or {}).get("candidate") is not None
            if not has_candidate:
                continue
            confirmed = confirmed_auxiliary_input(artifact, attempt)
            if confirmed is None:
                omissions["auxiliaryUncheckedInputs"] += 1
            else:
                invocations.append(confirmed)
        batches.append({"id": "auxiliary", "inputs": invocations, "isolateInputs": True})
    canonical = result.get("confirmation") or {}
    canonical_auxiliary = (canonical.get("candidate") or {}).get("target", {}).get("role") == "HYPOTHESIS"
    if canonical_auxiliary and not any(attempt.get("confirmation") == canonical for attempt in auxiliary):
        omissions["auxiliaryUncheckedInputs"] += 1
    if concrete is None and result["mode"] != "SYMBOLIC_ONLY":
        omissions["pbtUnavailableInputs"] += 1
    return batches, omissions


def full_budget_eligible(summary):
    quota_stopped_pbt = summary["mode"] == "PBT_ONLY" and summary.get("pbtQuotaReached") \
        and not summary["confirmedViolation"]
    return summary["comparisonKind"] == "equal-budget" and summary["primaryEligible"] and not quota_stopped_pbt


def _relation_summary(result):
    trace = result.get("relationGuidance") or {}
    attempts = trace.get("attempts", [])
    inference = (trace.get("selection") or {}).get("inference") or {}
    diagnostics = list(trace.get("diagnostics", []))
    for attempt in attempts:
        diagnostics.extend(attempt.get("diagnostics", []) + attempt.get("bindingDiagnostics", []))
        diagnostic = (attempt.get("confirmation") or {}).get("diagnostic")
        if diagnostic:
            diagnostics.append(diagnostic)
    relations = inference.get("relations", []) + inference.get("domainImpliedRelations", [])
    return {"relationElapsedMillis": trace.get("elapsedMillis", 0),
            "originalQueryElapsedMillis": trace.get("originalElapsedMillis"),
            "relationStatuses": [attempt.get("searchStatus") for attempt in attempts],
            "relationDiagnostics": diagnostics,
            "relationLimitations": sorted({item for relation in relations for item in relation.get("limitations", [])}),
            "relationOmittedPairs": inference.get("omittedPairs", 0),
            "relationOmittedInvocations": inference.get("omittedInvocations", 0),
            "relationDroppedInvocations": inference.get("droppedInvocations", 0),
            "relationDroppedPoints": inference.get("droppedPoints", 0)}


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
        summary.update(_relation_summary(result))
        failure = (result.get("concrete") or {}).get("failure") or {}
        summary["confirmedViolation"] = confirmed_original_violation(artifact)
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
        stopped_on_original_fault = result.get("symbolic") is None and summary["confirmedViolation"]
        relation_supported = all(status in ("REFUTATION_REACHED", "NO_REFUTATION_REACHED", "PRECONDITION_REJECTED", "TIMEOUT")
                                 for status in summary["relationStatuses"])
        summary["analysisSupported"] = (mode == "PBT_ONLY" or symbolic_supported or stopped_on_original_fault) \
            and not summary["symbolicDiagnostics"] and relation_supported and not summary["relationDiagnostics"]
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
                row["relationDiagnostics"] = json.dumps(row.get("relationDiagnostics", []), sort_keys=True)
                row["relationStatuses"] = json.dumps(row.get("relationStatuses", []))
                row["relationLimitations"] = json.dumps(row.get("relationLimitations", []))
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
