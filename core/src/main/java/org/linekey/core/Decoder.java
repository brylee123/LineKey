// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

/** Spatial sequence decoder with independently licensed word frequencies. */
public final class Decoder {
  public record Candidate(String word, double cost, double score) {}

  private record Word(String text, int frequency, double logFrequency, double compactPrior) {
    Word(String text, int frequency) {
      this(
          text,
          frequency,
          Math.log(frequency),
          .16 * Math.log(frequency) + commonWordBonus(frequency));
    }
  }

  /** Saturating frequency prior: common words get a preference without excluding rare entries. */
  private static double commonWordBonus(int frequency) {
    if (frequency <= 500) return 0;
    if (frequency >= 5000) return 3;
    return 3 * Math.log(frequency / 500.0) / Math.log(10);
  }

  private record Vocabulary(Map<String, Word> words, List<List<Word>> byLength) {}

  private static final class VocabularyHolder {
    static final Vocabulary DATA = loadVocabulary();
  }

  private Map<String, Word> vocabulary;
  private List<List<Word>> byLength;
  private boolean personalized;

  public Decoder() {
    // Share immutable base data across editor restarts; copy only when local learning adds a word.
    vocabulary = VocabularyHolder.DATA.words();
    byLength = VocabularyHolder.DATA.byLength();
  }

  private static Vocabulary loadVocabulary() {
    Map<String, Word> vocabulary = new LinkedHashMap<>();
    // Hand-authored starter vocabulary. These weights are illustrative, not corpus frequencies.
    String seed =
        "the of and to a in is it you that he was for on are with as I his they be at one have this"
            + " from or had by not word but what all were we when your can said there use an each"
            + " which she do how their if will up other about out many then them these so some her"
            + " would make like him into time has look two more write go see number no way could"
            + " people my than first water been call who its now find long down day did get come"
            + " made may part hello world keyboard small compact open source android line key type"
            + " typing test thanks thank good morning night yes okay great easy screen space full"
            + " precise gesture prediction emoji happy love home work today tomorrow meet please"
            + " message phone text friend family new old need want know think say help here where"
            + " why again just only very much really still well also back next right left delete"
            + " learn settings language data privacy local word words coffee cat dog quick brown"
            + " fox jumps over lazy sentence letter letters words punctuation stop start build"
            + " project code free beautiful cool awesome nice better best";
    try (var input = Decoder.class.getResourceAsStream("/english-frequency.txt")) {
      if (input != null) {
        var reader =
            new java.io.BufferedReader(
                new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        for (String line; (line = reader.readLine()) != null; ) {
          int separator = line.lastIndexOf(' ');
          String word = line.substring(0, separator);
          int frequency = Integer.parseInt(line.substring(separator + 1));
          vocabulary.put(word, new Word(word, frequency));
        }
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot load vocabulary", e);
    }
    int weight = 300;
    for (String s : seed.split(" "))
      vocabulary.putIfAbsent(
          s.toLowerCase(Locale.ROOT), new Word(s.toLowerCase(Locale.ROOT), Math.max(2, weight--)));

    List<List<Word>> lengths = new ArrayList<>();
    for (int length = 0; length <= 32; length++) lengths.add(new ArrayList<>());
    for (Word word : vocabulary.values()) lengths.get(word.text().length()).add(word);
    for (int length = 0; length < lengths.size(); length++)
      lengths.set(length, Collections.unmodifiableList(lengths.get(length)));
    return new Vocabulary(
        Collections.unmodifiableMap(vocabulary), Collections.unmodifiableList(lengths));
  }

  public int seedSize() {
    return vocabulary.size();
  }

  public void addWord(String word, int count) {
    if (!word.matches("[a-zA-Z]{1,32}")) return;
    if (!personalized) {
      vocabulary = new LinkedHashMap<>(vocabulary);
      List<List<Word>> mutable = new ArrayList<>();
      for (List<Word> length : byLength) mutable.add(new ArrayList<>(length));
      byLength = mutable;
      personalized = true;
    }
    String lower = word.toLowerCase(Locale.ROOT);
    Word learned = new Word(lower, 100000 + Math.min(1000, Math.max(0, count)) * 10000);
    Word previous = vocabulary.put(lower, learned);
    List<Word> length = byLength.get(lower.length());
    if (previous != null) length.remove(previous);
    length.add(learned);
  }

  public List<Candidate> compact(List<Double> taps, LetterLayout layout) {
    return predict(taps, layout, 1);
  }

  /** Same vocabulary, three independently implemented ranking approaches for benchmarking. */
  public List<Candidate> predict(List<Double> taps, LetterLayout layout, int algorithm) {
    if (taps.isEmpty()) return java.util.Collections.emptyList();
    List<Candidate> list = new ArrayList<>();
    for (var e : vocabulary.entrySet()) {
      if (Math.abs(e.getKey().length() - taps.size()) > 2) continue;
      double cost =
          algorithm == 0
              ? editDistance(literal(taps, layout), e.getKey()) * 3
              : algorithm == 1
                  ? spatialCost(taps, e.getKey(), layout)
                  : robustCost(taps, e.getKey(), layout);
      if (cost / Math.max(1, taps.size()) <= 1.5)
        list.add(
            new Candidate(
                e.getKey(),
                cost,
                -cost + (algorithm == 2 ? .28 : .12) * e.getValue().logFrequency()));
    }
    list.sort(
        Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::word));
    return list.subList(0, Math.min(3, list.size()));
  }

  private static final Comparator<Candidate> BEST_FIRST =
      Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::word);

