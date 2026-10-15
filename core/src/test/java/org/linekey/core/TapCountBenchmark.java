// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class TapCountBenchmark {
  public static void main(String[] args) throws Exception {
    Decoder d = new Decoder();
    LetterLayout l = new LetterLayout(0);
    List<String> words = new ArrayList<>();
    for (String line :
        new String(
                Decoder.class.getResourceAsStream("/english-frequency.txt").readAllBytes(),
                StandardCharsets.UTF_8)
            .split("\n")) {
      String w = line.split(" ")[0];
      if (w.length() >= 3 && w.length() <= 10) words.add(w);
      if (words.size() == 1000) break;
    }
    Collections.shuffle(words, new Random(42191));
    System.out.println("width,noise,cases,top1,top3,shorter_top1");
    for (double width : new double[] {360, 240, 180})
      for (double noise : new double[] {12, 18}) {
        Random r = new Random(9139);
        int one = 0, three = 0, shorter = 0;
        for (String w : words.subList(0, 160)) {
          List<Double> taps = new ArrayList<>();
          double bias = r.nextGaussian() * 4 / width;
          for (char c : w.toCharArray())
            taps.add(
                Math.max(
                    0, Math.min(.99999, l.center(c) + bias + r.nextGaussian() * noise / width)));
          var predictions = d.forgiving(taps, l, width, 32);
          if (!predictions.isEmpty()) {
            if (predictions.get(0).word().equals(w)) one++;
            if (predictions.get(0).word().length() < w.length()) shorter++;
          }
          if (predictions.stream().limit(3).anyMatch(c -> c.word().equals(w))) three++;
        }
        System.out.printf(
            Locale.ROOT, "%.0f,%.0f,160,%d,%d,%d%n", width, noise, one, three, shorter);
      }
  }
}
