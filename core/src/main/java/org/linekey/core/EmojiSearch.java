// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

/** Offline Unicode name/keyword search with conservative spelling tolerance. */
public final class EmojiSearch {
  public record Entry(String symbol, String name, String tags) {}

  private record Indexed(Entry entry, String name, String[] words) {}

  private record Match(Entry entry, int score) {}

  private static final List<Indexed> INDEX = load();
  private static final Map<String, String> ALIASES = new HashMap<>();

  static {
    ALIASES.put("kitty", "cat");
    ALIASES.put("kitten", "cat");
    ALIASES.put("puppy", "dog");
    ALIASES.put("lol", "laugh");
    ALIASES.put("haha", "laugh");
    ALIASES.put("sad", "cry");
    ALIASES.put("100", "hundred");
    ALIASES.put("ok", "okay");
  }

  private static String normalize(String text) {
    return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
        .replaceAll("\\p{M}", "")
        .replaceAll("[^a-z0-9]+", " ")
        .trim();
  }

  private static List<Indexed> load() {
    List<Indexed> entries = new ArrayList<>();
    try (InputStream stream = EmojiSearch.class.getResourceAsStream("/emoji-index.tsv")) {
      if (stream == null) throw new IllegalStateException("Missing emoji index");
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
      for (String line; (line = reader.readLine()) != null; ) {
        String[] fields = line.split("\t", -1);
        Entry entry = new Entry(fields[0], fields[1], fields[2]);
        String name = normalize(entry.name());
        entries.add(
            new Indexed(entry, name, normalize(entry.name() + " " + entry.tags()).split(" +")));
      }
    } catch (IOException error) {
      throw new IllegalStateException("Cannot read emoji index", error);
    }
    return entries;
  }

  public static int size() {
    return INDEX.size();
  }

  public static List<Entry> search(String query, int limit) {
    String normalized = normalize(query);
    List<Match> matches = new ArrayList<>();
    String symbol = query.trim().replace("\ufe0f", "");
    if (normalized.isEmpty() && !symbol.isEmpty()) {
      for (Indexed entry : INDEX)
        if (entry.entry().symbol().replace("\ufe0f", "").equals(symbol))
          matches.add(new Match(entry.entry(), 0));
    } else {
      String[] terms = normalized.isEmpty() ? new String[0] : normalized.split(" +");
      for (Indexed entry : INDEX) {
        int score = 0;
        for (String term : terms) {
          String target = ALIASES.getOrDefault(term, term);
          int best = 100;
          for (String word : entry.words()) {
            int cost = word.equals(target) ? 0 : word.startsWith(target) ? 2 : 100;
            if (cost == 100
                && target.length() >= 4
                && Math.abs(word.length() - target.length()) <= 1
                && Decoder.editDistance(target, word) <= 1) cost = 6;
            best = Math.min(best, cost);
          }
          score += best;
          if (best == 100) break;
        }
        if (score < 100) {
          if (entry.name().equals(normalized)) score -= 20;
          else if (!normalized.isEmpty() && entry.name().startsWith(normalized)) score -= 10;
          // Base emoji are easier to browse before skin-tone and gender variants.
          if (entry.entry().name().contains("skin tone")) score += 12;
          matches.add(new Match(entry.entry(), score));
        }
      }
    }
    matches.sort(Comparator.comparingInt(Match::score));
    List<Entry> result = new ArrayList<>();
    for (Match match : matches) {
      if (result.size() >= limit) break;
      result.add(match.entry());
    }
    return result;
  }
}