  /**
   * Retain only the requested best matches rather than allocating and sorting every possible word.
   */
  private static final class Shortlist {
    private final int limit;
    private final PriorityQueue<Candidate> words = new PriorityQueue<>(BEST_FIRST.reversed());

    Shortlist(int limit) {
      this.limit = limit;
    }

    double cutoff() {
      return words.size() < limit ? Double.NEGATIVE_INFINITY : words.peek().score();
    }

    void add(String word, double cost, double score) {
      Candidate candidate = new Candidate(word, cost, score);
      if (words.size() < limit) words.add(candidate);
      else if (BEST_FIRST.compare(candidate, words.peek()) < 0) {
        words.poll();
        words.add(candidate);
      }
    }

    List<Candidate> sorted() {
      List<Candidate> result = new ArrayList<>(words);
      result.sort(BEST_FIRST);
      return result;
    }
  }

  /** Width-aware touch tolerance, missing/repeated taps, and adjacent transpositions. */
  public List<Candidate> forgiving(
      List<Double> taps, LetterLayout layout, double widthDp, int limit) {
    if (taps.isEmpty() || limit <= 0) return List.of();
    double sigma = Math.max(.06, Math.min(.10, 16 / Math.max(100, widthDp)));
    // Every word uses the same 26 target positions: calculate each tap/letter loss just once.
    double[][] losses = new double[taps.size()][26];
    for (int i = 0; i < taps.size(); i++)
      for (int letter = 0; letter < 26; letter++) {
        double distance = (taps.get(i) - layout.center((char) ('a' + letter))) / sigma;
        losses[i][letter] = Math.min(5, distance * distance);
      }
    Shortlist result = new Shortlist(limit);
    double[][] work = new double[3][33];
    // Start with the expected length so strong candidates establish a useful pruning bound early.
    for (int difference = 0; difference <= 3; difference++) {
      scoreLength(taps.size() - difference, losses, result, work);
      if (difference > 0) scoreLength(taps.size() + difference, losses, result, work);
    }
    return result.sorted();
  }

  private void scoreLength(int length, double[][] losses, Shortlist result, double[][] work) {
    if (length < 1 || length >= byLength.size()) return;
    int taps = losses.length;
    double difference = Math.abs(length - taps);
    double qualityLimit = taps * (taps < 4 ? 1.5 : 2.5);
    for (Word word : byLength.get(length)) {
      double prior = -2.4 * difference + word.compactPrior();
      double budget = Math.min(qualityLimit, prior - result.cutoff());
      if (difference * 2.7 > budget + 1e-9) continue;
      double cost = forgivingCost(losses, word.text(), work, budget);
      if (cost <= qualityLimit && cost <= budget + 1e-9)
        result.add(word.text(), cost, -cost + prior);
    }
  }

  private static double forgivingCost(
      double[][] losses, String word, double[][] work, double budget) {
    double[] current = work[0], previous = work[1], twoBack = work[2];
    for (int j = 0; j <= word.length(); j++) previous[j] = j * 2.7;
    double previousMinimum = 0;
    for (int i = 1; i <= losses.length; i++) {
      current[0] = i * 2.7;
      double minimum = current[0];
      for (int j = 1; j <= word.length(); j++) {
        int letter = word.charAt(j - 1) - 'a';
        current[j] =
            Math.min(
                previous[j - 1] + losses[i - 1][letter],
                Math.min(previous[j] + 2.7, current[j - 1] + 2.7));
        if (i > 1 && j > 1)
          current[j] =
              Math.min(
                  current[j],
                  twoBack[j - 2]
                      + 1.2
                      + losses[i - 2][letter]
                      + losses[i - 1][word.charAt(j - 2) - 'a']);
        minimum = Math.min(minimum, current[j]);
      }
      // The next row can still recover through a transposition from the previous row.
      if (Math.min(minimum, previousMinimum + 1.2) > budget + 1e-9) return Double.POSITIVE_INFINITY;
      previousMinimum = minimum;
      double[] spare = twoBack;
      twoBack = previous;
      previous = current;
      current = spare;
    }
    return previous[word.length()];
  }

