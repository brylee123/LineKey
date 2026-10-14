// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Spatial sequence decoder with independently licensed word frequencies. */
public final class Decoder {
  public record Candidate(String word, double cost, double score) {}

  private final Map<String, Integer> vocabulary = new LinkedHashMap<>();

  public Decoder() {
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
          String[] fields = line.split(" ");
          vocabulary.put(fields[0], Integer.parseInt(fields[1]));
        }
      }
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot load vocabulary", e);
    }
    int weight = 300;
    for (String s : seed.split(" "))
      vocabulary.putIfAbsent(s.toLowerCase(Locale.ROOT), Math.max(2, weight--));
  }

  public int seedSize() {
    return vocabulary.size();
  }

  public void addWord(String word, int count) {
    if (word.matches("[a-zA-Z]{1,32}"))
      vocabulary.put(word.toLowerCase(Locale.ROOT), 100000 + Math.min(1000, count) * 10000);
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
                e.getKey(), cost, -cost + (algorithm == 2 ? .28 : .12) * Math.log(e.getValue())));
    }
    list.sort(
        Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::word));
    return list.subList(0, Math.min(3, list.size()));
  }

  /** Width-aware touch tolerance, missing/repeated taps, and adjacent transpositions. */
  public List<Candidate> forgiving(
      List<Double> taps, LetterLayout layout, double widthDp, int limit) {
    if (taps.isEmpty()) return List.of();
    double sigma = Math.max(.045, Math.min(.10, 16 / Math.max(100, widthDp)));
    List<Candidate> result = new ArrayList<>();
    double[][] work = new double[4][33];
    for (var entry : vocabulary.entrySet()) {
      String word = entry.getKey();
      if (Math.abs(word.length() - taps.size()) > 3) continue;
      double cost = forgivingCost(taps, word, layout, sigma, work);
      if (cost / taps.size() <= (taps.size() < 4 ? 1.5 : 2.5))
        result.add(new Candidate(word, cost, -cost + .12 * Math.log(entry.getValue())));
    }
    result.sort(
        Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::word));
    return result.subList(0, Math.min(limit, result.size()));
  }

  private static double forgivingCost(
      List<Double> taps, String word, LetterLayout layout, double sigma, double[][] work) {
    double[] current = work[0], previous = work[1], twoBack = work[2];
    double[] centers = work[3];
    for (int j = 0; j <= word.length(); j++) {
      previous[j] = j * 2.7;
      if (j > 0) centers[j] = layout.center(word.charAt(j - 1));
    }
    for (int i = 1; i <= taps.size(); i++) {
      current[0] = i * 2.7;
      for (int j = 1; j <= word.length(); j++) {
        double distance = (taps.get(i - 1) - centers[j]) / sigma;
        current[j] =
            Math.min(
                previous[j - 1] + Math.min(5, distance * distance),
                Math.min(previous[j] + 2.7, current[j - 1] + 2.7));
        if (i > 1 && j > 1) {
          double first = (taps.get(i - 2) - centers[j]) / sigma;
          double second = (taps.get(i - 1) - centers[j - 1]) / sigma;
          current[j] =
              Math.min(
                  current[j],
                  twoBack[j - 2] + 1.2 + Math.min(5, first * first) + Math.min(5, second * second));
        }
      }
      double[] spare = twoBack;
      twoBack = previous;
      previous = current;
      current = spare;
    }
    return previous[word.length()];
  }

  public List<Candidate> full(String raw) {
    if (raw.isEmpty()) return java.util.Collections.emptyList();
    List<Candidate> result = new ArrayList<>();
    String lower = raw.toLowerCase(Locale.ROOT);
    for (var e : vocabulary.entrySet()) {
      int cost = editDistance(lower, e.getKey());
      if (cost <= (lower.length() < 4 ? 0 : lower.length() < 7 ? 1 : 2))
        result.add(
            new Candidate(
                e.getKey(),
                cost,
                -cost * 4
                    - Math.abs(lower.length() - e.getKey().length()) * 2
                    + .12 * Math.log(e.getValue())));
    }
    result.sort(
        Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::word));
    return result.subList(0, Math.min(3, result.size()));
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
