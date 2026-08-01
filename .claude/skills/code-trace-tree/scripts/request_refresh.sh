#!/usr/bin/env bash
# Ask IntelliJ (Code Trace Tree plugin) to reload global storage for this project.
set -euo pipefail

start="${1:-.}"
if [[ -f "$start" ]]; then
  start="$(dirname "$start")"
fi
start="$(cd "$start" && pwd)"

find_project_root() {
  local cur="$1"
  while [[ -n "$cur" && "$cur" != "/" ]]; do
    if [[ -d "$cur/.idea" || -d "$cur/.vscode" || -e "$cur/.git" ]]; then
      printf '%s\n' "$cur"
      return 0
    fi
    cur="$(dirname "$cur")"
  done
  return 1
}

project_root="$(find_project_root "$start" || true)"
if [[ -z "${project_root:-}" ]]; then
  echo "ERROR: could not locate project root from $start" >&2
  exit 1
fi

idea_dir="$project_root/.idea"
mkdir -p "$idea_dir"
request="$idea_dir/code-trace-tree.refresh-request"

# Epoch milliseconds: GNU date supports %3N; otherwise use seconds * 1000.
if ms="$(date +%s%3N 2>/dev/null)" && [[ "$ms" != *N ]]; then
  :
else
  ms="$(date +%s)000"
fi

printf '%s\n' "$ms" > "$request"
printf 'wrote=%s\n' "$request"
echo "IDE should reload Code Trace Tree data if the project is open with the plugin."
exit 0
