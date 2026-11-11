import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parents[1] / "kai" / "compare_test_baseline.py"
spec = importlib.util.spec_from_file_location("compare_test_baseline", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

BASELINE = {
    "classes": {
        "com.a.NetTest": {"tests": 2, "passed": 1, "failed": 1, "skipped": 0, "failedCases": ["tls fallback"]},
        "com.a.OkTest": {"tests": 1, "passed": 1, "failed": 0, "skipped": 0, "failedCases": []},
    }
}


def suite(directory: Path, cls: str, cases: dict):
    """Writes a Gradle-style TEST-<class>.xml; cases: name -> passed|failed|error|skipped."""
    body = []
    for name, outcome in cases.items():
        inner = {"failed": '<failure message="boom">trace</failure>', "error": '<error message="x"/>',
                 "skipped": "<skipped/>"}.get(outcome, "")
        body.append(f'  <testcase name="{name}" classname="{cls}" time="0.1">{inner}</testcase>')
    failures = sum(o == "failed" for o in cases.values())
    xml = (f'<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="{cls}" tests="{len(cases)}" '
           f'failures="{failures}" errors="0" skipped="0">\n' + "\n".join(body) + "\n</testsuite>\n")
    directory.mkdir(parents=True, exist_ok=True)
    (directory / f"TEST-{cls}.xml").write_text(xml)


class CompareTestBaselineTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.baseline = self.root / "baseline.json"
        self.baseline.write_text(json.dumps(BASELINE))

    def tearDown(self):
        self.tmp.cleanup()

    def run_cli(self, *extra):
        return subprocess.run([sys.executable, "-I", str(SCRIPT), "--baseline", str(self.baseline), *extra],
                              capture_output=True, text=True)

    def test_only_the_baseline_failures_pass(self):
        first = self.root / "first"
        suite(first, "com.a.NetTest", {"tls fallback": "failed", "plain": "passed"})
        suite(first, "com.a.OkTest", {"works": "passed"})
        suite(first, "com.kai.NewTest", {"added": "passed", "later": "skipped"})
        report_json = self.root / "report.json"
        retry = self.root / "retry.txt"
        done = self.run_cli("--results", str(first), "--report-json", str(report_json), "--retry-classes", str(retry))
        self.assertEqual(0, done.returncode, done.stderr)
        report = json.loads(report_json.read_text())
        self.assertEqual([["com.a.NetTest", "tls fallback"]], report["knownFailures"])
        self.assertEqual([], report["newFailures"])
        self.assertEqual({"tests": 5, "passed": 3, "failed": 1, "skipped": 1}, report["totals"])
        self.assertEqual("", retry.read_text())

    def test_a_new_failure_asks_for_a_retry_of_its_class(self):
        first = self.root / "first"
        suite(first, "com.a.NetTest", {"tls fallback": "failed"})
        suite(first, "com.a.OkTest", {"works": "error"})
        retry = self.root / "retry.txt"
        done = self.run_cli("--results", str(first), "--retry-classes", str(retry))
        self.assertEqual(1, done.returncode)
        self.assertEqual("com.a.OkTest\n", retry.read_text())
        self.assertIn("Fallas nuevas: 1", done.stdout)

    def test_a_failure_that_passes_on_retry_is_flaky_not_new(self):
        first, second = self.root / "first", self.root / "second"
        suite(first, "com.a.NetTest", {"tls fallback": "failed"})
        suite(first, "com.a.OkTest", {"works": "failed"})
        suite(second, "com.a.OkTest", {"works": "passed"})
        report_md = self.root / "report.md"
        done = self.run_cli("--results", str(first), "--retry-results", str(second), "--report-md", str(report_md))
        self.assertEqual(0, done.returncode, done.stdout)
        text = report_md.read_text()
        self.assertIn("Aprobadas al reintentar", text)
        self.assertIn("`com.a.OkTest` › works", text)

    def test_a_failure_that_fails_again_on_retry_is_new(self):
        first, second = self.root / "first", self.root / "second"
        suite(first, "com.a.OkTest", {"works": "failed"})
        suite(second, "com.a.OkTest", {"works": "failed"})
        report_json = self.root / "report.json"
        done = self.run_cli("--results", str(first), "--retry-results", str(second), "--report-json", str(report_json))
        self.assertEqual(1, done.returncode)
        self.assertEqual([["com.a.OkTest", "works"]], json.loads(report_json.read_text())["newFailures"])

    def test_a_retry_only_replaces_the_retried_classes(self):
        first, second = self.root / "first", self.root / "second"
        suite(first, "com.a.NetTest", {"tls fallback": "failed"})
        suite(first, "com.a.OkTest", {"works": "failed"})
        # A stray result for a class that was not retried must not hide anything.
        suite(second, "com.a.OkTest", {"works": "passed"})
        suite(second, "com.a.NetTest", {"tls fallback": "passed"})
        report = module.compare(module.read_results(first), module.known_failures(self.baseline), module.read_results(second))
        self.assertEqual(["com.a.OkTest"], report["retriedClasses"])
        self.assertEqual([["com.a.NetTest", "tls fallback"]], report["knownFailures"])

    def test_no_results_is_an_error(self):
        self.assertEqual(2, self.run_cli("--results", str(self.root / "missing")).returncode)


if __name__ == "__main__":
    unittest.main()
