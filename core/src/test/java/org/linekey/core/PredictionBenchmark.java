// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Reproducible synthetic touch benchmark. Validation and held-out words never overlap. */
public final class PredictionBenchmark {
    public static void main(final String[] args) throws Exception {
        final Decoder decoder = new Decoder();
        final LetterLayout layout = new LetterLayout(0);
        final List<String> words = new ArrayList<>();
        try (final BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                Decoder.class.getResourceAsStream("/english-frequency.txt"),
                                StandardCharsets.UTF_8))) {
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                final String word = line.split(" ")[0];
                if (word.length() >= 3 && word.length() <= 10) {
                    words.add(word);
                }
                if (words.size() == 3000) {
                    break;
                }
            }
        }
        Collections.shuffle(words, new Random(7341));
        System.out.println("split,noise,algorithm,words,top1,top3,mean_ms");
        for (int split = 0; split < 2; split++) {
            final List<String> sample = words.subList(split == 0 ? 0 : 80, split == 0 ? 80 : 240);
            for (final double noise : new double[] {0, .018, .035}) {
                for (int algorithm = 0; algorithm < 3; algorithm++) {
                    final Random random = new Random(90210 + split);
                    int top1 = 0;
                    int top3 = 0;

                    long elapsed = 0;
                    for (final String word : sample) {
                        final List<Double> taps = new ArrayList<>();
                        for (final char letter : word.toCharArray()) {
                            final double x =
                                    Math.max(
                                            0,
                                            Math.min(
                                                    .99999,
                                                    layout.center(letter)
                                                            + random.nextGaussian() * noise));
                            if (noise > .02 && random.nextDouble() < .02) {
                                continue;
                            }
                            taps.add(x);
                            if (noise > .02 && random.nextDouble() < .02) {
                                taps.add(x);
                            }
                        }
                        final long start = System.nanoTime();
                        final List<Decoder.Candidate> predictions =
                                decoder.predict(taps, layout, algorithm);
                        elapsed += System.nanoTime() - start;
                        if (!predictions.isEmpty() && predictions.get(0).word().equals(word)) {
                            top1++;
                        }
                        if (predictions.stream()
                                .anyMatch((final Decoder.Candidate c) -> c.word().equals(word))) {
                            top3++;
                        }
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
