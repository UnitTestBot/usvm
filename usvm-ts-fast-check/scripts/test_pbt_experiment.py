import importlib.util
import copy
import os
from pathlib import Path
import struct
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("pbt_experiment", ROOT / "pbt_experiment.py")
driver = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(driver)


def number(value):
    return {"kind": "number", "value": "finite", "bits": struct.pack(">d", value).hex()}


def symbolic_artifact(outcome="HOLDS"):
    candidate = {"propertyId": "p", "manifestSha256": "hash", "inputs": [number(1)],
                 "target": {"id": "predicate", "role": "PROPERTY", "expectedOutcome": outcome}}
    native = {"admission": "ADMITTED", "outcome": outcome, "targetMatched": True,
              "coverage": {"propertyId": "p", "admittedInvocations": 1,
                           "identity": {"manifestSha256": "hash"},
                           "provenance": {"request": {"admittedOnly": True}}}}
    return {"property": {"predicate": {"exportName": "predicate"}},
            "result": {"propertyId": "p", "mode": "SYMBOLIC_ONLY", "symbolic": {
                "inputs": candidate["inputs"], "completedInputs": [{"inputs": candidate["inputs"]}]},
                "symbolicInputReplays": [{"candidate": candidate, "replay": native,
                                         "completedWithinDeadline": True, "predictedOutcomes": [outcome]}]}}


class ExperimentTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name).resolve()

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


    def test_compact_export_takes_priority_and_preserves_all_omission_kinds(self):
        concrete = {"inputExport": {"droppedInvocations": 3, "unsupportedInputs": 2,
                                   "pendingInvocations": 1, "records": [
            {"phase": "generation", "input": {"status": "captured", "value": {"kind": "array", "elements": [number(1)]}},
             "admission": "admitted", "outcome": "holds"}]},
                    "observations": {"droppedInvocations": 99, "invocations": []}}

        batches, omissions = driver.replay_inputs({"result": {"mode": "PBT_ONLY", "concrete": concrete}})

        self.assertEqual(len(batches[0]["inputs"]), 1)
        self.assertEqual(omissions["pbtDroppedInvocations"], 3)
        self.assertEqual(omissions["pbtUnsupportedInputs"], 2)
        self.assertEqual(omissions["pbtPendingInvocations"], 1)


    def test_raw_false_or_exception_does_not_bypass_original_confirmation(self):
        for outcome in ("FALSE", "THREW"):
            artifact = symbolic_artifact(outcome)

            batches, omissions = driver.replay_inputs(artifact)

            self.assertEqual(batches[0]["inputs"], [])
            self.assertEqual(omissions["symbolicUncheckedInputs"], 1)


    def test_healthy_symbolic_coverage_requires_the_existing_admitted_receipt_contract(self):
        artifact = symbolic_artifact()
        batches, omissions = driver.replay_inputs(artifact)
        self.assertEqual(len(batches[0]["inputs"]), 1)
        self.assertFalse(any(omissions.values()))

        for change in ("late", "unadmitted", "missing-identity", "different-manifest", "diagnostic"):
            invalid = copy.deepcopy(artifact)
            replay = invalid["result"]["symbolicInputReplays"][0]
            if change == "late":
                replay["completedWithinDeadline"] = False
            elif change == "unadmitted":
                replay["replay"]["coverage"]["admittedInvocations"] = 0
            elif change == "missing-identity":
                replay["replay"]["coverage"].pop("identity")
            elif change == "different-manifest":
                replay["replay"]["coverage"]["identity"]["manifestSha256"] = "other"
            else:
                replay["replay"]["diagnostic"] = "mismatch"

            batches, omissions = driver.replay_inputs(invalid)

            self.assertEqual(batches[0]["inputs"], [], change)
            self.assertEqual(omissions["symbolicUncheckedInputs"], 1, change)


    def test_confirmed_named_fault_uses_the_original_input_and_check_receipt(self):
        artifact = symbolic_artifact("FALSE")
        artifact["property"]["assertions"] = [{"id": "named"}]
        raw = artifact["result"]["symbolicInputReplays"][0]
        candidate = copy.deepcopy(raw["candidate"])
        candidate["target"]["id"] = "named"
        native = copy.deepcopy(raw["replay"])
        native["failure"] = {"checkId": "named"}
        confirmation = {"status": "CONFIRMED_PROPERTY_VIOLATION", "candidate": candidate, "originalReplay": native}
        artifact["result"]["confirmation"] = confirmation

        batches, omissions = driver.replay_inputs(artifact)

        self.assertEqual(batches[0]["inputs"], [{"inputs": candidate["inputs"], "admission": "ADMITTED", "outcome": "FALSE"}])
        self.assertFalse(any(omissions.values()))

        confirmation["originalReplay"]["failure"]["checkId"] = "different"

        batches, omissions = driver.replay_inputs(artifact)

        self.assertEqual(batches[0]["inputs"], [])
        self.assertEqual(omissions["symbolicUncheckedInputs"], 1)


    def test_functional_quota_and_early_success_are_not_full_budget_results(self):
        row = {"comparisonKind": "functional-quota", "primaryEligible": True, "mode": "PBT_ONLY",
               "pbtQuotaReached": True, "confirmedViolation": False}

        self.assertFalse(driver.full_budget_eligible(row))
        row["comparisonKind"] = "equal-budget"
        self.assertFalse(driver.full_budget_eligible(row))
        row["confirmedViolation"] = True
        self.assertTrue(driver.full_budget_eligible(row))
        row.update(mode="SYMBOLIC_ONLY", confirmedViolation=False)
        self.assertTrue(driver.full_budget_eligible(row))


    def test_budget_driven_quota_is_independent_of_the_explicit_large_capture_limit(self):
        manifest = driver.read_json(ROOT.parent / "experiments/two-phase-smoke/manifest.json")
        manifest.update(comparisonKind="equal-budget", numRuns=1_000_000,
                        pbtInputLimit=100_000, maxObservationBytes=64 * 1024 * 1024)

        driver.validate_manifest(manifest)

        manifest["maxObservationBytes"] += 1
        with self.assertRaisesRegex(ValueError, "64MiB"):
            driver.validate_manifest(manifest)


    def test_process_timeout_retains_raw_output_and_failure(self):
        process = driver.run_process(
            ["node", "-e", "process.stdout.write('started'); setInterval(() => {}, 1000)"], self.root, "slow", 0.2,
        )

        self.assertTrue(process["timedOut"])
        self.assertNotEqual(process["exitCode"], 0)
        self.assertEqual((self.root / "slow.stdout").read_text(), "started")



if __name__ == "__main__":
    unittest.main()
