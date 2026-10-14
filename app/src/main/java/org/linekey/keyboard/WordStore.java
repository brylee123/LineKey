// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;
import org.json.JSONObject;
import org.linekey.core.Decoder;

/** Optional on-device word counts. No surrounding text, passwords, or network traffic. */
final class WordStore {
  private final SharedPreferences store;

  WordStore(Context context) {
    store = context.getSharedPreferences("linekey_words", Context.MODE_PRIVATE);
  }

  private JSONObject read() {
    try {
      return new JSONObject(store.getString("counts", "{}"));
    } catch (Exception ignored) {
      return new JSONObject();
    }
  }

  void load(Decoder decoder) {
    JSONObject words = read();
    for (var it = words.keys(); it.hasNext(); ) {
      String word = it.next();
      decoder.addWord(word, words.optInt(word, 1));
    }
  }

  boolean learn(String raw) {
    String word = raw.toLowerCase(Locale.ROOT);
    if (!word.matches("[a-z]{2,32}")) return false;
    JSONObject words = read();
    if (words.length() >= 500 && !words.has(word)) return false;
    try {
      words.put(word, Math.min(1000, words.optInt(word, 0) + 1));
    } catch (Exception ignored) {
      return false;
    }
    store.edit().putString("counts", words.toString()).apply();
    return true;
  }

  int size() {
    return read().length();
  }

  void clear() {
    store.edit().clear().apply();
  }
}
