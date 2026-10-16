// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

/** Volatile dictation draft. Never written to preferences or logs. */
final class VoiceDraft {
    private static String text = "";

    static synchronized void set(final String value) {
        text = value == null ? "" : value;
    }

    static synchronized String get() {
        return text;
    }

    static synchronized void clear() {
        text = "";
    }
}
