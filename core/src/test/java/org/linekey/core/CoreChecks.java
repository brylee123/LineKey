// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.core;

import java.util.ArrayList;
import java.util.List;

/** Deterministic behavior checks, run by :core:verifyCore without a test-library download. */
public final class CoreChecks {
  private static int assertions;

  private static void check(boolean value, String message) {
    assertions++;
    if (!value) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    Decoder decoder = new Decoder();
    LanguageModel model = LanguageModel.get();
    check(model.pairCount() > 50000, "Corpus-trained context coverage");
    check(model.next("according", 3).get(0).word().equals("to"), "Natural next-word context");
    check(
        LanguageModel.previousWord("Hello world. ").equals("<s>"),
        "Sentence boundary resets context");
    check(LanguageModel.previousWord("Hello WORLD ").equals("world"), "Case-insensitive context");
    check(model.next("qzxunknowncontext", 3).size() == 3, "Unseen context fallback");
    check(
        model
            .rescore(
                List.of(new Decoder.Candidate("the", 0, 0), new Decoder.Candidate("to", 0, 0)),
                "according")
            .get(0)
            .word()
            .equals("to"),
        "Context ranks candidate alternatives");
    check(Accents.choices('e', false).contains("é"), "Lowercase diacritics");
    check(
        Accents.choices('e', true).contains("É") && Accents.choices('e', true).startsWith("E"),
        "Uppercase diacritics");
    check(
        Accents.choices('s', true).contains("ẞ"),
        "Sharp S selector remains a single uppercase character");
    check(EmojiSearch.size() > 3000, "Broad Unicode emoji coverage");
    check(EmojiSearch.search("coffee", 8).get(0).symbol().equals("☕"), "Emoji keyword ranking");
    check(EmojiSearch.search("cofee", 8).get(0).symbol().equals("☕"), "Emoji typo tolerance");
    check(EmojiSearch.search("red heart", 8).get(0).symbol().equals("❤️"), "Multi-word emoji name");
    check(
        EmojiSearch.search("birthday cake", 8).get(0).symbol().equals("🎂"),
        "Specific emoji phrase");
    check(EmojiSearch.search("😀", 8).get(0).symbol().equals("😀"), "Direct emoji lookup");
    check(EmojiSearch.search("zzzzzzzzzz", 8).isEmpty(), "Unknown emoji search");
    LetterLayout qwerty = new LetterLayout(0);
    check(qwerty.compact().equals("qazwsxedcrfvtgbyhnujmikolp"), "Screenshot letter order");
    check(
        qwerty.vertical('q') < qwerty.vertical('a') && qwerty.vertical('a') < qwerty.vertical('z'),
        "Letters retain staggered row positions");
    for (int index = 0; index < 4; index++) {
      LetterLayout layout = new LetterLayout(index);
      check(layout.compact().chars().distinct().count() == 26, "Each layout has every letter once");
      for (char c : layout.compact().toCharArray())
        check(layout.nearest(layout.center(c)) == c, "Letter center round trip");
      List<Double> taps = new ArrayList<>();
      for (char c : "hello".toCharArray()) taps.add(layout.center(c) + .007);
      check(
          decoder.compact(taps, layout).get(0).word().equals("hello"),
          "Noisy hello should decode in every layout");
    }
    check(decoder.compact(List.of(), new LetterLayout(0)).isEmpty(), "Empty taps");
    check(decoder.full("hellp").get(0).word().equals("hello"), "One typo corrected");
    check(decoder.full("xyzzzzzzz").isEmpty(), "Unknown words remain literal");
    check(Decoder.editDistance("cat", "cat") == 0, "Identical");
    check(Decoder.editDistance("cat", "cart") == 1, "Insertion");
    check(Decoder.editDistance("kitten", "sitting") == 3, "Edit distance regression");
    decoder.addWord("quokka", 20);
    check(
        decoder.full("quokka").get(0).word().equals("quokka"), "Personal vocabulary participates");
    check(FieldPolicy.from(0x81, 0).privateField(), "Text password");
    check(FieldPolicy.from(0x91, 0).privateField(), "Visible password");
    check(FieldPolicy.from(0xe1, 0).privateField(), "Web password");
    check(FieldPolicy.from(0x12, 0).privateField(), "Numeric password");
    check(FieldPolicy.from(1, 0x1000000).privateField(), "No personalized learning");
    check(FieldPolicy.from(0x21, 0).literal(), "Email literal");
    check(FieldPolicy.from(0xd1, 0).literal(), "Web email literal");
    check(FieldPolicy.from(0x11, 0).literal(), "URL literal");
    check(FieldPolicy.from(2, 0).numeric(), "Number pad");
    check(FieldPolicy.from(3, 0).phone(), "Phone pad");
    check(FieldPolicy.from(0, 0).rawKeys(), "TYPE_NULL key events");
    check(!FieldPolicy.from(1, 0).privateField(), "Ordinary text may learn when enabled");
    check(FieldPolicy.from(1, 0).voiceAllowed(), "Ordinary text offers voice");
    for (int type : new int[] {0x21, 0xd1, 0x11, 0x81, 0x12, 2, 3, 0})
      check(
          !FieldPolicy.from(type, 0).voiceAllowed(), "Specialized field suppresses voice: " + type);
    check(!FieldPolicy.from(1, 0x1000000).voiceAllowed(), "Private editor suppresses voice");
    check(
        SymbolLayout.rows(false)[1].equals("@#$_&-+()/"),
        "Primary punctuation follows reference placement");
    check(
        SymbolLayout.rows(true)[0].equals("~`|•√π÷×§Δ"),
        "Secondary symbols follow reference placement");
    check(SymbolLayout.rows(true)[1].equals("£¢€¥^°={}\\"), "Currency and bracket placement");
    check(
        SymbolLayout.operators(false).equals("+-*/()"), "Number pad has all scrollable operators");
    check(SymbolLayout.operators(true).equals("+,;"), "Phone pad provides plus, pause and wait");
    check(
        KeyboardMode.NUMPAD.next(true) == KeyboardMode.ALPHABET, "Number pad returns to alphabet");
    List<Double> narrow = new ArrayList<>();
    for (char letter : "keyboard".toCharArray()) narrow.add(qwerty.center(letter));
    java.util.Collections.swap(narrow, 2, 3);
    check(
        decoder.forgiving(narrow, qwerty, 180, 3).stream()
            .anyMatch(c -> c.word().equals("keyboard")),
        "Narrow keyboard transposition remains selectable");
    narrow.clear();
    for (char letter : "keyboad".toCharArray()) narrow.add(qwerty.center(letter));
    check(
        decoder.forgiving(narrow, qwerty, 180, 3).stream()
            .anyMatch(c -> c.word().equals("keyboard")),
        "Narrow keyboard missing tap remains selectable");
    narrow.clear();
    for (char letter : "keybboard".toCharArray()) narrow.add(qwerty.center(letter));
    check(
        decoder.forgiving(narrow, qwerty, 180, 3).stream()
            .anyMatch(c -> c.word().equals("keyboard")),
        "Repeated tap remains selectable");
    check(KeyboardMode.MINI.next(true) == KeyboardMode.ALPHABET, "Mini cycles to full alphabet");
    check(
        KeyboardMode.ALPHABET.next(true) == KeyboardMode.NUMBERS,
        "Alphabet cycles to full numbers");
    check(KeyboardMode.NUMBERS.next(true) == KeyboardMode.SYMBOLS, "Numbers cycle to full symbols");
    check(KeyboardMode.SYMBOLS.next(true) == KeyboardMode.MINI, "Symbols cycle to Mini");
    check(
        KeyboardMode.SYMBOLS.next(false) == KeyboardMode.ALPHABET, "Restricted editors skip Mini");
    check(
        KeyboardMode.MINI.nextLabel(true).equals("ABC"), "Mini button names next alphabet layout");
    check(
        KeyboardMode.NUMBERS.nextLabel(true).equals("#+="),
        "Numbers button names next symbol layout");
    check(
        KeyboardMode.SYMBOLS.nextLabel(true).equals("Mini"),
        "Symbols button names next compact layout");
    List<Double> sloppyQuick = new ArrayList<>();
    for (char letter : "ankrj".toCharArray()) sloppyQuick.add(qwerty.center(letter));
    for (double width : new double[] {360, 240, 180}) {
      check(
          decoder.forgiving(sloppyQuick, qwerty, width, 32).get(0).word().equals("quick"),
          "Sloppy five-tap ankrj suggests quick at " + width + "dp");
    }
    List<Double> commonTaps = new java.util.ArrayList<>();
    for (char letter : "there".toCharArray()) commonTaps.add(qwerty.center(letter));
    check(
        decoder.forgiving(commonTaps, qwerty, 240, 32).size() > 4,
        "Compact predictions retain a scrollable shortlist");
    check(
        decoder.full("hello").get(0).word().equals("hello"),
        "Full exact spelling survives frequency weighting");
    System.out.println(
        "PASS: "
            + assertions
            + " core assertions; starter vocabulary: "
            + decoder.seedSize()
            + " words");
  }
}
