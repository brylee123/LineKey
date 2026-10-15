// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.*;

/** Diagnostic desktop/emulator latency sample, not a device performance guarantee. */
public final class DictionaryBenchmark {
  public static void main(String[] args) {
    long start = System.nanoTime();
    Decoder decoder = new Decoder();
    System.out.printf(
        Locale.ROOT,
        "vocabulary,%d%nload_ms,%.3f%n",
        decoder.seedSize(),
        (System.nanoTime() - start) / 1e6);
    start = System.nanoTime();
    new Decoder();
    System.out.printf(
        Locale.ROOT, "editor_restart_load_ms,%.3f%n", (System.nanoTime() - start) / 1e6);
    LetterLayout layout = new LetterLayout(0);
    List<Double> times = new ArrayList<>();
    String[] samples = {
      "install",
      "keyboard",
      "application",
      "download",
      "settings",
      "permission",
      "dictionary",
      "developer"
    };
    for (String word : samples)
      for (int length : new int[] {2, 4, word.length()}) {
        List<Double> taps = new ArrayList<>();
        for (char c : word.substring(0, length).toCharArray()) taps.add(layout.center(c));
        start = System.nanoTime();
        decoder.forgiving(taps, layout, 240, 32);
        times.add((System.nanoTime() - start) / 1e6);
      }
    Collections.sort(times);
    System.out.printf(
        Locale.ROOT,
        "compact_samples,%d%np50_ms,%.3f%np95_ms,%.3f%nmax_ms,%.3f%n",
        times.size(),
        times.get(times.size() / 2),
        times.get((int) Math.ceil(times.size() * .95) - 1),
        times.get(times.size() - 1));
    times.clear();
    for (String word : samples) {
      start = System.nanoTime();
      decoder.full(word);
      times.add((System.nanoTime() - start) / 1e6);
    }
    Collections.sort(times);
    System.out.printf(
        Locale.ROOT,
        "full_p50_ms,%.3f%nfull_max_ms,%.3f%n",
        times.get(times.size() / 2),
        times.get(times.size() - 1));
  }
}
