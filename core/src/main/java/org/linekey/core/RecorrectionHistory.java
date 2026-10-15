// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.ArrayList;
import java.util.List;

/** Bounded, in-memory evidence for words committed in the current editor session. */
public final class RecorrectionHistory {
  private static final int MAX_WORDS = 64;
  public static final int MAX_TEXT = 65536;

  public record Entry(int start, String word, String typed, List<String> alternatives) {
    public int end() {
      return start + word.length();
    }
  }

  public record Target(int start, int end, String word, Entry evidence) {}

  private final List<Entry> entries = new ArrayList<>();
  private String text = "";
  private int offset;

  public void clear() {
    entries.clear();
    text = "";
    offset = 0;
  }

  /** Rebase unaffected ranges after edits; overlapping or unverifiable evidence is discarded. */
  public void observe(String updated, int startOffset) {
    if (updated == null || updated.length() > MAX_TEXT || startOffset < 0) {
      clear();
      return;
    }
    if (startOffset != offset) {
      entries.clear();
    } else if (!updated.equals(text)) {
      int prefix = 0;
      while (prefix < Math.min(text.length(), updated.length())
          && text.charAt(prefix) == updated.charAt(prefix)) prefix++;
      int oldEnd = text.length(), newEnd = updated.length();
      while (oldEnd > prefix
          && newEnd > prefix
          && text.charAt(oldEnd - 1) == updated.charAt(newEnd - 1)) {
        oldEnd--;
        newEnd--;
      }
      // Equal runs can make a deletion/insertion position ambiguous ("word word" → "word").
      // Discard evidence throughout that ambiguous band rather than attributing the wrong taps.
      int suffix = 0;
      while (suffix < Math.min(text.length(), updated.length())
          && text.charAt(text.length() - suffix - 1)
              == updated.charAt(updated.length() - suffix - 1)) suffix++;
      int safePrefix = Math.min(prefix, Math.min(text.length(), updated.length()) - suffix);
      int changeStart = offset + safePrefix, changeEnd = offset + oldEnd;
      int delta = newEnd - oldEnd;
      List<Entry> retained = new ArrayList<>();
      for (Entry entry : entries) {
        if (entry.end() <= changeStart) retained.add(entry);
        else if (entry.start() >= changeEnd)
          retained.add(
              new Entry(entry.start() + delta, entry.word(), entry.typed(), entry.alternatives()));
      }
      entries.clear();
      entries.addAll(retained);
    }
    text = updated;
    offset = startOffset;
    entries.removeIf(entry -> !matches(entry.start(), entry.word()));
  }

  public Entry remember(int start, String word, String typed, List<String> alternatives) {
    if (word.isEmpty() || !matches(start, word)) return null;
    entries.removeIf(entry -> entry.start() < start + word.length() && entry.end() > start);
    Entry entry =
        new Entry(
            start,
            word,
            typed,
            List.copyOf(alternatives.subList(0, Math.min(32, alternatives.size()))));
    entries.add(entry);
    while (entries.size() > MAX_WORDS) entries.remove(0);
    return entry;
  }

  private boolean matches(int start, String word) {
    int local = start - offset;
    return local >= 0 && local + word.length() <= text.length() && text.startsWith(word, local);
  }

  /** Caret within/at the edge of a word, or an exact whole-word selection. Never a phrase. */
  public Target target(int selectionStart, int selectionEnd) {
    if (selectionStart > selectionEnd) {
      int swap = selectionStart;
      selectionStart = selectionEnd;
      selectionEnd = swap;
    }
    if (selectionStart < offset
        || selectionEnd < selectionStart
        || selectionEnd > offset + text.length()) return null;
    int cursor = selectionStart - offset;
    int anchor = cursor;
    if (anchor == text.length() || !wordCharacter(text.charAt(anchor))) anchor--;
    if (anchor < 0 || !wordCharacter(text.charAt(anchor))) return null;
    int start = anchor, end = anchor + 1;
    while (start > 0 && wordCharacter(text.charAt(start - 1))) start--;
    while (end < text.length() && wordCharacter(text.charAt(end))) end++;
    while (start < end && "'’‘".indexOf(text.charAt(start)) >= 0) start++;
    while (end > start && "'’‘".indexOf(text.charAt(end - 1)) >= 0) end--;
    // A sentence's full stop is outside its word; a dotted abbreviation retains its final dot.
    if (end > start
        && text.charAt(end - 1) == '.'
        && text.substring(start, end - 1).indexOf('.') < 0) end--;
    if (cursor < start || cursor > end || start == end) return null;
    int absoluteStart = offset + start, absoluteEnd = offset + end;
    if (selectionStart != selectionEnd
        && (selectionStart != absoluteStart || selectionEnd != absoluteEnd)) return null;
    String word = text.substring(start, end);
    if (!WordSpelling.valid(word)) return null;
    Entry evidence = null;
    for (Entry entry : entries)
      if (entry.start() == absoluteStart && entry.word().equals(word)) evidence = entry;
    return new Target(absoluteStart, absoluteEnd, word, evidence);
  }

  private static boolean wordCharacter(char c) {
    return Character.isLetter(c) || "'.&/+#-’‘‐‑".indexOf(c) >= 0;
  }
}
