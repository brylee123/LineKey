// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded, optional local adaptation. Stores tokens and adjacent pairs, never editor text. */
public final class Personalization {
    public static final int MAX_WORDS = 500;
    public static final int MAX_PAIRS = 2000;
    public static final int MAX_FORGOTTEN = 500;

    private final Map<String, Integer> words = new LinkedHashMap<>();
    private final Map<String, Integer> pairs = new LinkedHashMap<>();
    private final Set<String> forgotten = new LinkedHashSet<>();

    public Map<String, Integer> words() {
        return Collections.unmodifiableMap(words);
    }

    public Map<String, Integer> pairs() {
        return Collections.unmodifiableMap(pairs);
    }

    public Set<String> forgotten() {
        return Collections.unmodifiableSet(forgotten);
    }

    public void restore(
            final Map<String, Integer> counts,
            final Map<String, Integer> contexts,
            final Collection<String> hidden) {
        words.clear();
        pairs.clear();
        forgotten.clear();
        for (final String word : hidden) {
            if (valid(word) && forgotten.size() < MAX_FORGOTTEN) {
                forgotten.add(key(word));
            }
        }
        for (final Map.Entry<String, Integer> e : counts.entrySet()) {
            if (valid(e.getKey())
                    && !isForgotten(e.getKey())
                    && words.size() < MAX_WORDS
                    && e.getValue() > 0) {
                words.put(key(e.getKey()), Math.min(1000, e.getValue()));
            }
        }
        for (final Map.Entry<String, Integer> e : contexts.entrySet()) {
            final String[] pair = e.getKey().split("\\t", -1);
            if (pair.length == 2
                    && contextValid(pair[0])
                    && valid(pair[1])
                    && !isForgotten(pair[0])
                    && !isForgotten(pair[1])
                    && pairs.size() < MAX_PAIRS
                    && e.getValue() > 0) {
                pairs.put(key(pair[0]) + "\t" + key(pair[1]), Math.min(1000, e.getValue()));
            }
        }
    }

    private static String key(final String word) {
        return WordSpelling.canonical(word);
    }

    private static boolean valid(final String word) {
        return word != null && WordSpelling.valid(word);
    }

    private static boolean contextValid(final String word) {
        return "<s>".equals(word) || valid(word);
    }

    public boolean isForgotten(final String word) {
        return forgotten.contains(key(word));
    }

    private static void increment(
            final Map<String, Integer> map, final String key, final int amount, final int limit) {
        if (!map.containsKey(key) && map.size() >= limit) {
            // Replace the least-used entry; insertion order makes ties deterministic.
            final String weakest =
                    Collections.min(map.entrySet(), Map.Entry.comparingByValue()).getKey();
            map.remove(weakest);
        }
        map.put(key, Math.min(1000, map.getOrDefault(key, 0) + amount));
    }

    public boolean learn(final String text, final String previous, final int weight) {
        if (text == null || weight < 1) {
            return false;
        }
        final String[] tokens = key(text).split(" +");
        if (tokens.length > 8 || Arrays.stream(tokens).anyMatch((final String w) -> !valid(w))) {
            return false;
        }
        String context = contextValid(previous) ? key(previous) : "<s>";
        boolean changed = false;
        for (final String word : tokens) {
            if (isForgotten(word)) {
                context = word;
                continue;
            }
            increment(words, word, Math.min(3, weight), MAX_WORDS);
            if (!isForgotten(context)) {
                increment(pairs, context + "\t" + word, Math.min(3, weight), MAX_PAIRS);
            }
            context = word;
            changed = true;
        }
        return changed;
    }

    /** Explicit manual teaching restores a previously forgotten word. */
    public boolean teach(final String word) {
        if (!valid(word)) {
            return false;
        }
        forgotten.remove(key(word));
        increment(words, key(word), 1, MAX_WORDS);
        return true;
    }

    public void forget(final String word) {
        if (!valid(word)) {
            return;
        }
        final String token = key(word);
        words.remove(token);
        pairs.keySet()
                .removeIf(
                        (final String p) -> p.startsWith(token + "\t") || p.endsWith("\t" + token));
        if (!forgotten.contains(token) && forgotten.size() >= MAX_FORGOTTEN) {
            forgotten.remove(forgotten.iterator().next());
        }
        forgotten.add(token);
    }

    public double boost(final String previous, final String word) {
        return Math.min(1.5, .3 * Math.log1p(words.getOrDefault(key(word), 0)))
                + Math.min(
                        3,
                        .9 * Math.log1p(pairs.getOrDefault(key(previous) + "\t" + key(word), 0)));
    }

    public List<Decoder.Candidate> rescore(
            final List<Decoder.Candidate> source, final String previous, final boolean adapt) {
        final List<Decoder.Candidate> result = new ArrayList<>();
        for (final Decoder.Candidate candidate : source) {
            String context = previous;
            double bonus = 0;
            boolean hidden = false;
            final String[] tokens = candidate.word().split(" ");
            for (final String token : tokens) {
                hidden |= isForgotten(token);
                if (adapt) {
                    bonus += boost(context, token);
                }
                context = token;
            }
            if (!hidden) {
                result.add(
                        new Decoder.Candidate(
                                candidate.word(),
                                candidate.cost(),
                                candidate.score() + bonus / tokens.length));
            }
        }
        result.sort(
                Comparator.comparingDouble(Decoder.Candidate::score)
                        .reversed()
                        .thenComparing(Decoder.Candidate::word));
        return result;
    }

    public List<LanguageModel.Prediction> next(
            final LanguageModel model,
            final String previous,
            final int limit,
            final boolean adapt) {
        final Map<String, LanguageModel.Prediction> choices = new LinkedHashMap<>();
        for (final LanguageModel.Prediction p : model.next(previous, 64)) {
            if (!isForgotten(p.word())) {
                choices.put(
                        p.word(),
                        new LanguageModel.Prediction(
                                p.word(),
                                p.logProbability() + (adapt ? boost(previous, p.word()) : 0)));
            }
        }
        if (adapt) {
            for (final Map.Entry<String, Integer> pair : pairs.entrySet()) {
                final String prefix = key(previous) + "\t";
                if (!pair.getKey().startsWith(prefix)) {
                    continue;
                }
                final String word = pair.getKey().substring(prefix.length());
                if (!isForgotten(word)) {
                    choices.put(
                            word,
                            new LanguageModel.Prediction(
                                    word,
                                    Math.max(model.score(previous, word), -3)
                                            + boost(previous, word)));
                }
            }
        }
        final List<LanguageModel.Prediction> result = new ArrayList<>(choices.values());
        result.sort(
                Comparator.comparingDouble(LanguageModel.Prediction::logProbability)
                        .reversed()
                        .thenComparing(LanguageModel.Prediction::word));
        return result.subList(0, Math.min(Math.max(0, limit), result.size()));
    }
}
