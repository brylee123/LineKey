// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;
import org.linekey.core.*;

/** Optional on-device token/pair counts and explicit forgotten words. No editor text. */
final class WordStore {
  private final SharedPreferences store;

  WordStore(Context context) {
    store = context.getSharedPreferences("linekey_words", Context.MODE_PRIVATE);
  }

  private Map<String, Integer> counts(String name) {
    Map<String, Integer> result = new LinkedHashMap<>();
    try {
      JSONObject json = new JSONObject(store.getString(name, "{}"));
      for (var it = json.keys(); it.hasNext(); ) {
        String key = it.next();
        result.put(key, json.optInt(key, 0));
      }
    } catch (JSONException ignored) {
      /* Recover a corrupt optional data section independently. */
    }
    return result;
  }

  Personalization read() {
    Personalization data = new Personalization();
    data.restore(counts("counts"), counts("pairs"), store.getStringSet("forgotten", Set.of()));
    return data;
  }

  void save(Personalization data) {
    store
        .edit()
        .putString("counts", new JSONObject(data.words()).toString())
        .putString("pairs", new JSONObject(data.pairs()).toString())
        .putStringSet("forgotten", new HashSet<>(data.forgotten()))
        .apply();
  }

  void load(Decoder decoder) {
    for (var e : read().words().entrySet()) decoder.addWord(e.getKey(), e.getValue());
  }

  boolean learn(String word) {
    Personalization data = read();
    if (!data.teach(word)) return false;
    save(data);
    return true;
  }

  boolean learn(String word, String previous, int weight) {
    Personalization data = read();
    if (!data.learn(word, previous, weight)) return false;
    save(data);
    return true;
  }

  void forget(String word) {
    Personalization data = read();
    data.forget(word);
    save(data);
  }

  int size() {
    return read().words().size();
  }

  void clear() {
    store.edit().clear().apply();
  }
}
