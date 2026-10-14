// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reproducible synthetic touch benchmark. Validation and held-out words never overlap. */
public final class PredictionBenchmark {
  public static void main(String[] args) throws Exception {
    Decoder decoder = new Decoder();
    LetterLayout layout = new LetterLayout(0);
    List<String> words = new ArrayList<>();
    try (var reader =
        new BufferedReader(
            new InputStreamReader(
                Decoder.class.getResourceAsStream("/english-frequency.txt"),
                StandardCharsets.UTF_8))) {
      for (String line; (line = reader.readLine()) != null; ) {
        String word = line.split(" ")[0];
        if (word.length() >= 3 && word.length() <= 10) words.add(word);
        if (words.size() == 3000) break;
      }
    }
    Collections.shuffle(words, new Random(7341));
    System.out.println("split,noise,algorithm,words,top1,top3,mean_ms");
    for (int split = 0; split < 2; split++) {
      List<String> sample = words.subList(split == 0 ? 0 : 80, split == 0 ? 80 : 240);
      for (double noise : new double[] {0, .018, .035}) {
        for (int algorithm = 0; algorithm < 3; algorithm++) {
          Random random = new Random(90210 + split);
          int top1 = 0, top3 = 0;
          long elapsed = 0;
          for (String word : sample) {
            List<Double> taps = new ArrayList<>();
            for (char letter : word.toCharArray()) {
              double x =
                  Math.max(
                      0, Math.min(.99999, layout.center(letter) + random.nextGaussian() * noise));
              if (noise > .02 && random.nextDouble() < .02) continue;
              taps.add(x);
              if (noise > .02 && random.nextDouble() < .02) taps.add(x);
            }
            long start = System.nanoTime();
            var predictions = decoder.predict(taps, layout, algorithm);
            elapsed += System.nanoTime() - start;
            if (!predictions.isEmpty() && predictions.get(0).word().equals(word)) top1++;
            if (predictions.stream().anyMatch(c -> c.word().equals(word))) top3++;
          }
          System.out.printf(
              Locale.ROOT,
              "%s,%.3f,%d,%d,%d,%d,%.3f%n",
              split == 0 ? "validation" : "heldout",
              noise,
              algorithm,
              sample.size(),
              top1,
              top3,
              elapsed / 1e6 / sample.size());
        }
      }
    }
  }
}
