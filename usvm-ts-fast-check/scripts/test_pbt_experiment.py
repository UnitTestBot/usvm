import importlib.util
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


    def test_process_timeout_retains_raw_output_and_failure(self):
        process = driver.run_process(
            ["node", "-e", "process.stdout.write('started'); setInterval(() => {}, 1000)"], self.root, "slow", 0.2,
        )

        self.assertTrue(process["timedOut"])
        self.assertNotEqual(process["exitCode"], 0)
        self.assertEqual((self.root / "slow.stdout").read_text(), "started")



if __name__ == "__main__":
    unittest.main()
