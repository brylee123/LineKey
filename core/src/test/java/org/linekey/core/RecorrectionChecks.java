// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.ArrayList;
import java.util.List;

/** Range integrity, original ambiguity, and memory/privacy lifecycle regressions. */
final class RecorrectionChecks {
  private static int assertions;

  private static void check(boolean condition, String message) {
    assertions++;
    if (!condition) throw new AssertionError(message);
  }

  static int run() {
    assertions = 0;
    RecorrectionHistory history = new RecorrectionHistory();
    history.observe("eaten later ", 0);
    List<String> alternatives = new ArrayList<>(List.of("eaten", "watch", "water"));
    history.remember(0, "eaten", "sztech", alternatives);
    alternatives.clear();
    for (int cursor : new int[] {0, 2, 5}) {
      var target = history.target(cursor, cursor);
      check(
          target.word().equals("eaten") && target.start() == 0 && target.end() == 5,
          "Caret resolves exactly the committed word");
      check(
          target.evidence().typed().equals("sztech")
              && target.evidence().alternatives().contains("watch"),
          "Revisiting eaten restores watch from original sztech ambiguity");
    }
    check(history.target(0, 5).evidence() != null, "Whole-word selection supports recorrection");
    check(history.target(5, 0).evidence() != null, "Backward whole-word selection is supported");
    check(
        history.target(1, 4) == null && history.target(0, 11) == null,
        "Partial and multiple-word selections do not replace an unexpected range");
    check(history.target(6, 6).word().equals("later"), "Start of next word resolves next word");
    check(history.target(12, 12) == null, "After whitespace retains next-word suggestions");
    check(
        history.remember(1, "eaten", "sztech", List.of("watch")) == null,
        "Mismatched commit ranges are rejected");
    history.observe("prefix eaten later ", 0);
    check(
        history.target(9, 9).evidence().typed().equals("sztech"),
        "Insertions before a word rebase its evidence");
    history.observe("prefix eaten later today ", 0);
    check(
        history.target(9, 9).evidence().alternatives().contains("watch"),
        "Subsequent words retain earlier evidence");
    history.observe("eaten later today ", 0);
    check(history.target(1, 1).evidence() != null, "Deleting preceding text rebases evidence");
    history.observe("eaten eaten ", 0);
    history.remember(6, "eaten", "other", List.of("other"));
    check(
        history.target(1, 1).evidence().typed().equals("sztech")
            && history.target(7, 7).evidence().typed().equals("other"),
        "Identical spellings at different positions keep distinct evidence");
    history.observe("watch eaten ", 0);
    check(
        history.target(1, 1).evidence() == null,
        "An overlapping external edit invalidates original evidence");
    check(
        history.target(7, 7).evidence().typed().equals("other"),
        "An external edit does not invalidate unaffected words");
    history.remember(0, "watch", "sztech", List.of("eaten", "watch"));
    history.observe("eaten eaten ", 0);
    history.remember(0, "eaten", "sztech", List.of("eaten", "watch"));
    check(
        history.target(2, 2).evidence().alternatives().contains("watch"),
        "Repeated correction and undo can preserve the original alternatives");
    history.clear();
    history.observe("eaten dog ", 0);
    history.remember(0, "eaten", "sztech", List.of("watch", "watermelon"));
    history.remember(6, "dog", "dog", List.of("dog"));
    history.observe("watermelon dog ", 0);
    history.remember(0, "watermelon", "sztech", List.of("watch", "watermelon"));
    check(
        history.target(12, 12).evidence().word().equals("dog"),
        "Longer replacement rebases later committed words");
    history.observe("eaten dog ", 0);
    check(
        history.target(7, 7).evidence().word().equals("dog"),
        "Shorter replacement rebases later committed words");
    history.clear();
    check(history.target(1, 1) == null, "Editor-session cleanup removes text and evidence");
    for (String spelling :
        List.of("don't", "well-known", "rock'n'roll", "u.s.a.", "and/or", "r&d", "c++")) {
      history.observe("one " + spelling + " next", 0);
      var target = history.target(5, 5);
      check(
          target.word().equals(spelling)
              && target.start() == 4
              && target.end() == 4 + spelling.length(),
          "Word boundaries preserve internal punctuation: " + spelling);
    }
    history.observe("'eaten', later.", 0);
    check(
        history.target(2, 2).word().equals("eaten") && history.target(2, 2).start() == 1,
        "Surrounding quotation marks and punctuation are outside the replacement");
    check(
        history.target(12, 12).word().equals("later") && history.target(12, 12).end() == 14,
        "Sentence period is outside the word");
    history.observe("eaten", 100);
    history.remember(100, "eaten", "sztech", List.of("watch"));
    check(
        history.target(102, 102).evidence() != null, "Partial editor windows use absolute ranges");
    history.observe("eaten", 101);
    check(
        history.target(102, 102).evidence() == null,
        "Moving extraction window discards uncertain evidence");
    history.clear();
    String text = "";
    for (int word = 0; word < 65; word++) {
      text += "word ";
      history.observe(text, 0);
      history.remember(word * 5, "word", "raw", List.of("alternative"));
    }
    check(
        history.target(1, 1).evidence() == null && history.target(321, 321).evidence() != null,
        "Only the latest 64 committed words retain original evidence");
    history.clear();
    history.observe("eaten eaten ", 0);
    history.remember(0, "eaten", "first", List.of("watch"));
    history.remember(6, "eaten", "second", List.of("other"));
    history.observe("eaten ", 0);
    check(
        history.target(2, 2).evidence() == null,
        "Ambiguous duplicate deletion discards uncertain evidence instead of attaching the wrong"
            + " original input");
    history.observe("a".repeat(RecorrectionHistory.MAX_TEXT + 1), 0);
    check(history.target(1, 1) == null, "Oversized editor snapshots clear retained evidence");
    return assertions;
  }
}
