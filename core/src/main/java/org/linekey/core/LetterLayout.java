// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Independently defined layouts. The compact strip orders letters by horizontal QWERTY position.
 */
public final class LetterLayout {
    public static final String[] NAMES = {"QWERTY", "QWERTZ", "AZERTY", "Alphabetical"};
    private static final String[][] ROWS = {
        {"qwertyuiop", "asdfghjkl", "zxcvbnm"},
        {"qwertzuiop", "asdfghjkl", "yxcvbnm"},
        {"azertyuiop", "qsdfghjklm", "wxcvbn"},
        {"abcdefghij", "klmnopqrs", "tuvwxyz"}
    };
    private final String[] rows;
    private final String compact;

    public LetterLayout(final int index) {
        rows = ROWS[Math.max(0, Math.min(ROWS.length - 1, index))].clone();
        final List<Character> letters = new ArrayList<>();
        for (final String row : rows) {
            for (final char c : row.toCharArray()) {
                letters.add(c);
            }
        }
        if (index != 3) {
            letters.sort(Comparator.comparingDouble(this::fullX));
        }
        final StringBuilder strip = new StringBuilder();
        for (final char c : letters) {
            strip.append(c);
        }
        compact = strip.toString();
    }

    private double fullX(final char c) {
        for (int r = 0; r < rows.length; r++) {
            final int k = rows[r].indexOf(c);
            if (k >= 0) {
                return (k + .5) / 10.0 + r * .0001;
            }
        }
        return .5;
    }

    public String[] rows() {
        return rows.clone();
    }

    public String compact() {
        return compact;
    }

    public double center(final char c) {
        return (compact.indexOf(Character.toLowerCase(c)) + .5) / compact.length();
    }

    /** Vertical position retains the source row while horizontal targets stay distinct. */
    public double vertical(final char letter) {
        for (int row = 0; row < rows.length; row++) {
            if (rows[row].indexOf(Character.toLowerCase(letter)) >= 0) {
                return .40 + row * .19;
            }
        }
        return .5;
    }

    public char nearest(final double x) {
        return compact.charAt(
                Math.max(0, Math.min(compact.length() - 1, (int) (x * compact.length()))));
    }
}
