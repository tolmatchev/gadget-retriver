# Build and quality policy

Use a full OpenJDK 21 distribution, version 21.0.8 or newer within Java 21.
CI pins Temurin 21.0.8+9.0.LTS; the initial local verification uses Corretto 21.0.8.
Set `JAVA_HOME` to that JDK before invoking Maven. Java 17 and Java 25 builds
are rejected, rather than silently using a different analysis toolchain.
The compiler uses `--release 21`. Update the CI JDK pin and minimum version
together after checking Error Prone / NullAway compatibility.

Maven Wrapper 3.3.4 downloads Maven 3.9.11 with a committed SHA-256 checksum.
The first build needs access to Maven Central. No globally installed Maven is
required. All directly used dependencies and lifecycle plugins have pinned
versions; Enforcer checks dependency convergence and plugin versions.

```sh
./mvnw clean verify
./mvnw spotless:apply
./mvnw clean verify -Pmutation
python3 scripts/check-quality-gates.py
```

`verify` enforces formatting, Checkstyle, `javac -Xlint:all -Werror`, Error
Prone, NullAway in JSpecify mode, SpotBugs with Find Security Bugs, PMD, CPD,
JUnit, AssertJ, jqwik, ArchUnit, fixture validation and JaCoCo. It fails on a
finding. Spotless owns layout; Checkstyle checks names, imports, file structure
and whitespace hygiene without competing indentation or line wrapping rules.
All production packages are `@NullMarked`.

JaCoCo requires at least 95% line and 90% branch coverage across production
code, and 100% branch coverage per normalization and conflict package. There
is no coverage exclusion. Future conflict resolution code must live under a
`conflict` package so the stricter rule applies automatically. The current
normalizer has no conditional branches; its behavior is checked with examples
and a reproducible 1000-sample idempotence property.

PIT uses its `STRONGER` mutator group plus `NON_VOID_METHOD_CALLS` and requires at least 90% killed mutations and fails if no mutations are
found. JaCoCo enforces the line and branch coverage requirements separately;
PIT counts private utility constructors differently from JaCoCo. Run `./mvnw clean verify -Pmutation` before creating a
release tag. The release workflow runs the same command weekly, manually and
on `v*` tags. Any future publishing step must depend on `release-verify`; the
current workflow verifies releases and publishes no artifacts.

Reports are written to `target/site/jacoco`, `target/site`,
`target/surefire-reports`, `target/spotbugsXml.xml`, `target/pmd.xml`,
`target/cpd.xml` and `target/pit-reports`. CI retains reports even on failures.
The gate regression script creates a temporary checkout and proves that
representative violations are rejected by their respective checks, including
weak cryptography, null dereferences, package boundary violations, inadequate
coverage and weak assertions that lower the mutation score. It does not
modify the working checkout or disable gates to make the baseline pass.

## Package boundaries

- `normalization`: pure identifier transformations; no ingestion, catalog or CLI dependencies.
- `ingestion`: source adapters, JDK `java.net.http.HttpClient` and provenance; no catalog or CLI dependencies.
- `catalog`: catalog generation and CSV/JSON output; may use normalization, never ingestion or CLI implementation.
- `cli`: picocli commands orchestrating the other packages.

ArchUnit enforces these directions and reserves JDK HttpClient for ingestion.
Package descriptors establish the ingestion and catalog boundaries; production
source adapters and exporters are separate issues. The initial CLI supports
help and version and a successful no-op invocation. Production ingestion and
complete lookup/export commands are intentionally outside this bootstrap.

The initial artifact is a library JAR containing the picocli command skeleton.
Command parsing is exercised directly with picocli in tests. A runnable
application distribution and process exit handling belong with the complete
CLI implementation; no manifest points to an unimplemented entry point.

## Fixture contract and catalog baselines

`src/test/resources/fixtures` contains synthetic records, a JSON Schema and a
SHA-256 manifest. These examples are not authoritative device data. Checks
validate the schema, required provenance, normalized identifiers, absence of
identical duplicate records, both names for a deliberately ambiguous
identifier, CSV/JSON equivalence, order-independent canonical serialization,
normalization idempotence and exact committed checksums. The schema rejects
missing fields, blank strings and unknown fields. Serialization helpers live
in the fixture test until production catalog generation is implemented; the
same contract checks must then exercise the production exporters.

Do not guess shrinkage or mass-change limits from three synthetic rows.
Before enabling production catalog updates, collect at least four successful
snapshots for each source and the merged catalog. Record record count,
identifier count, ambiguous identifier count, additions, removals and changed
names/provenance by source and overall. Review legitimate source variability,
then commit absolute and relative shrinkage/change limits with the measured
baseline and rationale. Reject refreshes exceeding either limit pending human
review. Baseline updates belong in a separate reviewed change with source
revisions and before/after statistics; an empty or unavailable source must
never silently replace the catalog. Until measured limits exist, production
refreshes must not be published automatically.

## Suppressions

Do not add package-wide analysis exclusions or reduce thresholds to accept a
change. Fix findings first. Any unavoidable suppression must name a specific
rule and the smallest possible symbol or statement, explain the false positive
or invariant in English, and include a regression test when behavior is
involved. Review removal when upgrading the tool.

PMD's `UseConcurrentHashMap` rule is disabled individually because it flags
method-local `TreeMap` instances used to guarantee deterministic fixture key
ordering; those maps are never shared between threads. All other selected
multithreading rules remain enabled. No production analysis annotations are
suppressed. Deliberately invalid code in the gate regression script is
isolated in temporary projects and never packaged.

## CI and repository settings

PRs and main pushes run `verify`, `gate-regressions`, `secrets` and `workflows`.
PRs additionally run `dependencies`, rejecting moderate or higher newly
introduced dependency advisories. Dependabot checks Maven and Actions weekly.
Gitleaks scans the fetched history; actionlint validates workflows and zizmor
audits their security. Actions use full commit SHAs, default permissions are
`contents: read`, checkout credentials are not persisted, and untrusted PR
code never runs via `pull_request_target` or with a write token.

The desired main branch protection is committed in
`config/main-branch-protection.json`. Apply it with repository administration
credentials after these workflow check names have been registered by a PR:

```sh
gh api --method PUT repos/tolmatchev/gadget-retriver/branches/main/protection \
  --input config/main-branch-protection.json
gh api repos/tolmatchev/gadget-retriver/branches/main/protection
```

It requires current PR checks, applies to administrators, forbids force
pushes and branch deletion, and requires PRs with resolved conversations and
linear history. Zero required approvals allows a solo maintainer to merge
after checks pass. The JSON file alone does not activate server-side
protection; confirm the API response before considering that part complete.
