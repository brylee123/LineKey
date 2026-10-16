// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Widths in dp, physical finger noise, independent validation and held-out word sets. */
public final class SmallScreenBenchmark {
    public static void main(final String[] args) throws Exception {
        final Decoder decoder = new Decoder();
        final LetterLayout layout = new LetterLayout(0);
        final List<String> words = new ArrayList<>();
        try (final InputStream input =
                Decoder.class.getResourceAsStream("/english-frequency.txt")) {
            for (final String line :
                    new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\n")) {
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
        System.out.println("split,width_dp,noise_dp,algorithm,words,top1,top3,mean_ms");
        for (int split = 0; split < 2; split++) {
            final List<String> sample = words.subList(split == 0 ? 0 : 80, split == 0 ? 80 : 240);
            for (final double width : new double[] {360, 240, 180}) {
                for (final double noise : new double[] {12, 18}) {
                    for (int method = 0; method < 2; method++) {
                        final Random random = new Random(51392 + split);
                        int top1 = 0;
                        int top3 = 0;

                        long elapsed = 0;
                        for (final String word : sample) {
                            final List<Double> taps = new ArrayList<>();
                            final double bias = random.nextGaussian() * 4 / width;
                            for (final char letter : word.toCharArray()) {
                                final double x =
                                        Math.max(
                                                0,
                                                Math.min(
                                                        .99999,
                                                        layout.center(letter)
                                                                + bias
                                                                + random.nextGaussian()
                                                                        * noise
                                                                        / width));
                                if (random.nextDouble() < .04) {
                                    continue;
                                }
                                taps.add(x);
                                if (random.nextDouble() < .04) {
                                    taps.add(x);
                                }
                            }
                            if (taps.size() > 1 && random.nextDouble() < .15) {
                                final int at = random.nextInt(taps.size() - 1);
                                Collections.swap(taps, at, at + 1);
                            }
                            final long start = System.nanoTime();
                            final List<Decoder.Candidate> predictions =
                                    method == 0
                                            ? decoder.predict(taps, layout, 1)
                                            : decoder.forgiving(taps, layout, width, 3);
                            elapsed += System.nanoTime() - start;
                            if (!predictions.isEmpty() && predictions.get(0).word().equals(word)) {
                                top1++;
                            }
                            if (predictions.stream()
                                    .anyMatch(
                                            (final Decoder.Candidate p) -> p.word().equals(word))) {
                                top3++;
                            }
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
}
