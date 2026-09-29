#!/usr/bin/env bash

set -euo pipefail

version="${1:?Usage: extract-changelog.sh VERSION [CHANGELOG] [OUTPUT]}"
changelog="${2:-CHANGELOG.md}"
output="${3:-release-notes.md}"

if [[ ! -f "$changelog" ]]; then
  echo "Changelog not found: $changelog" >&2
  exit 1
fi

temporary="$(mktemp)"
trap 'rm -f "$temporary"' EXIT

if ! awk -v version="$version" '
  BEGIN {
    heading = "## [" version "]"
  }
  !found && ($0 == heading || index($0, heading " - ") == 1) {
    found = 1
    next
  }
  found && index($0, "## ") == 1 {
    exit
  }
  found {
    print
  }
  END {
    if (!found) exit 1
  }
' "$changelog" > "$temporary"; then
  echo "No changelog section found for version $version" >&2
  exit 1
fi

mkdir -p "$(dirname "$output")"
awk '
  NF { started = 1 }
  started { lines[++count] = $0 }
  END {
    while (count > 0 && lines[count] == "") count--
    for (line = 1; line <= count; line++) print lines[line]
  }
' "$temporary" > "$output"

if [[ ! -s "$output" ]]; then
  echo "Changelog section for version $version is empty" >&2
  exit 1
fi
