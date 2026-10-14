// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.*;

/** Diagnostic collocations, not held-out training data or a production accuracy certificate. */
public final class LanguageModelBenchmark {
  public static void main(String[] args) {
    long start = System.nanoTime();
    LanguageModel model = LanguageModel.get();
    System.out.printf(Locale.ROOT, "load_ms,%.3f%n", (System.nanoTime() - start) / 1e6);
    String[] examples = {
      "according to",
      "instead of",
      "such as",
      "because of",
      "more than",
      "less than",
      "united states",
      "new york",
      "prime minister",
      "last year",
      "next week",
      "this is",
      "there are",
      "thank you",
      "good morning",
      "happy birthday",
      "see you",
      "how are",
      "kind of",
      "as well",
      "a lot",
      "in the",
      "on the",
      "at the",
      "with the",
      "for the",
      "i am",
      "you are",
      "we are",
      "it is"
    };
    int top1 = 0, top3 = 0, baseline = 0;
    System.out.println("context,expected,first,top3");
    for (String phrase : examples) {
      String[] words = phrase.split(" ");
      var predictions = model.next(words[0], 3);
      if (predictions.get(0).word().equals(words[1])) top1++;
      boolean found = predictions.stream().anyMatch(candidate -> candidate.word().equals(words[1]));
      if (found) top3++;
      if (model.next("qzxunknowncontext", 3).stream()
          .anyMatch(candidate -> candidate.word().equals(words[1]))) baseline++;
      System.out.println(words[0] + "," + words[1] + "," + predictions.get(0).word() + "," + found);
    }
    start = System.nanoTime();
    for (int index = 0; index < 10000; index++)
      model.next(examples[index % examples.length].split(" ")[0], 3);
    System.out.printf(
        Locale.ROOT,
        "queries,30,top1,%d,top3,%d,unknown_context_top3,%d,mean_lookup_ms,%.6f%n",
        top1,
        top3,
        baseline,
        (System.nanoTime() - start) / 1e6 / 10000);
  }
}
