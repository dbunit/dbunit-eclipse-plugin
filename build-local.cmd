@echo off
rem Builds the plugin with the current UTC time as the version qualifier, to install the build into
rem Eclipse during development. build-local.sh does the same in bash.
rem
rem A plain mvnw build takes the qualifier from project.build.outputTimestamp in the root pom.xml,
rem which changes only per release, so every local build has the same version, and Eclipse does not
rem offer a rebuilt plugin as an update to the installed one. A build from this script is newer than
rem the one before it. The qualifier has the format of the pom's 'v'yyyyMMdd-HHmm, as in the CI build.
rem
rem Usage: build-local.cmd [Maven arguments]
rem   Without arguments it runs: clean install
rem   Arguments replace those goals, so give the goals too: build-local.cmd clean verify -DskipTests

setlocal
pushd "%~dp0"

set "BUILD_QUALIFIER="
for /f "usebackq delims=" %%q in (`powershell -NoProfile -NonInteractive -Command "'v' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmm')"`) do set "BUILD_QUALIFIER=%%q"
if not defined BUILD_QUALIFIER (
  echo Cannot get the current UTC time from powershell for the version qualifier.>&2
  popd
  exit /b 1
)

set "DEFAULT_GOALS="
if "%~1"=="" set "DEFAULT_GOALS=clean install"

echo Building with the version qualifier %BUILD_QUALIFIER%
call "%~dp0mvnw.cmd" -DforceContextQualifier=%BUILD_QUALIFIER% %DEFAULT_GOALS% %*
set "MAVEN_EXIT_CODE=%ERRORLEVEL%"

popd
exit /b %MAVEN_EXIT_CODE%
