// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.linekey.core.Decoder;
import org.linekey.core.Personalization;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Optional on-device token/pair counts and explicit forgotten words. No editor text. */
final class WordStore {
    private final SharedPreferences store;

    WordStore(final Context context) {
        store = context.getSharedPreferences("linekey_words", Context.MODE_PRIVATE);
    }

    private Map<String, Integer> counts(final String name) {
        final Map<String, Integer> result = new LinkedHashMap<>();
        try {
            final JSONObject json = new JSONObject(store.getString(name, "{}"));
            for (final Iterator<String> it = json.keys(); it.hasNext(); ) {
                final String key = it.next();
                result.put(key, json.optInt(key, 0));
            }
        } catch (final JSONException ignored) {
            /* Recover a corrupt optional data section independently. */
        }
        return result;
    }

    Personalization read() {
        final Personalization data = new Personalization();
        data.restore(counts("counts"), counts("pairs"), store.getStringSet("forgotten", Set.of()));
        return data;
    }

    void save(final Personalization data) {
        store.edit()
                .putString("counts", new JSONObject(data.words()).toString())
                .putString("pairs", new JSONObject(data.pairs()).toString())
                .putStringSet("forgotten", new HashSet<>(data.forgotten()))
                .apply();
    }

    void load(final Decoder decoder) {
        for (final Map.Entry<String, Integer> e : read().words().entrySet()) {
            decoder.addWord(e.getKey(), e.getValue());
        }
    }

    boolean learn(final String word) {
        final Personalization data = read();
        if (!data.teach(word)) {
            return false;
        }
        save(data);
        return true;
    }

    boolean learn(final String word, final String previous, final int weight) {
        final Personalization data = read();
        if (!data.learn(word, previous, weight)) {
            return false;
        }
        save(data);
        return true;
    }

    void forget(final String word) {
        final Personalization data = read();
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
