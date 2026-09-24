#!/usr/bin/env bash
#
# Assembles the Eclipse update site (p2) inside the generated Maven site directory:
#
#   <site>/updates/                     p2 composite repository of every released version;
#                                       the URL users add in Help > Install New Software
#   <site>/updates/releases/<version>/  each release's p2 repository, unzipped from the
#                                       dbunit-eclipse-plugin-<version>-p2-repository.zip asset
#                                       of its GitHub Release (tag eclipse-plugin-<version>)
#   <site>/updates/nightly/             the latest build of main; not part of the composite
#
# Usage: assemble-update-site.sh <site-dir> [<nightly-p2-repository-dir>]
# Environment: REPOSITORY=<owner>/<repo>; GH_TOKEN for the gh CLI.
# Requires: gh, unzip, sort with -V.

set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <site-dir> [<nightly-p2-repository-dir>]" >&2
  exit 2
fi

site_dir="$1"
nightly_dir="${2:-}"
repository="${REPOSITORY:?REPOSITORY must be set to <owner>/<repo>}"
tag_prefix="eclipse-plugin-"
updates_dir="${site_dir}/updates"
releases_dir="${updates_dir}/releases"
download_dir="$(mktemp -d)"
trap 'rm -rf "${download_dir}"' EXIT

mkdir -p "${releases_dir}"

# Released versions, oldest first, from non-draft GitHub Releases with the release tag prefix.
# gh runs in a plain assignment, where set -e stops the script when it fails; inside the process
# substitution below, a failure would go unnoticed and publish an empty composite over the live site.
release_tags="$(gh release list --repo "${repository}" --limit 1000 --exclude-drafts \
    --json tagName --jq '.[].tagName')"
mapfile -t versions < <(
  printf '%s\n' "${release_tags}" \
    | { grep "^${tag_prefix}" || true; } \
    | sed "s/^${tag_prefix}//" \
    | sort -V
)

for version in "${versions[@]}"; do
  echo "Adding release ${version}"
  gh release download "${tag_prefix}${version}" --repo "${repository}" \
      --pattern "dbunit-eclipse-plugin-${version}-p2-repository.zip" \
      --dir "${download_dir}/${version}"
  mkdir -p "${releases_dir}/${version}"
  unzip -q "${download_dir}/${version}/dbunit-eclipse-plugin-${version}-p2-repository.zip" \
      -d "${releases_dir}/${version}"
done

timestamp="$(date +%s)000"

write_composite() {
  local file="$1"
  local processing_instruction="$2"
  local repository_type="$3"
  {
    echo "<?xml version='1.0' encoding='UTF-8'?>"
    echo "<?${processing_instruction} version='1.0.0'?>"
    echo "<repository name='dbUnit Eclipse Plugin' type='${repository_type}' version='1.0.0'>"
    echo "  <properties size='2'>"
    echo "    <property name='p2.timestamp' value='${timestamp}'/>"
    echo "    <property name='p2.atomic.composite.loading' value='true'/>"
    echo "  </properties>"
    echo "  <children size='${#versions[@]}'>"
    for version in "${versions[@]}"; do
      echo "    <child location='releases/${version}'/>"
    done
    echo "  </children>"
    echo "</repository>"
  } > "${file}"
}

write_composite "${updates_dir}/compositeContent.xml" \
    compositeMetadataRepository \
    org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository
write_composite "${updates_dir}/compositeArtifacts.xml" \
    compositeArtifactRepository \
    org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository

cat > "${updates_dir}/p2.index" <<'EOF'
version=1
metadata.repository.factory.order=compositeContent.xml,\!
artifact.repository.factory.order=compositeArtifacts.xml,\!
EOF

if [ -n "${nightly_dir}" ] && [ -d "${nightly_dir}" ]; then
  echo "Adding nightly build from ${nightly_dir}"
  rm -rf "${updates_dir}/nightly"
  cp -R "${nightly_dir}" "${updates_dir}/nightly"
fi

# A human-readable page for anyone who opens the update site URL in a browser.
cat > "${updates_dir}/index.html" <<'EOF'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>dbUnit Eclipse Plugin update site</title>
</head>
<body>
  <h1>dbUnit Eclipse Plugin update site</h1>
  <p>This URL is an Eclipse update site (p2 repository); open it from inside Eclipse:</p>
  <ol>
    <li>Help &gt; Install New Software...</li>
    <li>Work with: <code>https://dbunit.github.io/dbunit-eclipse-plugin/updates/</code></li>
    <li>Select <em>dbUnit Dataset Editor</em> and finish the wizard.</li>
  </ol>
  <p>Documentation: <a href="../">dbUnit Eclipse Plugin</a></p>
</body>
</html>
EOF

echo "Update site assembled with ${#versions[@]} release(s) in ${updates_dir}"
