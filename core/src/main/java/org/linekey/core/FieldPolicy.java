// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.core;

/** Pure Java policy using public Android input-type constants; no Android dependency. */
public record FieldPolicy(
    boolean numeric, boolean phone, boolean privateField, boolean literal, boolean rawKeys) {
  public boolean voiceAllowed() {
    return !literal && !privateField && !rawKeys && !numeric && !phone;
  }

  public static FieldPolicy from(int inputType, int imeOptions) {
    int kind = inputType & 15, variant = inputType & 4080;
    boolean password =
        (kind == 1 && (variant == 128 || variant == 144 || variant == 224))
            || (kind == 2 && variant == 16);
    boolean literal =
        kind != 1
            || variant == 16
            || variant == 32
            || variant == 208
            || password
            || (inputType & 0x80000) != 0;
    return new FieldPolicy(
        kind == 2, kind == 3, password || (imeOptions & 0x1000000) != 0, literal, kind == 0);
  }
}
