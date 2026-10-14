// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Widths in dp, physical finger noise, independent validation and held-out word sets. */
public final class SmallScreenBenchmark {
  public static void main(String[] args) throws Exception {
    Decoder decoder = new Decoder();
    LetterLayout layout = new LetterLayout(0);
    List<String> words = new ArrayList<>();
    try (var input = Decoder.class.getResourceAsStream("/english-frequency.txt")) {
      for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\n")) {
        String word = line.split(" ")[0];
        if (word.length() >= 3 && word.length() <= 10) words.add(word);
        if (words.size() == 3000) break;
      }
    }
    Collections.shuffle(words, new Random(7341));
    System.out.println("split,width_dp,noise_dp,algorithm,words,top1,top3,mean_ms");
    for (int split = 0; split < 2; split++) {
      List<String> sample = words.subList(split == 0 ? 0 : 80, split == 0 ? 80 : 240);
      for (double width : new double[] {360, 240, 180})
        for (double noise : new double[] {12, 18}) {
          for (int method = 0; method < 2; method++) {
            Random random = new Random(51392 + split);
            int top1 = 0, top3 = 0;
            long elapsed = 0;
            for (String word : sample) {
              List<Double> taps = new ArrayList<>();
              double bias = random.nextGaussian() * 4 / width;
              for (char letter : word.toCharArray()) {
                double x =
                    Math.max(
                        0,
                        Math.min(
                            .99999,
                            layout.center(letter) + bias + random.nextGaussian() * noise / width));
                if (random.nextDouble() < .04) continue;
                taps.add(x);
                if (random.nextDouble() < .04) taps.add(x);
              }
              if (taps.size() > 1 && random.nextDouble() < .15) {
                int at = random.nextInt(taps.size() - 1);
                Collections.swap(taps, at, at + 1);
              }
              long start = System.nanoTime();
              var predictions =
                  method == 0
                      ? decoder.predict(taps, layout, 1)
                      : decoder.forgiving(taps, layout, width, 3);
              elapsed += System.nanoTime() - start;
              if (!predictions.isEmpty() && predictions.get(0).word().equals(word)) top1++;
              if (predictions.stream().anyMatch(p -> p.word().equals(word))) top3++;
            }
            System.out.printf(
                Locale.ROOT,
                "%s,%.0f,%.0f,%s,%d,%d,%d,%.3f%n",
                split == 0 ? "validation" : "heldout",
                width,
                noise,
                method == 0 ? "previous" : "adaptive",
                sample.size(),
                top1,
                top3,
                elapsed / 1e6 / sample.size());
          }
        }
    }
  }
}
