import importlib.util
import copy
import json
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


def auxiliary_artifact():
    source = {"module": "p.ts", "line": 1, "column": 1}
    sources = [{"module": "p.ts", "sha256": "a" * 64}]
    left = {"id": "left", "assertionId": "bound", "operandId": "left", "source": source,
            "callSite": source, "kind": "entry-argument", "inputIndex": 0}
    right = dict(left, id="right", operandId="right", inputIndex=1)
    relation = {"pair": {"left": left, "right": right}, "template": "STRICT_EQUAL", "status": "SAMPLED"}
    selected = {"relation": relation, "relevance": "UNKNOWN"}
    property = {"id": "p", "predicate": {"module": "p.ts", "exportName": "predicate", "executionKind": "sync"},
                "inputs": [{"name": name, "domain": {"kind": "integer", "min": 0, "max": 10}} for name in ("a", "b")],
                "assertions": [{"id": "bound", "source": source, "testedCall": source,
                                "operands": [{"id": name, "source": source} for name in ("left", "right")]}]}
    manifest = dict(property, propertyId="p")
    manifest.pop("id")
    target_id = "bound:left:STRICT_EQUAL:right"
    binding = {"pointId": target_id, "contextId": "original-entry", "revision": "b" * 64, "occurrence": 0}
    candidate = {"propertyId": "p", "manifestSha256": "b" * 64, "manifest": manifest,
                 "sources": sources, "inputs": [number(1), number(2)],
                 "target": {"id": target_id, "role": "HYPOTHESIS", "binding": binding,
                            "entryRelation": {"leftInputIndex": 0, "rightInputIndex": 1, "template": "STRICT_EQUAL"}}}
    event = {"binding": binding, "guard": True, "holds": False}
    native = {"admission": "ADMITTED", "outcome": "HOLDS", "targetMatched": True, "events": [event],
              "coverage": {"propertyId": "p", "admittedInvocations": 1,
                           "identity": {"manifestSha256": "b" * 64, "sourceSha256": {"/fixture/p.ts": "a" * 64}},
                           "provenance": {"sourceRoots": ["/fixture"], "request": {"admittedOnly": True}}}}
    confirmation = {"status": "CONFIRMED_HYPOTHESIS_REFUTATION", "candidate": candidate, "originalReplay": native,
                    "contextualRefutations": [event], "reduction": {"inputs": [number(0)]}}
    artifact = {"property": property, "metadata": {"sourceRoot": "/fixture", "sources": sources},
                "configuration": {"observationRequest": {"sources": sources}},
                "relationGuidanceControls": {"pairs": [relation["pair"]]},
                "result": {"propertyId": "p", "mode": "SEQUENTIAL", "relationGuidance": {
                    "selection": {"candidates": [selected], "inference": {
                        "propertyId": "p", "property": property, "sources": sources, "relations": [relation]}},
                    "attempts": [{"selected": selected, "bindingStatus": "EXACT", "bindingDiagnostics": [],
                                  "searchStatus": "REFUTATION_REACHED", "witness": candidate,
                                  "confirmation": confirmation, "completedWithinDeadline": True}]}}}
    return json.loads(json.dumps(artifact))


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


    def test_confirmed_auxiliary_refutation_with_passing_original_retains_its_original_input(self):
        artifact = auxiliary_artifact()

        batches, omissions = driver.replay_inputs(artifact)

        self.assertEqual(batches[0]["id"], "auxiliary")
        self.assertEqual(batches[0]["inputs"], [{"inputs": [number(1), number(2)], "admission": "ADMITTED", "outcome": "HOLDS"}])
        self.assertTrue(batches[0]["isolateInputs"])
        self.assertEqual(omissions["auxiliaryUncheckedInputs"], 0)


    def test_unchecked_late_or_wrong_identity_auxiliary_inputs_never_become_class_coverage(self):
        for change in ("late", "raw", "missing-identity", "missing-original", "different-input", "no-context"):
            artifact = auxiliary_artifact()
            attempt = artifact["result"]["relationGuidance"]["attempts"][0]
            if change == "late":
                attempt["completedWithinDeadline"] = False
            elif change == "raw":
                attempt["confirmation"]["status"] = "PROPERTY_HOLDS"
            elif change == "missing-identity":
                attempt["confirmation"]["originalReplay"]["coverage"].pop("identity")
            elif change == "missing-original":
                attempt["confirmation"].pop("originalReplay")
            elif change == "different-input":
                attempt["confirmation"]["candidate"] = dict(attempt["witness"], inputs=[number(2)])
            else:
                attempt["confirmation"]["contextualRefutations"] = []

            batches, omissions = driver.replay_inputs(artifact)

            self.assertEqual(batches[0]["inputs"], [], change)
            self.assertEqual(omissions["auxiliaryUncheckedInputs"], 1, change)


    def test_auxiliary_original_fault_requires_its_named_original_check_and_preserves_false(self):
        artifact = auxiliary_artifact()
        confirmation = artifact["result"]["relationGuidance"]["attempts"][0]["confirmation"]
        confirmation["status"] = "CONFIRMED_PROPERTY_VIOLATION"
        confirmation["originalReplay"].update(outcome="FALSE", failure={"checkId": "bound"}, targetMatched=True)

        batches, omissions = driver.replay_inputs(artifact)

        self.assertEqual(batches[0]["inputs"][0]["outcome"], "FALSE")
        self.assertEqual(omissions["auxiliaryUncheckedInputs"], 0)

        artifact["result"]["confirmation"] = confirmation
        self.assertTrue(driver.confirmed_original_violation(artifact))
        confirmation["originalReplay"]["targetMatched"] = False
        self.assertFalse(driver.confirmed_original_violation(artifact))
        self.assertIsNone(driver.confirmed_auxiliary_input(artifact, artifact["result"]["relationGuidance"]["attempts"][0]))
        confirmation["originalReplay"]["targetMatched"] = True
        confirmation["originalReplay"]["failure"]["checkId"] = "other"
        batches, omissions = driver.replay_inputs(artifact)
        self.assertEqual(batches[0]["inputs"], [])
        self.assertEqual(omissions["auxiliaryUncheckedInputs"], 1)


    def test_auxiliary_receipts_require_trusted_registration_selected_point_and_complete_binding(self):
        changes = ("manifest", "source", "missing-binding", "wrong-binding", "ambiguous", "wrong-target",
                   "unselected", "unconfigured-pair", "wrong-point", "missing-native-event", "wrong-input")
        for change in changes:
            with self.subTest(change=change):
                artifact = auxiliary_artifact()
                attempt = artifact["result"]["relationGuidance"]["attempts"][0]
                confirmation = attempt["confirmation"]
                candidate = confirmation["candidate"]
                native = confirmation["originalReplay"]
                if change == "manifest":
                    candidate["manifest"]["predicate"]["exportName"] = "other"
                    candidate["manifestSha256"] = "c" * 64
                    native["coverage"]["identity"]["manifestSha256"] = "c" * 64
                    candidate["target"]["binding"]["revision"] = "c" * 64
                elif change == "source":
                    candidate["sources"][0]["sha256"] = "c" * 64
                    native["coverage"]["identity"]["sourceSha256"]["/fixture/p.ts"] = "c" * 64
                elif change == "missing-binding":
                    candidate["target"]["binding"] = None
                    native["events"][0]["binding"] = None
                    confirmation["contextualRefutations"][0]["binding"] = None
                elif change == "wrong-binding":
                    candidate["target"]["binding"]["contextId"] = "helper"
                elif change == "ambiguous":
                    attempt["bindingStatus"] = "AMBIGUOUS"
                elif change == "wrong-target":
                    candidate["target"]["entryRelation"]["leftInputIndex"] = 1
                elif change == "unselected":
                    artifact["result"]["relationGuidance"]["selection"]["candidates"] = []
                elif change == "unconfigured-pair":
                    artifact["relationGuidanceControls"]["pairs"] = []
                elif change == "wrong-point":
                    attempt["selected"]["relation"]["pair"]["left"]["id"] = "other"
                elif change == "missing-native-event":
                    native["events"] = []
                else:
                    candidate["inputs"] = []
                attempt["witness"] = copy.deepcopy(candidate)

                batches, omissions = driver.replay_inputs(artifact)

                self.assertEqual(batches[0]["inputs"], [])
                self.assertEqual(omissions["auxiliaryUncheckedInputs"], 1)


    def test_canonical_auxiliary_fault_keeps_its_role_and_requires_the_timely_named_attempt(self):
        artifact = auxiliary_artifact()
        attempt = artifact["result"]["relationGuidance"]["attempts"][0]
        attempt["confirmation"]["status"] = "CONFIRMED_PROPERTY_VIOLATION"
        attempt["confirmation"]["originalReplay"].update(outcome="FALSE", failure={"checkId": "bound"})
        artifact["result"]["confirmation"] = copy.deepcopy(attempt["confirmation"])
        artifact["result"]["symbolic"] = None

        self.assertTrue(driver.confirmed_original_violation(artifact))
        self.assertEqual(artifact["result"]["confirmation"]["candidate"]["target"]["role"], "HYPOTHESIS")

        artifact["result"]["relationGuidance"]["attempts"] = []
        self.assertFalse(driver.confirmed_original_violation(artifact))
        _, omissions = driver.replay_inputs(artifact)
        self.assertEqual(omissions["auxiliaryUncheckedInputs"], 1)


    def test_relation_summary_retains_capture_limits_costs_and_failed_coverage_confirmation(self):
        artifact = auxiliary_artifact()
        trace = artifact["result"]["relationGuidance"]
        trace.update(elapsedMillis=4980, originalElapsedMillis=70, diagnostics=["trace-note"])
        trace["selection"]["inference"].update(droppedInvocations=56, droppedPoints=4, omittedInvocations=2, omittedPairs=1)
        trace["selection"]["inference"]["relations"][0]["limitations"] = ["INCOMPLETE_CAPTURE", "TRUNCATED_INVOCATION"]
        trace["attempts"][0]["confirmation"]["diagnostic"] = "backend.process.timeout"

        summary = driver._relation_summary(artifact["result"])

        self.assertEqual(summary["relationElapsedMillis"], 4980)
        self.assertEqual(summary["originalQueryElapsedMillis"], 70)
        self.assertEqual(summary["relationDiagnostics"], ["trace-note", "backend.process.timeout"])
        self.assertEqual(summary["relationDroppedInvocations"], 56)
        self.assertEqual(summary["relationDroppedPoints"], 4)
        self.assertEqual(summary["relationOmittedInvocations"], 2)
        self.assertEqual(summary["relationOmittedPairs"], 1)
        self.assertEqual(summary["relationLimitations"], ["INCOMPLETE_CAPTURE", "TRUNCATED_INVOCATION"])
        _, omissions = driver.replay_inputs(artifact)
        self.assertEqual(omissions["auxiliaryUncheckedInputs"], 1)


    def test_process_timeout_retains_raw_output_and_failure(self):
        process = driver.run_process(
            ["node", "-e", "process.stdout.write('started'); setInterval(() => {}, 1000)"], self.root, "slow", 0.2,
        )

        self.assertTrue(process["timedOut"])
        self.assertNotEqual(process["exitCode"], 0)
        self.assertEqual((self.root / "slow.stdout").read_text(), "started")



if __name__ == "__main__":
    unittest.main()
