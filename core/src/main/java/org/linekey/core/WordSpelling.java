// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.Locale;
import java.util.regex.Pattern;

/** Display spelling and the letters-only sequence used for approximate typing. */
public final class WordSpelling {
    private static final Pattern VALID =
            Pattern.compile("[a-z]+(?:(?:['.&/-]|'-)[a-z]+)*(?:\\.|\\+\\+|#)?");

    private WordSpelling() {}

    public static String canonical(final String word) {
        return word.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('‐', '-')
                .replace('‑', '-');
    }

    public static String letters(final String word) {
        return matchingLetters(canonical(word));
    }

    // Dictionary entries are already canonical; avoid repeating Unicode/case normalization at load.
    static String matchingLetters(final String spelling) {
        StringBuilder letters = null;
        for (int index = 0; index < spelling.length(); index++) {
            final char c = spelling.charAt(index);
            if (c < 'a' || c > 'z') {
                if (letters == null) {
                    letters = new StringBuilder(spelling.substring(0, index));
                }
            } else if (letters != null) {
                letters.append(c);
            }
        }
        return letters == null ? spelling : letters.toString();
    }

    /** Supported lexical separators only; reject whitespace, digits and arbitrary symbols. */
    public static boolean valid(final String word) {
        if (word == null || word.length() > 64) {
            return false;
        }
        final String spelling = canonical(word);
        return VALID.matcher(spelling).matches() && letters(spelling).length() <= 32;
    }
}
