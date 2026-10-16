// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class VocabularyCoverageBenchmark {
    public static void main(final String[] args) throws Exception {
        final Decoder d = new Decoder();
        final LetterLayout l = new LetterLayout(0);
        final ArrayList<String> all = new ArrayList<String>();
        for (final String line :
                new String(
                                Decoder.class
                                        .getResourceAsStream("/english-frequency.txt")
                                        .readAllBytes(),
                                StandardCharsets.UTF_8)
                        .split("\n")) {
            all.add(line.split(" ")[0]);
        }
        final ArrayList<String> tail = new ArrayList<String>(all.subList(10000, 170000));
        Collections.shuffle(tail, new Random(81234));
        int one = 0;
        int thirtytwo = 0;

        for (final String w : tail.subList(0, 64)) {
            for (final double width : new double[] {240, 180}) {
                final ArrayList<Double> taps = new ArrayList<Double>();
                for (final char c : w.toCharArray()) {
                    taps.add(l.center(c));
                }
                final List<Decoder.Candidate> p = d.forgiving(taps, l, width, 32);
                if (!p.isEmpty() && p.get(0).word().equals(w)) {
                    one++;
                }
                if (p.stream().anyMatch((final Decoder.Candidate c) -> c.word().equals(w))) {
                    thirtytwo++;
                }
            }
        }
        System.out.println("tail_cases,128\ntop1," + one + "\ntop32," + thirtytwo);
    }
}
