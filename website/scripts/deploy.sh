#!/usr/bin/env bash
set -euo pipefail

deployment_branch="${DEPLOYMENT_BRANCH:-website}"
deployment_remote="${DEPLOYMENT_REMOTE:-origin}"
source_commit="$(git rev-parse --verify "${SOURCE_COMMIT:-${GITHUB_SHA:-HEAD}}^{commit}")"
source_tree="$(git rev-parse --verify "${source_commit}:website")"
git check-ref-format --branch "$deployment_branch" >/dev/null

for file in package.json package-lock.json Procfile server.mjs; do
  git cat-file -e "${source_commit}:website/${file}"
done

remote_ref="refs/heads/${deployment_branch}"
remote_status=0
git ls-remote --exit-code --heads "$deployment_remote" "$remote_ref" >/dev/null || remote_status=$?
set --
if [[ "$remote_status" == 0 ]]; then
  git fetch --no-tags "$deployment_remote" "$remote_ref"
  parent="$(git rev-parse FETCH_HEAD)"
  if [[ "$(git rev-parse "${parent}^{tree}")" == "$source_tree" ]]; then
    printf 'Website is already current on %s.\n' "$deployment_branch"
    exit 0
  fi
  set -- -p "$parent"
elif [[ "$remote_status" != 2 ]]; then
  printf 'Unable to inspect deployment branch (git exit %s).\n' "$remote_status" >&2
  exit "$remote_status"
fi

deployment_commit="$(printf 'Publish website from %s\n\nSource-commit: %s\n' "$source_commit" "$source_commit" |
  git -c user.name='github-actions[bot]' \
      -c user.email='41898282+github-actions[bot]@users.noreply.github.com' \
      commit-tree "$source_tree" "$@")"

git push "$deployment_remote" "${deployment_commit}:${remote_ref}"
printf 'Published website from %s to %s.\n' "$source_commit" "$deployment_branch"
