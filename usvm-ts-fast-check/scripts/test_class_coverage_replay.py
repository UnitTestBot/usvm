import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent
ADAPTER = Path(os.environ.get("PBT_ADAPTER_ROOT", ROOT.parent / "fast-check-adapter")).resolve()


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def number(value):
    return {"kind": "number", "value": "finite", "bits": struct.pack(">d", value).hex()}


class NativeClassCoverageTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name).resolve()
        (self.root / "Subject.ts").write_text(
            "export class Subject { value(x: number): number { if (x > 0) return 1; return 2; } }\n",
            encoding="utf-8",
        )
        (self.root / "Properties.ts").write_text(
            "import { Subject } from './Subject';\n"
            "export function holds(x: number): boolean { new Subject().value(x); return true; }\n"
            "export function scenario(x: number) { return { result: new Subject().value(x) }; }\n",
            encoding="utf-8",
        )
        self.case = {"sourceRoot": str(self.root), "classSource": "Subject.ts", "className": "Subject",
                     "property": {"predicate": {"module": "Properties.ts", "exportName": "holds"}},
                     "diagnosticScenario": {"module": "Properties.ts", "exportName": "scenario"},
                     "sourceSha256": {name: digest(self.root / name) for name in ("Subject.ts", "Properties.ts")}}


    def tearDown(self):
        self.temporary.cleanup()


    def native(self, operation, request):
        request_path = self.root / "request.json"
        output = self.root / "response.json"
        request_path.write_text(json.dumps(request), encoding="utf-8")
        process = subprocess.run(
            ["node", ROOT / "class-coverage-replay.mjs", operation, ADAPTER, request_path, output],
            capture_output=True, timeout=20,
        )
        result = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(process.returncode, 0, (result, process.stderr.decode()))
        return result


    def test_exact_class_statements_are_checked_on_both_native_branches(self):
        prepared = self.native("prepare", self.case)
        self.assertTrue(prepared["supported"], prepared)
        inputs = [{"inputs": [number(value)], "admission": "ADMITTED", "outcome": "HOLDS"} for value in (-1, 1)]

        replay = self.native("replay", dict(self.case, prepared=prepared, inputs=inputs))

        self.assertEqual(replay["verificationBoundary"], "SCENARIO_OBSERVATIONS")
        self.assertTrue(all(record["verified"] for record in replay["records"]), replay)
        covered = {item for record in replay["records"] for item in record["creditedStatementIds"]}
        self.assertEqual(covered, {statement["id"] for statement in prepared["universe"]["statements"]})


    def test_always_true_oracle_cannot_hide_changed_scenario_results(self):
        prepared = self.native("prepare", self.case)
        prepared["instrumentedSource"] = prepared["instrumentedSource"].replace("return 1", "return 3")
        prepared["instrumentedSha256"] = hashlib.sha256(prepared["instrumentedSource"].encode()).hexdigest()
        inputs = [{"inputs": [number(1)], "admission": "ADMITTED", "outcome": "HOLDS"}]

        replay = self.native("replay", dict(self.case, prepared=prepared, inputs=inputs))

        record = replay["records"][0]
        self.assertEqual(record["original"]["oracle"], record["counted"]["oracle"])
        self.assertFalse(record["verified"])
        self.assertEqual(record["creditedStatementIds"], [])


    def test_pure_static_cache_preserves_repeated_native_observations_without_module_coverage(self):
        (self.root / "Subject.ts").write_text(
            "export class Subject { private static cache: number[] = []; "
            "value(x: number): number { Subject.cache.push(x); return Subject.cache.length; } }\n",
            encoding="utf-8",
        )
        self.case["sourceSha256"] = {name: digest(self.root / name) for name in ("Subject.ts", "Properties.ts")}
        prepared = self.native("prepare", self.case)
        self.assertTrue(prepared["supported"], prepared)
        self.assertIn("module-loading", prepared["excludedExecutionScopes"])
        self.assertEqual({statement["memberName"] for statement in prepared["universe"]["statements"]}, {"value"})
        inputs = [{"inputs": [number(value)], "admission": "ADMITTED", "outcome": "HOLDS"} for value in (4, 7)]

        replay = self.native("replay", dict(self.case, prepared=prepared, inputs=inputs))

        self.assertTrue(all(record["verified"] for record in replay["records"]), replay)
        covered = {item for record in replay["records"] for item in record["creditedStatementIds"]}
        self.assertEqual(covered, {statement["id"] for statement in prepared["universe"]["statements"]})


    def test_diagnostic_module_state_does_not_change_the_oracle_input_trajectory(self):
        (self.root / "Subject.ts").write_text(
            "export class Subject { value(x: number): number { if (x === 2) return 2; return 1; } }\n",
            encoding="utf-8",
        )
        (self.root / "Properties.ts").write_text(
            "import { Subject } from './Subject';\n"
            "let calls = 0;\n"
            "export function scenario(x: number) { calls++; return { result: new Subject().value(calls) }; }\n"
            "export function holds(x: number): boolean { scenario(x); return true; }\n",
            encoding="utf-8",
        )
        self.case["sourceSha256"] = {name: digest(self.root / name) for name in ("Subject.ts", "Properties.ts")}
        prepared = self.native("prepare", self.case)
        inputs = [{"inputs": [number(value)], "admission": "ADMITTED", "outcome": "HOLDS"} for value in (0, 0)]

        replay = self.native("replay", dict(self.case, prepared=prepared, inputs=inputs))

        self.assertTrue(all(record["verified"] for record in replay["records"]), replay)
        covered = {item for record in replay["records"] for item in record["creditedStatementIds"]}
        self.assertEqual(covered, {statement["id"] for statement in prepared["universe"]["statements"]})



if __name__ == "__main__":
    unittest.main()
