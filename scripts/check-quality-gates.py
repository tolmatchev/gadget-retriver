#!/usr/bin/env python3
"""Prove that representative violations are rejected in disposable checkouts."""

from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "src/main/java/io/github/tolmatchev/gadgetretriver/normalization/"
TEST_PACKAGE = "src/test/java/io/github/tolmatchev/gadgetretriver/"


def run(project, arguments, expected=None):
    result = subprocess.run(
        [str(project / "mvnw"), "-B", "-ntp", *arguments],
        cwd=project, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        timeout=600, check=False,
    )
    if expected is None:
        if result.returncode:
            raise RuntimeError(result.stdout)
    elif result.returncode == 0 or expected not in result.stdout:
        raise AssertionError(f"Expected rejection by {expected}:\n{result.stdout}")


def java(name, body):
    return f"package io.github.tolmatchev.gadgetretriver.normalization;\n\nclass {name} {{\n{body}\n}}\n"


def main():
    with tempfile.TemporaryDirectory(prefix="gadget-gates-") as temporary:
        project = Path(temporary) / "project"
        shutil.copytree(ROOT, project, ignore=shutil.ignore_patterns(".git", "target", ".idea", ".jqwik-database"))
        run(project, ["verify"])

        def probe(name, arguments, expected, files, format_java=False):
            originals = {}
            try:
                for relative, content in files.items():
                    destination = project / relative
                    originals[destination] = destination.read_bytes() if destination.exists() else None
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_text(content, encoding="utf-8")
                if format_java:
                    run(project, ["spotless:apply"])
                run(project, arguments, expected)
                print(f"PASS: {name} rejected", flush=True)
            finally:
                for destination, original in originals.items():
                    if original is None:
                        destination.unlink(missing_ok=True)
                    else:
                        destination.write_bytes(original)

        pom = (project / "pom.xml").read_text()
        conflict = "<dependency><groupId>commons-io</groupId><artifactId>commons-io</artifactId><version>2.11.0</version></dependency>"
        probe("dependency convergence", ["validate"], "DependencyConvergence", {
            "pom.xml": pom.replace("\n  <dependencies>\n", "\n  <dependencies>\n    " + conflict + "\n", 1)})
        probe("formatting", ["validate"], "spotless", {
            PACKAGE + "BadFormat.java": java("BadFormat", "int value(){return 1;}")})
        probe("Checkstyle", ["checkstyle:check"], "Checkstyle", {
            PACKAGE + "BadName.java": java("BadName", "int Bad_Method() { return 1; }")})
        probe("javac warnings", ["clean", "compile"], "-Werror", {
            PACKAGE + "RawType.java": java("RawType", "java.util.List values() { return new java.util.ArrayList(); }")}, True)
        probe("Error Prone", ["clean", "compile"], "[ReferenceEquality]", {
            PACKAGE + "BadEquality.java": java("BadEquality", "boolean same(String a, String b) { return a == b; }")}, True)
        probe("NullAway", ["clean", "compile"], "[NullAway]", {
            PACKAGE + "BadNull.java": java("BadNull", "String value() { return null; }")}, True)
        probe("SpotBugs", ["clean", "compile", "spotbugs:check"], "NP_ALWAYS_NULL", {
            PACKAGE + "BadDereference.java": java("BadDereference", "@SuppressWarnings(\"NullAway\")\nint value() { Object object = null; return object.hashCode(); }")}, True)
        probe("Find Security Bugs", ["clean", "compile", "spotbugs:check"], "WEAK_MESSAGE_DIGEST_MD5", {
            PACKAGE + "WeakDigest.java": java("WeakDigest", 'java.security.MessageDigest digest() throws java.security.NoSuchAlgorithmException { return java.security.MessageDigest.getInstance("MD5"); }')}, True)
        probe("PMD", ["pmd:check"], "PMD", {
            PACKAGE + "EmptyHandler.java": java("EmptyHandler", 'void parse(String value) { try { Integer.parseInt(value); } catch (NumberFormatException exception) {} }')})
        duplicate = "\n".join(f'System.out.println("duplicate step {index}");' for index in range(40))
        probe("CPD", ["pmd:cpd-check"], "duplication", {
            PACKAGE + "CopyOne.java": java("CopyOne", f"void run() {{\n{duplicate}\n}}"),
            PACKAGE + "CopyTwo.java": java("CopyTwo", f"void run() {{\n{duplicate}\n}}")})
        probe("fixture integrity", ["test", "-Dtest=CatalogFixtureTest"], "fixtureChecksumsMatchCommittedBytes", {
            "src/test/resources/fixtures/catalog.csv": (project / "src/test/resources/fixtures/catalog.csv").read_text() + "\n"})
        probe("architecture", ["clean", "test", "-Dtest=ArchitectureTest"], "Architecture Violation", {
            PACKAGE + "BadDependency.java": java("BadDependency", "Class<?> command() { return io.github.tolmatchev.gadgetretriver.cli.GadgetCommand.class; }")}, True)
        probe("coverage", ["clean", "verify"], "Coverage checks have not been met", {
            PACKAGE + "Uncovered.java": java("Uncovered", "public int value(boolean flag) { if (flag) { return 1; } return 2; }")}, True)
        probe("mutation threshold", ["clean", "verify", "-Pmutation"], "Mutation score of", {
            TEST_PACKAGE + "CatalogFixtureTest.java": (project / (TEST_PACKAGE + "CatalogFixtureTest.java")).read_text().replace(
                "      assertThat(row.get(IDENTIFIER))\n          .isEqualTo(IdentifierNormalizer.normalize(row.get(IDENTIFIER)));\n", ""),
            TEST_PACKAGE + "IdentifierNormalizerTest.java": '''package io.github.tolmatchev.gadgetretriver;
import io.github.tolmatchev.gadgetretriver.normalization.IdentifierNormalizer;
import org.junit.jupiter.api.Test;
class IdentifierNormalizerTest {
  @Test void executesWithoutCheckingResult() { IdentifierNormalizer.normalize(" VALUE "); }
}
'''}, True)
        print("All quality gates rejected representative violations.", flush=True)


if __name__ == "__main__":
    main()
