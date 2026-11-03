<p align="center">
  <img src="assets/brand/readme-logo.png" width="180" height="180" alt="Gadget Retriver">
</p>

# Gadget Retriver

A versioned catalog mapping technical device identifiers to marketing names.

Java 21 CLI scaffold with Maven and mandatory quality gates. Production data
ingestion and CSV/JSON exports are planned separately.

Use OpenJDK 21.0.8 or newer within Java 21, with `JAVA_HOME` set accordingly:

```sh
./mvnw clean verify
./mvnw clean verify -Pmutation
```

See [build and quality policy](docs/quality.md) for local commands, package
boundaries, fixture contracts, CI, suppressions and branch protection.
