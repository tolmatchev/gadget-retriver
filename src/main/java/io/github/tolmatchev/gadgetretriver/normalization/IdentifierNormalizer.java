package io.github.tolmatchev.gadgetretriver.normalization;

import java.util.Locale;

/** Normalizes surrounding whitespace and case without removing meaningful punctuation. */
public final class IdentifierNormalizer {
  private IdentifierNormalizer() {}

  public static String normalize(String identifier) {
    return identifier.strip().toLowerCase(Locale.ROOT);
  }
}
