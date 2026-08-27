#!/usr/bin/env bash
set -euo pipefail

PR_HEAD="${PR_HEAD:-${1:-HEAD}}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"

ensure_commit() {
  local sha="$1"
  if ! git cat-file -e "${sha}^{commit}" 2>/dev/null; then
    git fetch --no-tags origin "$sha" >/dev/null 2>&1 || return 1
  fi
}

CURRENT_FP="$(bash ci/celine-runtime-fingerprint.sh "$PR_HEAD")"
RUN_ID=""
SOURCE_SHA=""
MATCH_MODE=""

while read -r candidate_run candidate_sha; do
  [ -n "$candidate_run" ] || continue
  ensure_commit "$candidate_sha" || continue

  artifacts="$(gh api "repos/${GITHUB_REPOSITORY}/actions/runs/${candidate_run}/artifacts?per_page=100")"
  apk_id="$(jq -r '[.artifacts[] | select(.name == "yahya-ai-debug" and .expired == false)][0].id // empty' <<<"$artifacts")"
  [ -n "$apk_id" ] || continue

  metadata_id="$(jq -r --arg name "runtime-fingerprint-${CURRENT_FP}" '[.artifacts[] | select(.name == $name and .expired == false)][0].id // empty' <<<"$artifacts")"
  if [ -n "$metadata_id" ]; then
    RUN_ID="$candidate_run"
    SOURCE_SHA="$candidate_sha"
    MATCH_MODE="metadata"
    break
  fi

  candidate_fp="$(bash ci/celine-runtime-fingerprint.sh "$candidate_sha" 2>/dev/null || true)"
  if [ -n "$candidate_fp" ] && [ "$candidate_fp" = "$CURRENT_FP" ]; then
    RUN_ID="$candidate_run"
    SOURCE_SHA="$candidate_sha"
    MATCH_MODE="computed"
    break
  fi
done < <(gh api "repos/${GITHUB_REPOSITORY}/actions/workflows/android-build.yml/runs?status=success&per_page=60" --jq '.workflow_runs[] | "\(.id) \(.head_sha)"')

if [ -z "$RUN_ID" ]; then
  echo "No successful Android Build with a live APK matching runtime fingerprint $CURRENT_FP" >&2
  exit 1
fi

{
  echo "run_id=$RUN_ID"
  echo "source_sha=$SOURCE_SHA"
  echo "runtime_fingerprint=$CURRENT_FP"
  echo "match_mode=$MATCH_MODE"
} >> "$GITHUB_OUTPUT"

echo "Runtime-equivalent APK: run=$RUN_ID sha=$SOURCE_SHA fingerprint=$CURRENT_FP mode=$MATCH_MODE"
