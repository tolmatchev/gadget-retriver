package io.github.tolmatchev.gadgetretriver;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tolmatchev.gadgetretriver.normalization.IdentifierNormalizer;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class IdentifierNormalizerTest {
  @Test
  void trimsCaseAndPreservesPunctuation() {
    assertThat(IdentifierNormalizer.normalize("  SM-G991B \t")).isEqualTo("sm-g991b");
    assertThat(IdentifierNormalizer.normalize("I")).isEqualTo("i");
    assertThat(IdentifierNormalizer.normalize("  ")).isEmpty();
    assertThat(IdentifierNormalizer.normalize("iphone14,2")).isEqualTo("iphone14,2");
  }

  @Property(tries = 1000, seed = "20261028")
  void normalizationIsIdempotent(@ForAll String identifier) {
    String normalized = IdentifierNormalizer.normalize(identifier);
    assertThat(IdentifierNormalizer.normalize(normalized)).isEqualTo(normalized);
  }
}
