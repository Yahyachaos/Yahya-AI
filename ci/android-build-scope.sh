#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"
EVENT_NAME="${EVENT_NAME:-}"
TARGET_SHA="${TARGET_SHA:-}"
PUBLISH_RELEASE="${PUBLISH_RELEASE:-false}"
PR_ACTION="${PR_ACTION:-}"
PR_BASE_SHA="${PR_BASE_SHA:-}"
PR_HEAD_SHA="${PR_HEAD_SHA:-}"
PR_BEFORE_SHA="${PR_BEFORE_SHA:-}"
PR_DRAFT="${PR_DRAFT:-true}"

ensure_commit() {
  local sha="$1"
  [ -n "$sha" ] || return 1
  if ! git cat-file -e "${sha}^{commit}" 2>/dev/null; then
    git fetch --no-tags origin "$sha" >/dev/null 2>&1 || return 1
  fi
}

emit() { echo "$1=$2" >> "$GITHUB_OUTPUT"; }

if [ "$EVENT_NAME" = "workflow_dispatch" ]; then
  CHECKED_SHA="$(git rev-parse HEAD)"
  if [ -z "$TARGET_SHA" ] || [ "$CHECKED_SHA" != "$TARGET_SHA" ]; then
    echo "Dispatch checkout does not match requested target SHA." >&2
    exit 1
  fi
  git fetch --no-tags origin main
  MAIN_SHA="$(git rev-parse origin/main)"
  if [ "$PUBLISH_RELEASE" = "true" ] && [ "$MAIN_SHA" != "$TARGET_SHA" ]; then
    echo "Refusing to publish a dispatched commit that is not current main." >&2
    exit 1
  fi
  FP="$(bash ci/celine-runtime-fingerprint.sh HEAD)"
  emit app_changed true
  emit build_required true
  emit runtime_fingerprint "$FP"
  emit reusable_run_id ""
  emit reusable_source_sha ""
  exit 0
fi

if [ "$EVENT_NAME" = "pull_request" ]; then
  ensure_commit "$PR_HEAD_SHA"
  ensure_commit "$PR_BASE_SHA"
  if [ "$PR_ACTION" = "synchronize" ] && [ -n "$PR_BEFORE_SHA" ] && [ "$PR_BEFORE_SHA" != "0000000000000000000000000000000000000000" ]; then
    ensure_commit "$PR_BEFORE_SHA" || true
    DIFF_BASE="$PR_BEFORE_SHA"
  else
    DIFF_BASE="$PR_BASE_SHA"
  fi
  CHANGED="$(git diff --name-only "$DIFF_BASE" "$PR_HEAD_SHA")"
  printf '%s\n' "$CHANGED"
  if grep -Eq '^app/' <<<"$CHANGED"; then emit app_changed true; else emit app_changed false; fi

  FP="$(bash ci/celine-runtime-fingerprint.sh "$PR_HEAD_SHA")"
  emit runtime_fingerprint "$FP"

  # Final exact-head transitions deliberately rebuild even when the runtime fingerprint has appeared
  # before. Draft synchronize events may reuse a verified runtime-equivalent APK.
  if [ "$PR_DRAFT" != "true" ] || [ "$PR_ACTION" = "ready_for_review" ] || [ "$PR_ACTION" = "opened" ] || [ "$PR_ACTION" = "reopened" ]; then
    emit build_required true
    emit reusable_run_id ""
    emit reusable_source_sha ""
    echo "Final/open PR state: forcing exact-head Android build."
    exit 0
  fi

  REUSE_RUN=""
  REUSE_SHA=""
  if [ -n "${GITHUB_REPOSITORY:-}" ] && command -v gh >/dev/null 2>&1; then
    while read -r candidate_run candidate_sha; do
      [ -n "$candidate_run" ] || continue
      ensure_commit "$candidate_sha" || continue
      artifacts="$(gh api "repos/${GITHUB_REPOSITORY}/actions/runs/${candidate_run}/artifacts?per_page=100")"
      apk_id="$(jq -r '[.artifacts[] | select(.name == "yahya-ai-debug" and .expired == false)][0].id // empty' <<<"$artifacts")"
      [ -n "$apk_id" ] || continue
      metadata_id="$(jq -r --arg name "runtime-fingerprint-${FP}" '[.artifacts[] | select(.name == $name and .expired == false)][0].id // empty' <<<"$artifacts")"
      if [ -n "$metadata_id" ]; then
        REUSE_RUN="$candidate_run"; REUSE_SHA="$candidate_sha"; break
      fi
      candidate_fp="$(bash ci/celine-runtime-fingerprint.sh "$candidate_sha" 2>/dev/null || true)"
      if [ -n "$candidate_fp" ] && [ "$candidate_fp" = "$FP" ]; then
        REUSE_RUN="$candidate_run"; REUSE_SHA="$candidate_sha"; break
      fi
    done < <(gh api "repos/${GITHUB_REPOSITORY}/actions/workflows/android-build.yml/runs?status=success&per_page=60" --jq '.workflow_runs[] | "\(.id) \(.head_sha)"')
  fi

  if [ -n "$REUSE_RUN" ]; then
    emit build_required false
    emit reusable_run_id "$REUSE_RUN"
    emit reusable_source_sha "$REUSE_SHA"
    echo "Runtime fingerprint unchanged and reusable APK exists: run=$REUSE_RUN sha=$REUSE_SHA fingerprint=$FP"
  else
    emit build_required true
    emit reusable_run_id ""
    emit reusable_source_sha ""
    echo "No reusable APK for runtime fingerprint $FP; one Android build is required."
  fi
  exit 0
fi

# A push that reached this workflow is production-sensitive and gets a real build.
# Only an actual app/ change is release-eligible; CI/proof-only main merges must not
# attempt to republish an existing version tag.
CHECKED_SHA="$(git rev-parse HEAD)"
APP_CHANGED=true
if [ -n "$PR_BEFORE_SHA" ] && [ "$PR_BEFORE_SHA" != "0000000000000000000000000000000000000000" ] && ensure_commit "$PR_BEFORE_SHA"; then
  CHANGED="$(git diff --name-only "$PR_BEFORE_SHA" "$CHECKED_SHA")"
  printf '%s\n' "$CHANGED"
  if grep -Eq '^app/' <<<"$CHANGED"; then APP_CHANGED=true; else APP_CHANGED=false; fi
else
  echo "Push base is unavailable; keeping release eligibility fail-safe." >&2
fi

FP="$(bash ci/celine-runtime-fingerprint.sh HEAD)"
emit app_changed "$APP_CHANGED"
emit build_required true
emit runtime_fingerprint "$FP"
emit reusable_run_id ""
emit reusable_source_sha ""
