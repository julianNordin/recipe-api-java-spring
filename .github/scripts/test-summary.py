#!/usr/bin/env python3
"""Turn the Surefire and Failsafe XML reports into a GitHub job summary.

Kept as a file rather than inlined in the workflow for two reasons: a heredoc nested inside a YAML
block scalar is fragile in a way nothing warns you about, and a script on disk can be run locally
against real reports - which matters here, because this pipeline is written before the repository
has a remote and cannot be proven green any other way.

Reads target/{surefire,failsafe}-reports/TEST-*.xml, writes markdown to stdout. Prints a summary
even when the build failed, since that is when it is worth reading; the caller redirects it to
$GITHUB_STEP_SUMMARY.
"""

import glob
import sys
import xml.etree.ElementTree as ElementTree

TIERS = (
    ("Surefire", "unit, slice and repository tests", "target/surefire-reports/TEST-*.xml"),
    ("Failsafe", "end-to-end integration tests", "target/failsafe-reports/TEST-*.xml"),
)


def totals(pattern):
    """Sum the testsuite attributes across every report matching pattern."""
    tests = failures = errors = skipped = 0
    seconds = 0.0
    classes = 0
    for report in glob.glob(pattern):
        try:
            suite = ElementTree.parse(report).getroot()
        except ElementTree.ParseError:
            # A run killed mid-write leaves truncated XML. One unreadable report should not cost
            # the summary of every other tier.
            continue
        classes += 1
        tests += int(suite.get("tests") or 0)
        failures += int(suite.get("failures") or 0)
        errors += int(suite.get("errors") or 0)
        skipped += int(suite.get("skipped") or 0)
        seconds += float(suite.get("time") or 0)
    return classes, tests, failures, errors, skipped, seconds


def failed_cases(pattern):
    """Every failing case, so the summary names them rather than only counting them."""
    named = []
    for report in glob.glob(pattern):
        try:
            suite = ElementTree.parse(report).getroot()
        except ElementTree.ParseError:
            continue
        for case in suite.iter("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                named.append((
                    "%s.%s" % (case.get("classname", "?"), case.get("name", "?")),
                    (problem.get("message") or problem.get("type") or "").strip().splitlines()[:1],
                ))
    return named


def main():
    # The runner is UTF-8, but this script is meant to be runnable locally too, and a Windows
    # console defaults to cp1252 - where the cross below raises UnicodeEncodeError and takes the
    # whole summary with it. Found by running the failure path rather than only the happy one.
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except (AttributeError, ValueError):
        pass

    lines = ["## Test results", ""]
    lines.append("| Tier | What it covers | Classes | Tests | Failures | Errors | Skipped | Time |")
    lines.append("|---|---|--:|--:|--:|--:|--:|--:|")

    broken = []
    grand_total = 0
    for name, covers, pattern in TIERS:
        classes, tests, failures, errors, skipped, seconds = totals(pattern)
        grand_total += tests
        mark = "" if failures == 0 and errors == 0 else " ❌"
        lines.append("| %s%s | %s | %d | %d | %d | %d | %d | %.1fs |"
                     % (name, mark, covers, classes, tests, failures, errors, skipped, seconds))
        broken.extend(failed_cases(pattern))

    lines.append("")
    lines.append("**%d tests in total.**" % grand_total)

    if broken:
        lines.append("")
        lines.append("### Failures")
        lines.append("")
        for case, message in broken:
            lines.append("- `%s`%s" % (case, (" — " + message[0]) if message else ""))

    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
