# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

The **dbUnit Eclipse Plugin** adds a spreadsheet-like editor for [dbUnit](https://github.com/dbunit/dbunit-extension) dataset files to the Eclipse IDE. Version 1 supports the flat XML format (`FlatXmlDataSet`). It is built with Maven and Eclipse Tycho and published as a PGP-signed p2 repository (Eclipse update site) on GitHub Pages.

## Build Commands

The project uses a Maven wrapper. Always use `./mvnw` instead of `mvn`. Tycho 5 requires JDK 21+ to run the build; the bundles target Java 17 (`Bundle-RequiredExecutionEnvironment: JavaSE-17`).

```bash
# Build, run all tests, and assemble the update site (default goal is verify)
./mvnw clean verify

# Build and test against the newest Eclipse release instead of the baseline (oldest supported)
./mvnw clean verify -Platest-target

# Core bundle unit tests only (plain JUnit, no OSGi) - fast inner loop.
# List the target platform module explicitly: -am cannot find it, because Tycho references it
# through target-platform-configuration rather than as a Maven dependency.
./mvnw clean verify -pl releng/org.dbunit.eclipse.target,bundles/org.dbunit.eclipse.dataset.core

# A single core test class or method
./mvnw clean verify -pl releng/org.dbunit.eclipse.target,bundles/org.dbunit.eclipse.dataset.core -Dtest=FlatXmlParserTest
./mvnw clean verify -pl releng/org.dbunit.eclipse.target,bundles/org.dbunit.eclipse.dataset.core -Dtest=FlatXmlParserTest#testParse_whenEmptyDataset_returnsNoElements

# Skip tests
./mvnw clean verify -DskipTests
```

UI tests (`bundles/org.dbunit.eclipse.dataset.ui/src/test/java`) run inside an Eclipse workbench via `tycho-surefire-plugin:plugin-test`; on Linux without a display, prefix the command with `xvfb-run -a`.

## Architecture

* `bundles/org.dbunit.eclipse.dataset.core` - UI-independent: dataset model, flat XML parser, text-edit engine, DTD reader, validation. Unit tests run with maven-surefire-plugin (no OSGi).
* `bundles/org.dbunit.eclipse.dataset.ui` - the multi-page editor (Tables page with Nebula NatTable grids, Source page), commands, preferences, wizard. Tests run in a workbench.
* `features/org.dbunit.eclipse.feature` - the installable feature.
* `releng/org.dbunit.eclipse.target` - target platforms: `org.dbunit.eclipse.target.target` (baseline, Eclipse 2024-06) and `latest.target`.
* `releng/org.dbunit.eclipse.repository` - the p2 repository (`category.xml`); `-Psign` PGP-signs it.

The IDocument (text) is the single source of truth: grid edits become minimal text edits applied to the document, so undo/redo, dirty state, save, and file synchronization come from the Eclipse text infrastructure.

## Claude Directives

- Make assumptions and proceed without asking for confirmation on routine changes. If an action is destructive (e.g., deleting files), pause and ask.
- Never use Eclipse internal packages (`*.internal.*`) or classes marked `@noextend`/`@noinstantiate`/`@noreference` contrary to their restriction.

## Code Style

- General:
  - Prefer writing clear code and use inline comments sparingly.
  - Prefer single statements over compound statements as nested calls in one line are more confusing and more difficult to read and understand.
  - Prefer separate local variables over compound statements for readability.
  - Favor immutable data - try to not need setters.
  - Prefer constructors with arguments over no args constructors and using setters.
  - Prefer constructor injection
  - Use == instead of != in if statements when paired with an else statement.
  - Remove any blank line after opening curly braces.
  - Do not create "utils" or "helper" packages or class names. Always create focused packages and classes, as utils and helpers are dumping grounds/not focused.
  - When making changes, always work on a branch that is not main and if necessary, create and switch to a branch to isolate the work.
  - When making changes, ensure tests cover it and add or update tests as needed.
- Tests:
  - `<ClassName>Test` for unit test class (UT)
  - `<ClassName>IT` for integration test class (IT)
  - `<ClassName>AT` for acceptance test class (AT)
  - `test<MethodName>_<StartingStateConditions>_<AssertedOutcome>` for test method names
  - Prefer to assert the actual object to an expected object vs individual fields on the object to individual values.

- Commits:
  - Create atomic commits. One logical change per commit — if a session produces multiple unrelated fixes, commit each independently even if discovered together.
  - Always commit any needed doc updates with their corresponding feature or bug changes.
  - Consequence changes belong in the same commit as the change that caused them.
  - When necessary to change a file for a prior commit that is not yet merged to main, target that commit for squashing the change into by using the git "fixup!" feature for its commit - prefix the commit message it is in with "fixup! ".
  - When renaming files, always use `git mv` instead of `git delete` followed by `git add`.

- Commit Messages:
  - Adhere strictly to de facto standard Git commit message formatting.
  - Use Conventional Commits format.
  - **Commit Types:** `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`, `ci:`, `perf:`
  - **Scopes:** `core`, `model`, `flatxml`, `dtd`, `validation`, `editor`, `grid`, `source`, `clipboard`, `prefs`, `wizard`, `pom`, `target`, `feature`, `repository`, `site`, `release`, `mvn`, `ide`, `dependabot`, `ai`, `git`
  - Capitalize the first word after the type and scope.
  - You may suggest additional CC commit types and scopes when encountering situations where the changes do not fit into the approved lists above.
  - Reference GitHub issues in the commit footer with `Refs: <issue-number>` (e.g. `Refs: 123`).  Do not use a # before the number.
  - Do not put the issue number in the message topic.
  - Use * for bullets, not -.
  - Do not refer to files that are not committed.
  - Commit messages must only describe the final code state within that specific commit. Never include references to intermediate fixes, internal feedback loops, or temporary issues introduced and resolved during development.

- Java:
  - Use Eclipse code formatter settings file `java-codestyle-formatter.xml` (profile "dbUnit Code Format": braces on the next line, 4 spaces) when modifying or creating files.
  - Use Eclipse code cleanup settings file `code-cleanup-eclipse.xml` when modifying or creating files (e.g., `final` parameters, locals, and private fields).
  - Start every Java file with the dbUnit LGPL license header used by the existing sources.
  - Place the Logger variable first in the class; log through Eclipse's `org.eclipse.core.runtime.ILog` (`private static final ILog LOG = ILog.of(ClassName.class);`), not a logging framework.
  - Write JavaDoc comments on all public classes and methods in src/main.
  - In JavaDoc, use complete sentences for all descriptions, start with a capital letter and end with a period, for everything - the topic body, parameters, and return, including all annotations such as @param and @throws.
  - Tests:
    - Prefer assertJ.
    - Prefer to add ".as()" with a fail message ending with a period.

- GitHub
  - When creating a github issue, set the applicable labels, assignee, and issue type, and milestone as best can determine.  ask if needed.
  - Do not create GitHub issues for verification-type tasks, only create them for features, bugs, and file changing actions.
  - In issues, do not refer to files that are not committed.
  - Do not push commits unless told to.
  - Do not open pull requests unless told to, as it prematurely uses the very limited code review services; the user knows when it is ready for that.
  - Wait until the code is pushed before replying to PR feedback.

- dbUnit Organization:
  - changes.xml file (`src/changes/changes.xml`)
    - Always create and commit changes.xml updates with the corresponding feature or bug changes.
    - Add changes.xml updates at the bottom of the list.
    - Ensure each changes.xml entry has these attributes populated and ask when unknown: dev, type, issue, system="github", and due-to
    - Valid entries for the type field are: add, fix, update, remove
    - Keep the release element's `description` attribute a short 1-2 sentence summary of the release's overall themes. Update it only when a new theme is introduced.

- dbUnit Eclipse Plugin Specific Items:
  - Monitor a branch's CI build result at <https://github.com/dbunit/dbunit-eclipse-plugin/actions/workflows/build-any-branch.yml> for issues to correct.
  - Keep `org.dbunit.eclipse.dataset.core` free of UI dependencies (no `org.eclipse.ui*`, `org.eclipse.swt`, `org.eclipse.jface` other than `org.eclipse.jface.text` types from the `org.eclipse.text` bundle).
  - All SWT access happens on the UI thread; dispose every Color, Font, Image, and Cursor you create (prefer `JFaceResources`/`LocalResourceManager`).

## Troubleshooting

- Tycho resolution errors: the target platforms deliberately ignore p2 repository references (`followRepositoryReferences="false"`, `referencedRepositoryMode=ignore`); never re-enable them, as they silently pull in newer Eclipse releases.
- UI test failures: the workbench log is at `bundles/org.dbunit.eclipse.dataset.ui/target/work/data/.metadata/.log`; reports are in `bundles/org.dbunit.eclipse.dataset.ui/target/failsafe-reports`.
- UI tests report "No tests found": a test library did not resolve in the OSGi test runtime, so Tycho's test fragment was not attached. Compare `target/work/configuration/config.ini` with the fragment's `Import-Package`; test-only bundles must be listed as `extraRequirements` in the UI `pom.xml` (as AssertJ is).
