// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static String normalize(final String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static List<Indexed> load() {
        final List<Indexed> entries = new ArrayList<>();
        try (final InputStream stream = EmojiSearch.class.getResourceAsStream("/emoji-index.tsv")) {
            if (stream == null) {
                throw new IllegalStateException("Missing emoji index");
            }
            final BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                final String[] fields = line.split("\t", -1);
                final Entry entry = new Entry(fields[0], fields[1], fields[2]);
                final String name = normalize(entry.name());
                entries.add(
                        new Indexed(
                                entry,
                                name,
                                normalize(entry.name() + " " + entry.tags()).split(" +")));
            }
        } catch (final IOException error) {
            throw new IllegalStateException("Cannot read emoji index", error);
        }
        return entries;
    }

    public static int size() {
        return INDEX.size();
    }

    public static List<Entry> search(final String query, final int limit) {
        final String normalized = normalize(query);
        final List<Match> matches = new ArrayList<>();
        final String symbol = query.trim().replace("\ufe0f", "");
        if (normalized.isEmpty() && !symbol.isEmpty()) {
            for (final Indexed entry : INDEX) {
                if (entry.entry().symbol().replace("\ufe0f", "").equals(symbol)) {
                    matches.add(new Match(entry.entry(), 0));
                }
            }
        } else {
            final String[] terms = normalized.isEmpty() ? new String[0] : normalized.split(" +");
            for (final Indexed entry : INDEX) {
                int score = 0;
                for (final String term : terms) {
                    final String target = ALIASES.getOrDefault(term, term);
                    int best = 100;
                    for (final String word : entry.words()) {
                        int cost = word.equals(target) ? 0 : word.startsWith(target) ? 2 : 100;
                        if (cost == 100
                                && target.length() >= 4
                                && Math.abs(word.length() - target.length()) <= 1
                                && Decoder.editDistance(target, word) <= 1) {
                            cost = 6;
                        }
                        best = Math.min(best, cost);
                    }
                    score += best;
                    if (best == 100) {
                        break;
                    }
                }
                if (score < 100) {
                    if (entry.name().equals(normalized)) {
                        score -= 20;
                    } else if (!normalized.isEmpty() && entry.name().startsWith(normalized)) {
                        score -= 10;
                    }
                    // Base emoji are easier to browse before skin-tone and gender variants.
                    if (entry.entry().name().contains("skin tone")) {
                        score += 12;
                    }
                    matches.add(new Match(entry.entry(), score));
                }
            }
        }
        matches.sort(Comparator.comparingInt(Match::score));
        final List<Entry> result = new ArrayList<>();
        for (final Match match : matches) {
            if (result.size() >= limit) {
                break;
            }
            result.add(match.entry());
        }
        return result;
    }
}
