import importlib.util
from pathlib import Path
import struct
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("pbt_experiment", ROOT / "pbt_experiment.py")
driver = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(driver)


def number(value):
    return {"kind": "number", "value": "finite", "bits": struct.pack(">d", value).hex()}


class FakeClock:
    def __init__(self):
        self.now = 100.0

    def monotonic(self):
        return self.now

    def time(self):
        return 1_700_000_000 + self.now - 100

    def advance(self, seconds):
        self.now += seconds


class ExperimentTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name).resolve()
        self.clock = FakeClock()
        self.process_calls = []
        self.cell_count = 0

    def tearDown(self):
        self.temporary.cleanup()


    def test_bounded_pbt_capture_keeps_omissions_and_rejected_inputs(self):
        concrete = {"observations": {"droppedInvocations": 7, "invocations": [
            {"phase": "generation", "input": {"status": "captured", "value": {"kind": "array", "elements": [number(1)]}},
             "admission": "rejected", "outcome": "skipped"},
            {"phase": "generation", "input": {"status": "unsupported"}, "admission": "admitted", "outcome": "holds"},
        ]}}

        batches, omissions = driver.replay_inputs({"result": {"mode": "PBT_ONLY", "concrete": concrete}})

        self.assertEqual(omissions["pbtDroppedInvocations"], 7)
        self.assertEqual(omissions["pbtUnavailableInputs"], 1)
        self.assertEqual(batches[0]["inputs"][0]["admission"], "PRECONDITION_REJECTED")


    def test_frozen_runtime_changes_are_rejected_before_treatment(self):
        implementation = self.root / "implementation.jar"
        implementation.write_bytes(b"frozen")
        frozen = {"runtimeSha256": {str(implementation): driver.digest(implementation)}}
        implementation.write_bytes(b"changed")

        with self.assertRaisesRegex(ValueError, "implementation changed"):
            driver.verify_runtime(frozen)


    def test_process_timeout_retains_raw_output_and_failure(self):
        process = driver.run_process(
            [sys.executable, "-c", "import time; print('started', end='', flush=True); time.sleep(30)"],
            self.root, "slow", timeout_seconds=0.2,
        )

        self.assertTrue(process["timedOut"])
        self.assertNotEqual(process["exitCode"], 0)
        self.assertEqual((self.root / "slow.stdout").read_text(), "started")


    def test_absolute_process_deadline_charges_spawn_time(self):
        child = Mock(returncode=0)
        child.communicate.return_value = (b"completed", b"")

        def spawn(*args, **kwargs):
            self.clock.advance(3)
            return child

        with patch.object(driver.time, "monotonic", self.clock.monotonic), \
                patch.object(driver.subprocess, "Popen", side_effect=spawn):
            process = driver.run_process(
                ["runner"], self.root, "spawn", timeout_seconds=20, deadline=105,
            )

        child.communicate.assert_called_once_with(timeout=2)
        self.assertEqual(process["waitTimeoutMillis"], 2000)
        self.assertEqual(process["elapsedMillis"], 3000)
        self.assertFalse(process["timedOut"])


    def test_relative_process_timeout_preserves_existing_callers(self):
        child = Mock(returncode=0)
        child.communicate.return_value = (b"", b"")

        def spawn(*args, **kwargs):
            self.clock.advance(3)
            return child

        with patch.object(driver.time, "monotonic", self.clock.monotonic), \
                patch.object(driver.subprocess, "Popen", side_effect=spawn):
            process = driver.run_process(["runner"], self.root, "relative", timeout_seconds=5)

        child.communicate.assert_called_once_with(timeout=5)
        self.assertFalse(process["timedOut"])


    def test_spawn_exhausting_deadline_kills_without_a_new_wait_allocation(self):
        child = Mock(pid=1234, returncode=-9)
        child.communicate.side_effect = [driver.subprocess.TimeoutExpired("runner", 0), (b"partial", b"stopped")]

        def spawn(*args, **kwargs):
            self.clock.advance(6)
            return child

        with patch.object(driver.time, "monotonic", self.clock.monotonic), \
                patch.object(driver.subprocess, "Popen", side_effect=spawn), \
                patch.object(driver.os, "killpg") as kill:
            process = driver.run_process(["runner"], self.root, "expired", timeout_seconds=20, deadline=105)

        self.assertEqual(child.communicate.call_args_list[0].kwargs, {"timeout": 0})
        kill.assert_called_once_with(1234, driver.signal.SIGKILL)
        self.assertTrue(process["timedOut"])
        self.assertEqual((self.root / "expired.stdout").read_bytes(), b"partial")


    def test_late_zero_exit_is_still_a_timeout(self):
        child = Mock(returncode=0)

        def communicate(*args, **kwargs):
            self.clock.advance(6)
            return b"late", b""

        child.communicate.side_effect = communicate

        with patch.object(driver.time, "monotonic", self.clock.monotonic), \
                patch.object(driver.subprocess, "Popen", return_value=child):
            process = driver.run_process(["runner"], self.root, "late", timeout_seconds=20, deadline=105)

        self.assertEqual(process["exitCode"], 0)
        self.assertTrue(process["timedOut"])


    def campaign(self, *, fault=False, request=None):
        inputs = [number(1)]
        artifact = {"result": {"mode": "COVERAGE_FEEDBACK", "stop": "COMPLETED",
                           "concrete": {"status": "success", "numRuns": 12,
                                        "observations": {"invocations": [
                                            {"phase": "generation", "admission": "admitted", "outcome": "holds",
                                             "input": {"status": "captured", "value": {"kind": "array", "elements": inputs}}},
                                        ]}},
                           "symbolic": {"status": "NO_VIOLATION_REACHED",
                                        "completedInputs": [{"inputs": inputs, "expectedOutcome": "HOLDS"}]},
                           "symbolicInputReplays": [
                               {"candidate": {"inputs": inputs}, "completedWithinDeadline": True,
                                "predictedOutcomes": ["HOLDS"], "replay": {"admission": "ADMITTED", "outcome": "HOLDS"}},
                           ]}}

        if fault:
            result = artifact["result"]
            result["concrete"]["status"] = "failure"
            result["concrete"]["failure"] = {"kind": "property"}
            result["concrete"]["observations"]["invocations"][0]["outcome"] = "false"
            result["symbolic"] = None
            result.pop("symbolicInputReplays")

        if request is not None:
            artifact.update({key: request[key] for key in (
                "property", "configuration", "validationReserveMillis", "completedInputLimit", "shrinkWitness",
            )})
            artifact["property"] = dict(request["property"], assertions=[])
            artifact["property"]["predicate"] = dict(request["property"]["predicate"], executionKind="sync")
            artifact["configuration"] = dict(request["configuration"], examples=[], exactReplay=False)
            artifact["configuration"]["observationRequest"] = dict(
                request["configuration"]["observationRequest"], maxPointsPerInvocation=8,
            )
            artifact["configuration"]["coverageRequest"] = {
                "scopes": ["source_under_test"], "includePatterns": [], "excludePatterns": [],
                "includeUnexecutedSources": False, "admittedOnly": True,
            }
            artifact["metadata"] = {"sourceRoot": request["sourceRoot"], "requestedNodeCommand": request["nodeExecutable"]}
            if "pbtAllocationMillis" in request:
                artifact["pbtAllocationMillis"] = request["pbtAllocationMillis"]
            result = artifact["result"]
            result["mode"] = request["mode"]
            result["propertyId"] = request["property"]["id"]
            concrete = result["concrete"]
            concrete["propertyId"] = result["propertyId"]
            concrete["seed"] = request["configuration"]["seed"]
            concrete["observations"]["verifiedSources"] = request["configuration"]["observationRequest"]["sources"]
            if result["symbolic"] is not None:
                result["symbolic"]["propertyId"] = result["propertyId"]
                for replay in result["symbolicInputReplays"]:
                    replay["candidate"].update(propertyId=result["propertyId"], sources=concrete["observations"]["verifiedSources"])
            if request["mode"] == "PBT_ONLY":
                result["symbolic"] = None
                result.pop("symbolicInputReplays", None)
            if request["mode"] == "SYMBOLIC_ONLY":
                result["concrete"] = None

        return artifact


    def complete_process(self, command, directory, stem, timeout_seconds, *, deadline=None):
        self.process_calls.append({"stem": stem, "timeout": timeout_seconds, "deadline": deadline})
        response = self.campaign(request=driver.read_json(directory / "request.json")) if stem == "runner" else {"records": [
            {"verified": True, "creditedStatementIds": [stem]},
        ]}
        driver.write_json(command[-1], response)
        self.clock.advance(1)
        return {"exitCode": 0, "timedOut": False}


    def budget_process(self, command, directory, stem, timeout_seconds, **kwargs):
        process = self.complete_process(command, directory, stem, timeout_seconds, **kwargs)
        if stem != "runner":
            return process

        artifact = driver.read_json(command[-1])
        result = artifact["result"]
        if result["concrete"] is not None:
            result["concrete"]["status"] = "failure"
            result["concrete"]["failure"] = {"kind": "timeout"}
        if result["mode"] == "PBT_ONLY":
            result.update(stop="DEADLINE", stoppedAt="PBT")
        elif result["mode"] == "SYMBOLIC_ONLY":
            result.update(stop="DEADLINE", stoppedAt="CONFIRMATION")
            result["symbolic"]["status"] = "TIMEOUT"
        else:
            result["diagnostic"] = "PBT allocation ended; completed prefix retained"
        driver.write_json(command[-1], artifact)
        return dict(process, exitCode=2)


    def invoke_cell(self, process, *, wall_time=None, mode="COVERAGE_FEEDBACK"):
        self.cell_count += 1
        output = self.root / f"output-{self.cell_count}"
        prepared = output / "subject" / "prepared.json"
        prepared.parent.mkdir(parents=True)
        driver.write_json(prepared, {"supported": True, "verificationBoundary": "SCENARIO_OBSERVATIONS"})
        source = self.root / "Subject.ts"
        source.write_text("export function holds(value: number) { return value === value; }", encoding="utf-8")
        case = {"id": "subject", "project": "fixture", "population": "correct-control",
                "sourceRoot": str(self.root), "sourceSha256": {"Subject.ts": driver.digest(source)},
                "property": {"id": "subject.holds",
                             "inputs": [{"name": "value", "domain": {"kind": "integer", "min": -10, "max": 10}}],
                             "predicate": {"module": "Subject.ts", "exportName": "holds"}},
                "preparation": {"elapsedMillis": 2000, "nativeSupported": True, "frontendSupported": True}}
        frozen = {"totalBudgetMillis": 30000, "classReplayReserveMillis": 8000, "validationReserveMillis": 1500,
                  "pbtAllocationMillis": 4000, "numRuns": 12, "pbtInputLimit": 64, "symbolicInputLimit": 16,
                  "maxArrayElements": 64, "maxObservationBytes": 65536, "nodeCommand": "node",
                  "distribution": "/distribution", "adapter": "/adapter", "runtimeSha256": {}}
        self.cell_directory = output / "subject" / "37" / mode
        wall_clock = self.clock.time if wall_time is None else wall_time

        with patch.object(driver.time, "monotonic", self.clock.monotonic), \
                patch.object(driver.time, "time", wall_clock), \
                patch.object(driver, "run_process", side_effect=process):
            return driver.run_cell(frozen, case, seed=37, mode=mode, output=output)


    def test_cell_shares_cutoffs_and_passes_reserves_without_rewriting_controls(self):
        with patch.object(driver, "verify_runtime", side_effect=lambda frozen: self.clock.advance(3)):
            summary = self.invoke_cell(self.complete_process)

        request = driver.read_json(self.cell_directory / "request.json")
        self.assertEqual(request["externalDeadlineEpochMillis"], 1_700_000_020_000)
        self.assertEqual(request["publicationReserveMillis"], 1000)
        self.assertEqual(request["configuration"]["timeoutMillis"], 20000)
        self.assertEqual(request["configuration"]["seed"], 37)
        self.assertEqual(request["configuration"]["numRuns"], 12)
        self.assertEqual(request["validationReserveMillis"], 1500)
        self.assertEqual(request["pbtAllocationMillis"], 4000)
        self.assertEqual(request["completedInputLimit"], 16)
        self.assertFalse(request["shrinkWitness"])
        self.assertEqual([call["deadline"] for call in self.process_calls], [120, 128, 128])
        self.assertEqual([call["timeout"] for call in self.process_calls], [20, 28, 28])
        self.assertTrue(summary["primaryEligible"], summary)
        self.assertEqual(summary["coveredStatementIds"], ["pbt", "symbolic"])


    def test_clock_sampling_delay_cannot_extend_the_monotonic_cutoff(self):
        def delayed_wall_time():
            self.clock.advance(3)
            sampled_wall = self.clock.time()
            self.clock.advance(2)
            return sampled_wall

        summary = self.invoke_cell(self.complete_process, wall_time=delayed_wall_time)

        request = driver.read_json(self.cell_directory / "request.json")
        self.assertEqual(request["externalDeadlineEpochMillis"], 1_700_000_018_000)
        self.assertEqual([call["deadline"] for call in self.process_calls], [120, 128, 128])
        self.assertTrue(summary["primaryEligible"], summary)


    def test_confirmed_original_fault_exit_one_remains_eligible(self):
        def process(command, directory, stem, timeout_seconds, **kwargs):
            result = self.complete_process(command, directory, stem, timeout_seconds, **kwargs)
            if stem == "runner":
                driver.write_json(command[-1], self.campaign(fault=True, request=driver.read_json(directory / "request.json")))
                result["exitCode"] = 1
            return result

        summary = self.invoke_cell(process)

        self.assertTrue(summary["primaryEligible"], summary)
        self.assertTrue(summary["confirmedViolationWithinBudget"])


    def test_pbt_deadline_with_complete_prefix_reaches_native_class_verification(self):
        summary = self.invoke_cell(self.budget_process, mode="PBT_ONLY")

        self.assertEqual(summary["campaignStop"], "DEADLINE")
        self.assertEqual(summary["concreteFailureKind"], "timeout")
        self.assertEqual(summary["runnerProcess"]["exitCode"], 2)
        self.assertEqual([call["stem"] for call in self.process_calls], ["runner", "pbt"])
        self.assertTrue(summary["primaryEligible"], summary)
        self.assertEqual(summary["coveredStatementIds"], ["pbt"])
        self.assertFalse(summary["confirmedViolation"])


    def test_hybrid_pbt_timeout_keeps_diagnostic_and_checks_both_prefixes(self):
        summary = self.invoke_cell(self.budget_process)

        self.assertEqual(summary["campaignStop"], "COMPLETED")
        self.assertEqual(summary["campaignDiagnostic"], "PBT allocation ended; completed prefix retained")
        self.assertEqual(summary["concreteFailureKind"], "timeout")
        self.assertEqual([call["deadline"] for call in self.process_calls], [120, 128, 128])
        self.assertTrue(summary["primaryEligible"], summary)
        self.assertEqual(summary["coveredStatementIds"], ["pbt", "symbolic"])


    def test_symbolic_timeout_with_checked_completed_inputs_keeps_timeout_disposition(self):
        summary = self.invoke_cell(self.budget_process, mode="SYMBOLIC_ONLY")

        self.assertEqual(summary["campaignStop"], "DEADLINE")
        self.assertEqual(summary["symbolicStatus"], "TIMEOUT")
        self.assertEqual([call["stem"] for call in self.process_calls], ["runner", "symbolic"])
        self.assertTrue(summary["primaryEligible"], summary)
        self.assertEqual(summary["coveredStatementIds"], ["symbolic"])


    def test_hybrid_query_timeout_with_complete_prefixes_is_verified(self):
        def process(command, directory, stem, timeout_seconds, **kwargs):
            receipt = self.budget_process(command, directory, stem, timeout_seconds, **kwargs)
            if stem == "runner":
                artifact = driver.read_json(command[-1])
                artifact["result"].update(stop="DEADLINE", stoppedAt="CONFIRMATION")
                artifact["result"]["symbolic"]["status"] = "TIMEOUT"
                driver.write_json(command[-1], artifact)
            return receipt

        summary = self.invoke_cell(process)

        self.assertEqual(summary["symbolicStatus"], "TIMEOUT")
        self.assertEqual(summary["concreteFailureKind"], "timeout")
        self.assertTrue(summary["coverageComplete"], summary)
        self.assertTrue(summary["primaryEligible"], summary)


    def test_budget_stop_does_not_admit_unsafe_or_mismatched_evidence(self):
        mutations = (
            ("execution failure", lambda item: item["result"].update(stop="EXECUTION_FAILURE")),
            ("unknown query", lambda item: item["result"]["symbolic"].update(status="SOLVER_UNKNOWN")),
            ("unsupported query", lambda item: item["result"]["symbolic"].update(status="UNSUPPORTED")),
            ("symbolic diagnostic", lambda item: item["result"]["symbolic"].update(diagnostics=["unsupported operation"])),
            ("contract failure", lambda item: item["result"]["concrete"].update(failure={"kind": "contract"})),
            ("unchecked target", lambda item: item["result"]["symbolic"].update(target={"id": "pending"})),
            ("unconfirmed witness", lambda item: item["result"].update(confirmation={"status": "UNCONFIRMED"})),
            ("dropped inputs", lambda item: item["result"]["concrete"]["observations"].update(droppedInvocations=1)),
            ("pending inputs", lambda item: item["result"]["concrete"]["observations"].update(pendingInvocations=1)),
            ("unsupported inputs", lambda item: item["result"]["concrete"]["observations"].update(unsupportedInputs=1)),
            ("foreign property", lambda item: item["property"].update(id="other.property")),
            ("foreign result", lambda item: item["result"].update(propertyId="other.property")),
            ("foreign source root", lambda item: item["metadata"].update(sourceRoot="/another/source")),
            ("foreign Node command", lambda item: item["metadata"].update(requestedNodeCommand="another-node")),
            ("changed allocation", lambda item: item["configuration"].update(timeoutMillis=30000)),
            ("foreign capture", lambda item: item["result"]["concrete"]["observations"].update(verifiedSources=[])),
            ("foreign native source", lambda item: item["result"]["symbolicInputReplays"][0]["candidate"].update(sources=[])),
            ("unknown native admission", lambda item: item["result"]["symbolicInputReplays"][0]["replay"].update(admission="UNKNOWN")),
            ("native mismatch", lambda item: item["result"]["symbolicInputReplays"][0]["replay"].update(outcome="FALSE")),
            ("changed symbolic prediction", lambda item: item["result"]["symbolic"]["completedInputs"][0].update(expectedOutcome="FALSE")),
            ("late native receipt", lambda item: item["result"]["symbolicInputReplays"][0].update(completedWithinDeadline=False)),
        )
        for reason, mutate in mutations:
            with self.subTest(reason=reason):
                self.clock = FakeClock()
                self.process_calls = []

                def process(command, directory, stem, timeout_seconds, **kwargs):
                    receipt = self.budget_process(command, directory, stem, timeout_seconds, **kwargs)
                    if stem == "runner":
                        artifact = driver.read_json(command[-1])
                        mutate(artifact)
                        driver.write_json(command[-1], artifact)
                    return receipt

                summary = self.invoke_cell(process)

                self.assertFalse(summary["primaryEligible"], summary)
                self.assertFalse(summary["coverageComplete"])
                self.assertEqual(summary["coveredStatementIds"], [])
                self.assertEqual([call["stem"] for call in self.process_calls], ["runner"])


    def test_query_timeout_with_unchecked_completed_inputs_is_rejected(self):
        for wrong_replay_input in (False, True):
            with self.subTest(wrong_replay_input=wrong_replay_input):
                self.clock = FakeClock()
                self.process_calls = []

                def process(command, directory, stem, timeout_seconds, **kwargs):
                    receipt = self.budget_process(command, directory, stem, timeout_seconds, **kwargs)
                    if stem == "runner":
                        artifact = driver.read_json(command[-1])
                        if wrong_replay_input:
                            artifact["result"]["symbolicInputReplays"][0]["candidate"]["inputs"] = [number(2)]
                        else:
                            artifact["result"]["symbolic"]["completedInputs"].append({"inputs": [number(2)], "expectedOutcome": "HOLDS"})
                        driver.write_json(command[-1], artifact)
                    return receipt

                summary = self.invoke_cell(process, mode="SYMBOLIC_ONLY")

                self.assertFalse(summary["primaryEligible"], summary)
                self.assertEqual(summary["coveredStatementIds"], [])
                self.assertEqual([call["stem"] for call in self.process_calls], ["runner"])


    def test_expected_budget_stop_still_needs_timely_successful_class_receipts(self):
        for failure in ("late", "nonzero", "mismatch"):
            with self.subTest(failure=failure):
                self.clock = FakeClock()
                self.process_calls = []

                def process(command, directory, stem, timeout_seconds, **kwargs):
                    receipt = self.budget_process(command, directory, stem, timeout_seconds, **kwargs)
                    if stem == "pbt":
                        if failure == "late":
                            self.clock.advance(27)
                        elif failure == "nonzero":
                            receipt["exitCode"] = 2
                        else:
                            driver.write_json(command[-1], {"records": [{"verified": False, "creditedStatementIds": []}]})
                    return receipt

                summary = self.invoke_cell(process, mode="PBT_ONLY")

                self.assertFalse(summary["primaryEligible"], summary)
                self.assertFalse(summary["coverageComplete"])
                self.assertEqual(summary["coveredStatementIds"], [])
                self.assertEqual([call["deadline"] for call in self.process_calls], [120, 128])


    def test_budget_artifacts_from_late_killed_or_missing_process_results_are_not_admitted(self):
        for failure in ("late", "killed", "missing"):
            with self.subTest(failure=failure):
                self.clock = FakeClock()
                self.process_calls = []

                def process(command, directory, stem, timeout_seconds, **kwargs):
                    receipt = self.budget_process(command, directory, stem, timeout_seconds, **kwargs)
                    if stem == "runner":
                        if failure == "late":
                            self.clock.advance(20)
                        elif failure == "killed":
                            receipt.update(timedOut=True, exitCode=-9)
                        else:
                            command[-1].unlink()
                    return receipt

                summary = self.invoke_cell(process, mode="PBT_ONLY")

                self.assertFalse(summary["primaryEligible"], summary)
                self.assertEqual(summary["coveredStatementIds"], [])
                self.assertEqual([call["stem"] for call in self.process_calls], ["runner"])


    def test_incomplete_killed_and_late_artifacts_keep_diagnostics_without_credit(self):
        for exit_code, timed_out, elapsed in ((2, False, 0), (-9, True, 0), (0, False, 20), (1, False, 20)):
            with self.subTest(exit_code=exit_code, timed_out=timed_out, elapsed=elapsed):
                self.clock = FakeClock()
                self.process_calls = []

                def process(command, directory, stem, timeout_seconds, **kwargs):
                    result = self.complete_process(command, directory, stem, timeout_seconds, **kwargs)
                    artifact = self.campaign(fault=True, request=driver.read_json(directory / "request.json"))
                    artifact["result"]["diagnostic"] = {"message": "retained reason"}
                    driver.write_json(command[-1], artifact)
                    self.clock.advance(elapsed)
                    return dict(result, exitCode=exit_code, timedOut=timed_out)

                summary = self.invoke_cell(process)

                self.assertEqual(summary["campaignDiagnostic"], {"message": "retained reason"})
                self.assertTrue(summary["confirmedViolation"])
                self.assertFalse(summary["confirmedViolationWithinBudget"])
                self.assertFalse(summary["primaryEligible"])
                self.assertFalse(summary["coverageComplete"])
                self.assertEqual(summary["coveredStatementIds"], [])
                self.assertEqual([call["stem"] for call in self.process_calls], ["runner"])
                self.assertTrue((self.cell_directory / "campaign.json").exists())


    def test_exit_one_without_confirmed_fault_is_incomplete(self):
        def process(command, directory, stem, timeout_seconds, **kwargs):
            result = self.complete_process(command, directory, stem, timeout_seconds, **kwargs)
            return dict(result, exitCode=1)

        summary = self.invoke_cell(process)

        self.assertFalse(summary["primaryEligible"])
        self.assertFalse(summary["runnerProcess"]["creditedWithinDeadline"])
        self.assertEqual(summary["coveredStatementIds"], [])


    def test_native_batch_cannot_reset_the_total_cutoff(self):
        def process(command, directory, stem, timeout_seconds, **kwargs):
            result = self.complete_process(command, directory, stem, timeout_seconds, **kwargs)
            if stem == "pbt":
                self.clock.advance(26)
            return result

        summary = self.invoke_cell(process)

        self.assertEqual([call["deadline"] for call in self.process_calls], [120, 128])
        self.assertFalse(summary["replayProcesses"][0]["creditedWithinDeadline"])
        self.assertFalse(summary["coverageComplete"])
        self.assertEqual(summary["coveredStatementIds"], [])


    def test_nonzero_native_batch_does_not_earn_coverage(self):
        def process(command, directory, stem, timeout_seconds, **kwargs):
            result = self.complete_process(command, directory, stem, timeout_seconds, **kwargs)
            if stem == "pbt":
                result["exitCode"] = 2
            return result

        summary = self.invoke_cell(process)

        self.assertFalse(summary["primaryEligible"])
        self.assertFalse(summary["coverageComplete"])
        self.assertEqual(summary["coveredStatementIds"], [])


    def test_invalid_runner_or_native_json_does_not_earn_credit(self):
        for malformed_stem in ("runner", "pbt"):
            with self.subTest(malformed_stem=malformed_stem):
                def process(command, directory, stem, timeout_seconds, **kwargs):
                    result = self.complete_process(command, directory, stem, timeout_seconds, **kwargs)
                    if stem == malformed_stem:
                        command[-1].write_text("{invalid", encoding="utf-8")
                    return result

                summary = self.invoke_cell(process)

                self.assertFalse(summary["primaryEligible"])
                self.assertFalse(summary["coverageComplete"])
                self.assertEqual(summary["coveredStatementIds"], [])
                self.assertIn("diagnostic", summary)



if __name__ == "__main__":
    unittest.main()
