// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

/** Main layout cycle plus an explicitly selected calculator-style number pad. */
public enum KeyboardMode {
  MINI,
  ALPHABET,
  NUMBERS,
  SYMBOLS,
  NUMPAD;

  public KeyboardMode next(boolean allowMini) {
    return switch (this) {
      case MINI -> ALPHABET;
      case ALPHABET -> NUMBERS;
      case NUMBERS -> SYMBOLS;
      case SYMBOLS -> allowMini ? MINI : ALPHABET;
      case NUMPAD -> ALPHABET;
    };
  }

  public String nextLabel(boolean allowMini) {
    return switch (next(allowMini)) {
      case MINI -> "Mini";
      case ALPHABET -> "ABC";
      case NUMBERS -> "123";
      case SYMBOLS -> "#+=";
      case NUMPAD -> "1234";
    };
  }
}
