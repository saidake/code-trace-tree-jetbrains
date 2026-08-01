#!/usr/bin/env bash
# Ask IntelliJ (Code Trace Tree plugin) to select trace points by id.
# With exactly one valid id, the IDE also navigates to the source location.
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <trace-point-id> [trace-point-id...]" >&2
  exit 1
fi

start="."
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
request="$idea_dir/code-trace-tree.select-request"

{
  for id in "$@"; do
    trimmed="${id#"${id%%[![:space:]]*}"}"
    trimmed="${trimmed%"${trimmed##*[![:space:]]}"}"
    if [[ -n "$trimmed" ]]; then
      printf '%s\n' "$trimmed"
    fi
  done
} > "$request"

printf 'wrote=%s\n' "$request"
echo "IDE should select the listed trace points if the project is open with the plugin."
exit 0
