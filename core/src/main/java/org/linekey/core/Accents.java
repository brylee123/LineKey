// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.Locale;

/** Original character variations; each selector retains the literal base letter. */
public final class Accents {
    public static String choices(final char letter, final boolean uppercase) {
        final String choices =
                switch (Character.toLowerCase(letter)) {
                    case 'a' -> "aàáâäæãåā";
                    case 'e' -> "eèéêëēėę";
                    case 'i' -> "iìíîïīįı";
                    case 'o' -> "oòóôöœõøō";
                    case 'u' -> "uùúûüū";
                    case 'c' -> "cçćč";
                    case 'n' -> "nñńň";
                    case 's' -> "sßśšș";
                    case 'y' -> "yýÿ";
                    case 'z' -> "zźżž";
                    case 'l' -> "lłľ";
                    case 'r' -> "rřŕ";
                    case 'd' -> "dďđ";
                    case 't' -> "tťț";
                    case 'g' -> "gğģ";
                    default -> String.valueOf(Character.toLowerCase(letter));
                };
        // Uppercase sharp S is one character, preserving selector cell geometry.
        return uppercase ? choices.replace('ß', 'ẞ').toUpperCase(Locale.ROOT) : choices;
    }
}
