// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.*;

/** Bounded, optional local adaptation. Stores tokens and adjacent pairs, never editor text. */
public final class Personalization {
  public static final int MAX_WORDS = 500, MAX_PAIRS = 2000, MAX_FORGOTTEN = 500;
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
      Map<String, Integer> counts, Map<String, Integer> contexts, Collection<String> hidden) {
    words.clear();
    pairs.clear();
    forgotten.clear();
    for (String word : hidden)
      if (valid(word) && forgotten.size() < MAX_FORGOTTEN) forgotten.add(key(word));
    for (var e : counts.entrySet())
      if (valid(e.getKey())
          && !isForgotten(e.getKey())
          && words.size() < MAX_WORDS
          && e.getValue() > 0) words.put(key(e.getKey()), Math.min(1000, e.getValue()));
    for (var e : contexts.entrySet()) {
      String[] pair = e.getKey().split("\\t", -1);
      if (pair.length == 2
          && contextValid(pair[0])
          && valid(pair[1])
          && !isForgotten(pair[0])
          && !isForgotten(pair[1])
          && pairs.size() < MAX_PAIRS
          && e.getValue() > 0)
        pairs.put(key(pair[0]) + "\t" + key(pair[1]), Math.min(1000, e.getValue()));
    }
  }

  private static String key(String word) {
    return WordSpelling.canonical(word);
  }

  private static boolean valid(String word) {
    return word != null && WordSpelling.valid(word);
  }

  private static boolean contextValid(String word) {
    return "<s>".equals(word) || valid(word);
  }

  public boolean isForgotten(String word) {
    return forgotten.contains(key(word));
  }

  private static void increment(Map<String, Integer> map, String key, int amount, int limit) {
    if (!map.containsKey(key) && map.size() >= limit) {
      // Replace the least-used entry; insertion order makes ties deterministic.
      String weakest = Collections.min(map.entrySet(), Map.Entry.comparingByValue()).getKey();
      map.remove(weakest);
    }
    map.put(key, Math.min(1000, map.getOrDefault(key, 0) + amount));
  }

  public boolean learn(String text, String previous, int weight) {
    if (text == null || weight < 1) return false;
    String[] tokens = key(text).split(" +");
    if (tokens.length > 8 || Arrays.stream(tokens).anyMatch(w -> !valid(w))) return false;
    previous = contextValid(previous) ? key(previous) : "<s>";
    boolean changed = false;
    for (String word : tokens) {
      if (isForgotten(word)) {
        previous = word;
        continue;
      }
      increment(words, word, Math.min(3, weight), MAX_WORDS);
      if (!isForgotten(previous))
        increment(pairs, previous + "\t" + word, Math.min(3, weight), MAX_PAIRS);
      previous = word;
      changed = true;
    }
    return changed;
  }

  /** Explicit manual teaching restores a previously forgotten word. */
  public boolean teach(String word) {
    if (!valid(word)) return false;
    forgotten.remove(key(word));
    increment(words, key(word), 1, MAX_WORDS);
    return true;
  }

  public void forget(String word) {
    if (!valid(word)) return;
    String token = key(word);
    words.remove(token);
    pairs.keySet().removeIf(p -> p.startsWith(token + "\t") || p.endsWith("\t" + token));
    if (!forgotten.contains(token) && forgotten.size() >= MAX_FORGOTTEN)
      forgotten.remove(forgotten.iterator().next());
    forgotten.add(token);
  }

  public double boost(String previous, String word) {
    return Math.min(1.5, .3 * Math.log1p(words.getOrDefault(key(word), 0)))
        + Math.min(3, .9 * Math.log1p(pairs.getOrDefault(key(previous) + "\t" + key(word), 0)));
  }

  public List<Decoder.Candidate> rescore(
      List<Decoder.Candidate> source, String previous, boolean adapt) {
    List<Decoder.Candidate> result = new ArrayList<>();
    for (var candidate : source) {
      String context = previous;
      double bonus = 0;
      boolean hidden = false;
      String[] tokens = candidate.word().split(" ");
      for (String token : tokens) {
        hidden |= isForgotten(token);
        if (adapt) bonus += boost(context, token);
        context = token;
      }
      if (!hidden)
        result.add(
            new Decoder.Candidate(
                candidate.word(), candidate.cost(), candidate.score() + bonus / tokens.length));
    }
    result.sort(
        Comparator.comparingDouble(Decoder.Candidate::score)
            .reversed()
            .thenComparing(Decoder.Candidate::word));
    return result;
  }

  public List<LanguageModel.Prediction> next(
      LanguageModel model, String previous, int limit, boolean adapt) {
    Map<String, LanguageModel.Prediction> choices = new LinkedHashMap<>();
    for (var p : model.next(previous, 64))
      if (!isForgotten(p.word()))
        choices.put(
            p.word(),
            new LanguageModel.Prediction(
                p.word(), p.logProbability() + (adapt ? boost(previous, p.word()) : 0)));
    if (adapt)
      for (var pair : pairs.entrySet()) {
        String prefix = key(previous) + "\t";
        if (!pair.getKey().startsWith(prefix)) continue;
        String word = pair.getKey().substring(prefix.length());
        if (!isForgotten(word))
          choices.put(
              word,
              new LanguageModel.Prediction(
                  word, Math.max(model.score(previous, word), -3) + boost(previous, word)));
      }
    List<LanguageModel.Prediction> result = new ArrayList<>(choices.values());
    result.sort(
        Comparator.comparingDouble(LanguageModel.Prediction::logProbability)
            .reversed()
            .thenComparing(LanguageModel.Prediction::word));
    return result.subList(0, Math.min(Math.max(0, limit), result.size()));
  }
}
