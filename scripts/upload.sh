#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src="$repo_root/packages/server"
user="${USER:-$(id -un)}"

target="${1:-${CIRCUITPY:-}}"
if [[ -z "$target" ]]; then
  for candidate in \
    "/run/media/$user/CIRCUITPY" \
    "/media/$user/CIRCUITPY" \
    "/media/CIRCUITPY" \
    "/Volumes/CIRCUITPY"; do
    if [[ -d "$candidate" ]]; then
      target="$candidate"
      break
    fi
  done
fi

if [[ -z "$target" || ! -d "$target" ]]; then
  echo "CIRCUITPY drive not found. Plug in the board, or pass its mount path:" >&2
  echo "  $0 /path/to/CIRCUITPY" >&2
  exit 1
fi

shopt -s nullglob
files=("$src"/*.py)
if (( ${#files[@]} == 0 )); then
  echo "No .py files found in $src" >&2
  exit 1
fi

for f in "${files[@]}"; do
  cp "$f" "$target/"
  echo "copied $(basename "$f") -> $target/"
done

if [[ -d "$src/src" ]]; then
  rm -rf "$target/src"
  cp -r "$src/src" "$target/src"
  echo "copied src/ -> $target/src/"
fi

sync
echo "Done: ${#files[@]} file(s) + src/ uploaded to $target"