  public List<Candidate> full(String raw) {
    if (raw.isEmpty()) return List.of();
    String lower = raw.toLowerCase(Locale.ROOT);
    int maximum = lower.length() < 4 ? 0 : lower.length() < 7 ? 1 : 2;
    if (maximum == 0) {
      Word exact = vocabulary.get(lower);
      return exact == null
          ? List.of()
          : List.of(new Candidate(lower, 0, .12 * exact.logFrequency()));
    }
    Shortlist result = new Shortlist(32);
    int[][] work = new int[2][33];
    for (int length = Math.max(1, lower.length() - maximum);
        length <= Math.min(32, lower.length() + maximum);
        length++) {
      for (Word word : byLength.get(length)) {
        int cost = boundedEditDistance(lower, word.text(), maximum, work);
        if (cost <= maximum)
          result.add(
              word.text(),
              cost,
              -cost * 4 - Math.abs(lower.length() - length) * 2 + .12 * word.logFrequency());
      }
    }
    return result.sorted();
  }

  private static int boundedEditDistance(String a, String b, int limit, int[][] work) {
    int[] previous = work[0], current = work[1];
    for (int j = 0; j <= b.length(); j++) previous[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      int minimum = i;
      int start = Math.max(1, i - limit), end = Math.min(b.length(), i + limit);
      if (start > 1) current[start - 1] = limit + 1;
      if (end < b.length()) current[end + 1] = limit + 1;
      for (int j = start; j <= end; j++) {
        current[j] =
            Math.min(
                previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                Math.min(previous[j] + 1, current[j - 1] + 1));
        minimum = Math.min(minimum, current[j]);
      }
      if (minimum > limit) return limit + 1;
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }

  private static String literal(List<Double> taps, LetterLayout layout) {
    StringBuilder text = new StringBuilder();
    for (double tap : taps) text.append(layout.nearest(tap));
    return text.toString();
  }

  /** Robust alignment: Gaussian near the target, capped loss for outliers, missing/extra taps. */
  public static double robustCost(List<Double> taps, String word, LetterLayout layout) {
    int n = word.length();
    double[] previous = new double[n + 1], current = new double[n + 1];
    for (int j = 0; j <= n; j++) previous[j] = j * 3.5;
    for (int i = 1; i <= taps.size(); i++) {
      current[0] = i * 3.5;
      for (int j = 1; j <= n; j++) {
        double delta = Math.abs(taps.get(i - 1) - layout.center(word.charAt(j - 1))) / .035;
        double loss = Math.min(6, delta < 1 ? delta * delta : 2 * delta - 1);
        current[j] =
            Math.min(previous[j - 1] + loss, Math.min(previous[j] + 3.5, current[j - 1] + 3.5));
      }
      double[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[n];
  }

  public static double spatialCost(List<Double> taps, String word, LetterLayout layout) {
    double[][] d = new double[taps.size() + 1][word.length() + 1];
    for (int i = 0; i <= taps.size(); i++) d[i][0] = i * 3;
    for (int j = 0; j <= word.length(); j++) d[0][j] = j * 3;
    for (int i = 1; i <= taps.size(); i++)
      for (int j = 1; j <= word.length(); j++) {
        double delta = (taps.get(i - 1) - layout.center(word.charAt(j - 1))) / .045;
        d[i][j] =
            Math.min(
                d[i - 1][j - 1] + Math.min(5, delta * delta),
                Math.min(d[i - 1][j] + 3, d[i][j - 1] + 3));
      }
    return d[taps.size()][word.length()];
  }

  public static int editDistance(String a, String b) {
    int[] row = new int[b.length() + 1];
    for (int j = 0; j < row.length; j++) row[j] = j;
    for (int i = 1; i <= a.length(); i++) {
      int prev = row[0];
      row[0] = i;
      for (int j = 1; j < row.length; j++) {
        int old = row[j];
        row[j] =
            Math.min(
                prev + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                Math.min(row[j] + 1, row[j - 1] + 1));
        prev = old;
      }
    }
    return row[b.length()];
  }
}
