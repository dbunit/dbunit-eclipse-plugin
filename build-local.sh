#!/usr/bin/env bash
#
# Builds the plugin with the current UTC time as the version qualifier, to install the build into
# Eclipse during development. build-local.cmd does the same on Windows.
#
# A plain mvnw build takes the qualifier from project.build.outputTimestamp in the root pom.xml,
# which changes only per release, so every local build has the same version, and Eclipse does not
# offer a rebuilt plugin as an update to the installed one. A build from this script is newer than
# the one before it. The qualifier has the format of the pom's 'v'yyyyMMdd-HHmm, as in the CI build.
#
# Usage: build-local.sh [<Maven arguments>]
#   Without arguments it runs: clean install
#   Arguments replace those goals, so give the goals too: build-local.sh clean verify -DskipTests

set -euo pipefail

cd "$(dirname "$0")"

if [ "$#" -eq 0 ]; then
  set -- clean install
fi

qualifier="v$(date -u +%Y%m%d-%H%M)"
echo "Building with the version qualifier ${qualifier}"

exec ./mvnw -DforceContextQualifier="${qualifier}" "$@"
