package io.github.tolmatchev.gadgetretriver;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.github.tolmatchev.gadgetretriver.normalization.IdentifierNormalizer;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.junit.jupiter.api.Test;

class CatalogFixtureTest {
  private static final String IDENTIFIER = "identifier";
  private static final String MARKETING_NAME = "marketingName";
  private static final String SOURCE = "source";
  private static final String SOURCE_REVISION = "sourceRevision";
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String[] COLUMNS = {IDENTIFIER, MARKETING_NAME, SOURCE, SOURCE_REVISION};
  private static final Comparator<Map<String, String>> ORDER =
      Comparator.comparing((Map<String, String> row) -> row.get(IDENTIFIER))
          .thenComparing(row -> row.get(MARKETING_NAME))
          .thenComparing(row -> row.get(SOURCE))
          .thenComparing(row -> row.get(SOURCE_REVISION));

  @Test
  void fixturesMatchSchemaAndPreserveProvenanceAndAmbiguities() throws IOException {
    JsonNode schema = MAPPER.readTree(FIXTURES.resolve("catalog.schema.json").toFile());
    var validator =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schema);
    JsonNode catalog = MAPPER.readTree(FIXTURES.resolve("catalog.json").toFile());
    assertThat(validator.validate(catalog)).isEmpty();
    assertThat(validator.validate(MAPPER.readTree("[{\"identifier\":\"\"}]"))).isNotEmpty();
    List<Map<String, String>> rows = jsonRows();
    assertThat(new HashSet<>(rows)).hasSize(rows.size());
    for (Map<String, String> row : rows) {
      assertThat(row.get(IDENTIFIER))
          .isEqualTo(IdentifierNormalizer.normalize(row.get(IDENTIFIER)));
      assertThat(URI.create(row.get(SOURCE)).getScheme()).isEqualTo("https");
      assertThat(URI.create(row.get(SOURCE)).getHost()).isNotBlank();
      assertThat(row.get(SOURCE_REVISION)).isNotBlank();
    }
    assertThat(
            rows.stream()
                .filter(row -> "shared-1".equals(row.get(IDENTIFIER)))
                .map(row -> row.get(MARKETING_NAME)))
        .containsExactly("Example Device A", "Example Device B");
  }

  @Test
  void csvAndJsonRepresentTheSameRowsAndSerializeDeterministically() throws IOException {
    List<Map<String, String>> rows = jsonRows();
    var format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
    try (var parser =
        format.parse(new StringReader(Files.readString(FIXTURES.resolve("catalog.csv"))))) {
      assertThat(parser.getHeaderNames()).containsExactly(COLUMNS);
      assertThat(parser.getRecords().stream().map(record -> record.toMap()).toList())
          .containsExactlyElementsOf(rows);
    }
    List<Map<String, String>> reversed = new ArrayList<>(rows.reversed());
    assertThat(canonicalJson(reversed)).isEqualTo(canonicalJson(rows));
    assertThat(canonicalCsv(reversed)).isEqualTo(canonicalCsv(rows));
    assertThat(MAPPER.readTree(canonicalJson(rows))).isEqualTo(MAPPER.valueToTree(rows));
  }

  @Test
  void fixtureChecksumsMatchCommittedBytes() throws IOException, NoSuchAlgorithmException {
    List<String> manifest = Files.readAllLines(FIXTURES.resolve("SHA256SUMS"));
    assertThat(manifest).hasSize(3);
    for (String line : manifest) {
      String[] parts = line.split("  ", 2);
      assertThat(parts).hasSize(2);
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(Files.readAllBytes(FIXTURES.resolve(parts[1])));
      assertThat(HexFormat.of().formatHex(digest)).isEqualTo(parts[0]);
    }
  }

  private static List<Map<String, String>> jsonRows() throws IOException {
    List<Map<String, String>> rows = new ArrayList<>();
    for (JsonNode node : MAPPER.readTree(FIXTURES.resolve("catalog.json").toFile())) {
      Map<String, String> row = new TreeMap<>();
      for (String column : COLUMNS) {
        row.put(column, node.path(column).asText());
      }
      rows.add(row);
    }
    return rows;
  }

  private static byte[] canonicalJson(List<Map<String, String>> rows) throws IOException {
    return MAPPER
        .writeValueAsString(rows.stream().sorted(ORDER).toList())
        .getBytes(StandardCharsets.UTF_8);
  }

  private static String canonicalCsv(List<Map<String, String>> rows) throws IOException {
    StringWriter output = new StringWriter();
    try (CSVPrinter printer =
        new CSVPrinter(output, CSVFormat.RFC4180.builder().setHeader(COLUMNS).get())) {
      for (Map<String, String> row : rows.stream().sorted(ORDER).toList()) {
        printer.printRecord(
            row.get(IDENTIFIER),
            row.get(MARKETING_NAME),
            row.get(SOURCE),
            row.get(SOURCE_REVISION));
      }
    }
    return output.toString();
  }
}
