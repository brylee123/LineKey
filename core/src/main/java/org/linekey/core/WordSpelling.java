// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.Locale;

/** Display spelling and the letters-only sequence used for approximate typing. */
public final class WordSpelling {
  private static final java.util.regex.Pattern VALID =
      java.util.regex.Pattern.compile("[a-z]+(?:(?:['.&/-]|'-)[a-z]+)*(?:\\.|\\+\\+|#)?");

  private WordSpelling() {}

  public static String canonical(String word) {
    return word.toLowerCase(Locale.ROOT)
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('‐', '-')
        .replace('‑', '-');
  }

  public static String letters(String word) {
    return matchingLetters(canonical(word));
  }

  // Dictionary entries are already canonical; avoid repeating Unicode/case normalization at load.
  static String matchingLetters(String spelling) {
    StringBuilder letters = null;
    for (int index = 0; index < spelling.length(); index++) {
      char c = spelling.charAt(index);
      if (c < 'a' || c > 'z') {
        if (letters == null) letters = new StringBuilder(spelling.substring(0, index));
      } else if (letters != null) letters.append(c);
    }
    return letters == null ? spelling : letters.toString();
  }

  /** Supported lexical separators only; reject whitespace, digits and arbitrary symbols. */
  public static boolean valid(String word) {
    if (word == null || word.length() > 64) return false;
    String spelling = canonical(word);
    return VALID.matcher(spelling).matches() && letters(spelling).length() <= 32;
  }
}
