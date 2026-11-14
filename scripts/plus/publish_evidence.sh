#!/usr/bin/env bash
# Publishes a CI evidence directory to refs/ci-evidence/gp2/<sha>-<label>.
# It is a throw-away ref outside refs/heads and refs/tags: no branch, tag or
# release is created and the working branch history is not touched.
set -u
label=$1; dir=$2
[ -d "$dir" ] || { echo "no evidence dir $dir"; exit 0; }
tmp=$(mktemp -d)
cp -r "$dir"/. "$tmp"/
cd "$tmp" || exit 0
find . -type f -size +40M -delete
git init -q
git config user.name "google-plus-2-ci"
git config user.email "ci@users.noreply.github.com"
git add -A
git commit -qm "Google Plus 2 evidence: $label @ ${GITHUB_SHA}" || exit 0
git push -q --force "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" \
  "HEAD:refs/ci-evidence/gp2/${GITHUB_SHA:0:12}-${label}" || echo "evidence push failed"
