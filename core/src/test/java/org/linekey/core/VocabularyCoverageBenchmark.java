// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class VocabularyCoverageBenchmark {
  public static void main(String[] args) throws Exception {
    var d = new Decoder();
    var l = new LetterLayout(0);
    var all = new ArrayList<String>();
    for (String line :
        new String(
                Decoder.class.getResourceAsStream("/english-frequency.txt").readAllBytes(),
                StandardCharsets.UTF_8)
            .split("\n")) all.add(line.split(" ")[0]);
    var tail = new ArrayList<String>(all.subList(10000, 170000));
    Collections.shuffle(tail, new Random(81234));
    int one = 0, thirtytwo = 0;
    for (String w : tail.subList(0, 64))
      for (double width : new double[] {240, 180}) {
        var taps = new ArrayList<Double>();
        for (char c : w.toCharArray()) taps.add(l.center(c));
        var p = d.forgiving(taps, l, width, 32);
        if (!p.isEmpty() && p.get(0).word().equals(w)) one++;
        if (p.stream().anyMatch(c -> c.word().equals(w))) thirtytwo++;
      }
    System.out.println("tail_cases,128\ntop1," + one + "\ntop32," + thirtytwo);
  }
}
