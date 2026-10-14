// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

/** Shared palette for compact letters, expanded keys, actions, and transient popups. */
final class KeyboardColors {
  final int surface, key, action, text, muted, outline;
  final int accent = android.graphics.Color.parseColor("#80C7BF");
  final int accentText = android.graphics.Color.parseColor("#183A35");

  KeyboardColors(boolean dark) {
    surface = color(dark ? "#0E2023" : "#F4F8F6");
    key = color(dark ? "#182D30" : "#DFEBE8");
    action = color(dark ? "#31565B" : "#C8DED7");
    text = color(dark ? "#F0FAF8" : "#122D2A");
    muted = color(dark ? "#849A99" : "#55736F");
    outline = color(dark ? "#628A8C" : "#7FA69B");
  }

  private static int color(String value) {
    return android.graphics.Color.parseColor(value);
  }
}
