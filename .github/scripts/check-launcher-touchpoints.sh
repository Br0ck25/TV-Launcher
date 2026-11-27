#!/usr/bin/env bash
# Fails when the launcher edits a Nuvio TV file that is not listed in .github/launcher-touchpoints.txt.
# Usage: .github/scripts/check-launcher-touchpoints.sh [upstream-ref]   (default: fetches NuvioMedia/NuvioTV dev)
set -euo pipefail

allowed_file=".github/launcher-touchpoints.txt"
ref="${1:-}"
if [[ -z "$ref" ]]; then
    git remote get-url nuviotv >/dev/null 2>&1 || git remote add nuviotv https://github.com/NuvioMedia/NuvioTV.git
    git fetch -q nuviotv "${UPSTREAM_BRANCH:-dev}"
    ref="nuviotv/${UPSTREAM_BRANCH:-dev}"
fi

mapfile -t patterns < <(grep -Ev '^[[:space:]]*(#|$)' "$allowed_file")

# Files Nuvio TV has that we changed or deleted. Three dots: against the common ancestor, so changes that only
# came in with the upstream merges are not counted.
mapfile -t changed < <(git diff --name-only --diff-filter=MD "$ref"...HEAD)

unlisted=()
for f in "${changed[@]}"; do
    ok=0
    for p in "${patterns[@]}"; do
        # shellcheck disable=SC2053
        if [[ "$f" == $p ]]; then ok=1; break; fi
    done
    (( ok )) || unlisted+=("$f")
done

echo "Nuvio TV files edited by the launcher: ${#changed[@]}"
if (( ${#unlisted[@]} > 0 )); then
    echo
    echo "These Nuvio TV files are edited but not listed in $allowed_file:" >&2
    printf '  %s\n' "${unlisted[@]}" >&2
    echo >&2
    echo "Keep launcher logic in launcher-owned files and make the edit one call, or list the file there and in LAUNCHER_TOUCHPOINTS.md." >&2
    exit 1
fi

# Not an error: a listed file nobody edits any more is just stale.
for p in "${patterns[@]}"; do
    [[ "$p" == *'*'* ]] && continue
    printf '%s\n' "${changed[@]}" | grep -qxF "$p" || echo "note: $p is listed but unchanged"
done
echo "OK"
