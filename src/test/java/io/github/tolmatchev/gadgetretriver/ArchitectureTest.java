package io.github.tolmatchev.gadgetretriver;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  private static final String INGESTION = "..ingestion..";

  @Test
  void packageBoundariesStayAcyclic() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.tolmatchev.gadgetretriver");
    noClasses()
        .that()
        .resideInAPackage("..normalization..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(INGESTION, "..catalog..", "..cli..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage(INGESTION)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..catalog..", "..cli..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("..catalog..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(INGESTION, "..cli..")
        .check(classes);
    noClasses()
        .that()
        .resideOutsideOfPackage(INGESTION)
        .should()
        .dependOnClassesThat()
        .resideInAPackage("java.net.http..")
        .check(classes);
  }
}
