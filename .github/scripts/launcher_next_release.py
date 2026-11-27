#!/usr/bin/env python3
"""Decides which Nuvio TV tag the launcher should build next. Our release has the same version as the tag
(Nuvio TV 1.2.0-beta.5 becomes Nuvio TV Launcher 1.2.0-beta.5, 1.2.0 becomes 1.2.0), so a user sees the same numbers in both.

Tags are taken oldest first, one per run, so a stable release is never skipped. A tag counts as built when one of our
releases says so ("Nuvio TV tag: <tag>" in its notes); BASELINE (the tag the launcher was first built on) and
everything older than it are never built.

Usage: launcher_next_release.py --upstream-tags FILE --built-tags FILE --baseline TAG
(each FILE has one tag per line.) Prints one JSON object; "tag" is empty when there is nothing to build.
"""
import argparse
import json
import re
import sys

SEMVER = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$")


def parse(tag):
    m = SEMVER.match(tag.strip())
    if not m:
        return None
    major, minor, patch, pre = m.groups()
    return int(major), int(minor), int(patch), pre.split(".") if pre else []


def sort_key(parsed):
    major, minor, patch, pre = parsed

    def identifier(part):
        # Numeric identifiers sort before alphanumeric ones, and numerically among themselves.
        return (0, int(part), "") if part.isdigit() else (1, 0, part)

    # A release sorts after its own pre-releases.
    return major, minor, patch, 1 if not pre else 0, [identifier(p) for p in pre]


def valid(tags):
    out = []
    for tag in tags:
        parsed = parse(tag)
        if parsed:
            out.append((tag.strip(), parsed))
    return out


def next_upstream_tag(upstream, built, baseline):
    floor = [parse(t) for t in list(built) + [baseline]]
    floor = [p for p in floor if p]
    highest = max(floor, key=sort_key) if floor else None
    candidates = [(t, p) for t, p in valid(upstream) if highest is None or sort_key(p) > sort_key(highest)]
    if not candidates:
        return None
    return min(candidates, key=lambda c: sort_key(c[1]))


def read_lines(path):
    with open(path, encoding="utf-8") as handle:
        return [line.strip() for line in handle if line.strip()]


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--upstream-tags", required=True)
    ap.add_argument("--built-tags", required=True)
    ap.add_argument("--baseline", required=True)
    args = ap.parse_args(argv)

    picked = next_upstream_tag(read_lines(args.upstream_tags), read_lines(args.built_tags), args.baseline)
    if picked is None:
        print(json.dumps({"tag": ""}))
        return 0
    tag, parsed = picked
    prerelease = bool(parsed[3])
    print(json.dumps({
        "tag": tag,
        "prerelease": prerelease,
        "version": tag.lstrip("vV"),
    }))
    return 0


if __name__ == "__main__":
    sys.exit(main())
