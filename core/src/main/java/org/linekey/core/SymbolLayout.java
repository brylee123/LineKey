// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

/**
 * Familiar number and symbol rows; the final row reserves its ends for page and Delete controls.
 */
public final class SymbolLayout {
    public static String[] rows(final boolean secondary) {
        return secondary
                ? new String[] {"~`|•√π÷×§Δ", "£¢€¥^°={}\\", "%©®™✓[]"}
                : new String[] {"1234567890", "@#$_&-+()/", "*\"':;!?"};
    }

    public static String operators(final boolean phone) {
        return phone ? "+,;" : "+-*/()";
    }

    private SymbolLayout() {}
}
