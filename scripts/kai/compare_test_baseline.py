#!/usr/bin/env python3
"""Compares JUnit XML results with the measured baseline failures (docs/kai/baseline-tests.json).

A failure counts as new only when its class and test name are not among the baseline's known failures. A class that
fails outside the baseline is retried once by the workflow; its retry results replace the first run's for that class.

Usage:
  compare_test_baseline.py --baseline docs/kai/baseline-tests.json --results DIR [--retry-results DIR]
                           [--report-json FILE] [--report-md FILE] [--retry-classes FILE]
Exit codes: 0 no new failure, 1 new failures (after the retry when given), 2 no results to read.
"""
from __future__ import annotations

import argparse
import glob
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def read_results(directory: Path) -> dict[str, dict[str, str]]:
    """{class: {test name: "passed" | "failed" | "skipped"}} from every TEST-*.xml under [directory]."""
    results: dict[str, dict[str, str]] = {}
    for path in sorted(glob.glob(str(directory / "**" / "TEST-*.xml"), recursive=True)):
        root = ET.parse(path).getroot()
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            for case in suite.findall("testcase"):
                cls = case.get("classname") or suite.get("name") or "?"
                if case.find("failure") is not None or case.find("error") is not None:
                    outcome = "failed"
                elif case.find("skipped") is not None:
                    outcome = "skipped"
                else:
                    outcome = "passed"
                results.setdefault(cls, {})[case.get("name") or "?"] = outcome
    return results


def known_failures(baseline: Path) -> set[tuple[str, str]]:
    classes = json.loads(baseline.read_text())["classes"]
    return {(cls, name) for cls, info in classes.items() for name in info.get("failedCases", [])}


def failures(results: dict[str, dict[str, str]]) -> set[tuple[str, str]]:
    return {(cls, name) for cls, cases in results.items() for name, outcome in cases.items() if outcome == "failed"}


def compare(first: dict, known: set, retry: dict | None = None) -> dict:
    first_new = sorted(failures(first) - known)
    retry_classes = sorted({cls for cls, _ in first_new})
    merged = dict(first)
    if retry is not None:
        for cls in retry_classes:
            if cls in retry:
                merged[cls] = retry[cls]
    final = failures(merged)
    new = sorted(final - known)
    totals = {"tests": 0, "passed": 0, "failed": 0, "skipped": 0}
    for cases in merged.values():
        for outcome in cases.values():
            totals["tests"] += 1
            totals[outcome] += 1
    return {
        "totals": totals,
        "knownFailures": sorted([list(f) for f in final & known]),
        "knownFailuresNowPassing": sorted([list(f) for f in known - final if f[0] in merged]),
        "newOnFirstRun": [list(f) for f in first_new],
        "retriedClasses": retry_classes if retry is not None else [],
        "passedOnRetry": sorted([list(f) for f in set(first_new) - final]) if retry is not None else [],
        "newFailures": [list(f) for f in new],
        "verdict": "fail" if new else "pass",
    }


def markdown(report: dict) -> str:
    t = report["totals"]
    lines = [
        "## Pruebas unitarias frente a la línea base",
        "",
        f"- Pruebas: {t['tests']} · aprobadas {t['passed']} · fallidas {t['failed']} · omitidas {t['skipped']}",
        f"- Fallas conocidas de la base: {len(report['knownFailures'])}",
        f"- Fallas nuevas en la primera ejecución: {len(report['newOnFirstRun'])}",
        f"- Clases reintentadas: {len(report['retriedClasses'])} · aprobadas al reintentar: {len(report['passedOnRetry'])}",
        f"- **Fallas nuevas: {len(report['newFailures'])} → {'FALLA' if report['verdict'] == 'fail' else 'OK'}**",
    ]
    for title, key in (("Fallas nuevas", "newFailures"), ("Aprobadas al reintentar (intermitentes)", "passedOnRetry"),
                       ("Fallas conocidas de la base", "knownFailures")):
        if report[key]:
            lines += ["", f"### {title}", ""] + [f"- `{cls}` › {name}" for cls, name in report[key]]
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--retry-results", type=Path)
    parser.add_argument("--report-json", type=Path)
    parser.add_argument("--report-md", type=Path)
    parser.add_argument("--retry-classes", type=Path, help="write the classes to retry here, one per line")
    args = parser.parse_args()

    first = read_results(args.results)
    if not first:
        print(f"No JUnit XML results under {args.results}", file=sys.stderr)
        return 2
    retry = read_results(args.retry_results) if args.retry_results else None
    report = compare(first, known_failures(args.baseline), retry)
    text = markdown(report)
    print(text)
    if args.report_json:
        args.report_json.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
    if args.report_md:
        args.report_md.write_text(text)
    if args.retry_classes:
        args.retry_classes.write_text("".join(f"{cls}\n" for cls in sorted({cls for cls, _ in report["newOnFirstRun"]})))
    return 1 if report["verdict"] == "fail" else 0


if __name__ == "__main__":
    sys.exit(main())
