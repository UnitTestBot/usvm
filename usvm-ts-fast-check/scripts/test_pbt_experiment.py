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


    def campaign(self, *, fault=False):
        inputs = [number(1)]
        artifact = {"result": {"mode": "COVERAGE_FEEDBACK", "stop": "COMPLETED",
                           "concrete": {"status": "success", "numRuns": 12,
                                        "observations": {"invocations": [
                                            {"phase": "generation", "admission": "admitted", "outcome": "holds",
                                             "input": {"status": "captured", "value": {"kind": "array", "elements": inputs}}},
                                        ]}},
                           "symbolic": {"status": "NO_VIOLATION_REACHED", "completedInputs": [inputs]},
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

        return artifact


    def complete_process(self, command, directory, stem, timeout_seconds, *, deadline=None):
        self.process_calls.append({"stem": stem, "timeout": timeout_seconds, "deadline": deadline})
        response = self.campaign() if stem == "runner" else {"records": [
            {"verified": True, "creditedStatementIds": [stem]},
        ]}
        driver.write_json(command[-1], response)
        self.clock.advance(1)
        return {"exitCode": 0, "timedOut": False}


    def invoke_cell(self, process, *, wall_time=None):
        self.cell_count += 1
        output = self.root / f"output-{self.cell_count}"
        prepared = output / "subject" / "prepared.json"
        prepared.parent.mkdir(parents=True)
        driver.write_json(prepared, {"supported": True, "verificationBoundary": "SCENARIO_OBSERVATIONS"})
        source = self.root / "Subject.ts"
        source.write_text("export function holds(value: number) { return value === value; }", encoding="utf-8")
        case = {"id": "subject", "project": "fixture", "population": "correct-control",
                "sourceRoot": str(self.root), "sourceSha256": {"Subject.ts": driver.digest(source)},
                "property": {"id": "subject.holds"},
                "preparation": {"elapsedMillis": 2000, "nativeSupported": True, "frontendSupported": True}}
        frozen = {"totalBudgetMillis": 30000, "classReplayReserveMillis": 8000, "validationReserveMillis": 1500,
                  "pbtAllocationMillis": 4000, "numRuns": 12, "pbtInputLimit": 64, "symbolicInputLimit": 16,
                  "maxArrayElements": 64, "maxObservationBytes": 65536, "nodeCommand": "node",
                  "distribution": "/distribution", "adapter": "/adapter", "runtimeSha256": {}}
        self.cell_directory = output / "subject" / "37" / "COVERAGE_FEEDBACK"
        wall_clock = self.clock.time if wall_time is None else wall_time

        with patch.object(driver.time, "monotonic", self.clock.monotonic), \
                patch.object(driver.time, "time", wall_clock), \
                patch.object(driver, "run_process", side_effect=process):
            return driver.run_cell(frozen, case, seed=37, mode="COVERAGE_FEEDBACK", output=output)


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
                driver.write_json(command[-1], self.campaign(fault=True))
                result["exitCode"] = 1
            return result

        summary = self.invoke_cell(process)

        self.assertTrue(summary["primaryEligible"], summary)
        self.assertTrue(summary["confirmedViolationWithinBudget"])


    def test_incomplete_killed_and_late_artifacts_keep_diagnostics_without_credit(self):
        for exit_code, timed_out, elapsed in ((2, False, 0), (-9, True, 0), (0, False, 20), (1, False, 20)):
            with self.subTest(exit_code=exit_code, timed_out=timed_out, elapsed=elapsed):
                self.clock = FakeClock()
                self.process_calls = []

                def process(command, directory, stem, timeout_seconds, **kwargs):
                    result = self.complete_process(command, directory, stem, timeout_seconds, **kwargs)
                    artifact = self.campaign(fault=True)
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
