// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

/** Spatial sequence decoder with independently licensed word frequencies. */
public final class Decoder {
    public record Candidate(String word, double cost, double score) {}

    private record Word(
            String text, String letters, int frequency, double logFrequency, double compactPrior) {
        Word(final String text, final int frequency) {
            this(text, WordSpelling.matchingLetters(text), frequency);
        }

        Word(final String text, final String letters, final int frequency) {
            this(
                    text,
                    letters,
                    frequency,
                    Math.log(frequency),
                    .16 * Math.log(frequency)
                            + commonWordBonus(frequency)
                            - (text.equals(letters) ? 0 : .15));
        }
    }

    /** Saturating frequency prior: common words get a preference without excluding rare entries. */
    private static double commonWordBonus(final int frequency) {
        if (frequency <= 500) {
            return 0;
        }
        if (frequency >= 5000) {
            return 3;
        }
        return 3 * Math.log(frequency / 500.0) / Math.log(10);
    }

    private record Vocabulary(Map<String, Word> words, List<List<Word>> byLength) {}

    private static final class VocabularyHolder {
        static final Vocabulary DATA = loadVocabulary();
    }

    private Map<String, Word> vocabulary;
    private List<List<Word>> byLength;
    private boolean personalized;

    public Decoder() {
        // Share immutable base data across editor restarts; copy only when local learning adds a
        // word.
        vocabulary = VocabularyHolder.DATA.words();
        byLength = VocabularyHolder.DATA.byLength();
    }

    private static Vocabulary loadVocabulary() {
        final Map<String, Word> vocabulary = new LinkedHashMap<>();
        // Hand-authored starter vocabulary. These weights are illustrative, not corpus frequencies.
        final String seed =
                "the of and to a in is it you that he was for on are with as I his they be at one"
                    + " have this from or had by not word but what all were we when your can said"
                    + " there use an each which she do how their if will up other about out many"
                    + " then them these so some her would make like him into time has look two more"
                    + " write go see number no way could people my than first water been call who"
                    + " its now find long down day did get come made may part hello world keyboard"
                    + " small compact open source android line key type typing test thanks thank"
                    + " good morning night yes okay great easy screen space full precise gesture"
                    + " prediction emoji happy love home work today tomorrow meet please message"
                    + " phone text friend family new old need want know think say help here where"
                    + " why again just only very much really still well also back next right left"
                    + " delete learn settings language data privacy local word words coffee cat dog"
                    + " quick brown fox jumps over lazy sentence letter letters words punctuation"
                    + " stop start build project code free beautiful cool awesome nice better best";
        loadWords(vocabulary, "/english-frequency.txt");
        loadWords(vocabulary, "/english-punctuated.txt");
        int weight = 300;
        for (final String s : seed.split(" ")) {
            vocabulary.putIfAbsent(
                    s.toLowerCase(Locale.ROOT),
                    new Word(s.toLowerCase(Locale.ROOT), Math.max(2, weight--)));
        }

        final List<List<Word>> lengths = new ArrayList<>();
        for (int length = 0; length <= 32; length++) {
            lengths.add(new ArrayList<>());
        }
        for (final Word word : vocabulary.values()) {
            lengths.get(word.letters().length()).add(word);
        }
        for (int length = 0; length < lengths.size(); length++) {
            lengths.set(length, Collections.unmodifiableList(lengths.get(length)));
        }
        return new Vocabulary(
                Collections.unmodifiableMap(vocabulary), Collections.unmodifiableList(lengths));
    }

    private static void loadWords(final Map<String, Word> vocabulary, final String resource) {
        final boolean plainLetters = resource.equals("/english-frequency.txt");
        try (final InputStream input = Decoder.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing vocabulary: " + resource);
            }
            final BufferedReader reader =
                    new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                final int separator = line.lastIndexOf(' ');
                final String rawWord = line.substring(0, separator);
                final String word = plainLetters ? rawWord : WordSpelling.canonical(rawWord);
                final int frequency = Integer.parseInt(line.substring(separator + 1));
                if ((!plainLetters && !WordSpelling.valid(word)) || frequency < 1) {
                    throw new IllegalStateException("Invalid vocabulary spelling: " + word);
                }
                vocabulary.put(word, new Word(word, frequency));
            }
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot load vocabulary", e);
        }
    }

    public int seedSize() {
        return vocabulary.size();
    }

    public void addWord(final String word, final int count) {
        if (!WordSpelling.valid(word)) {
            return;
        }
        if (VocabularyHolder.DATA.words().containsKey(WordSpelling.canonical(word))) {
            return;
        }
        if (!personalized) {
            vocabulary = new LinkedHashMap<>(vocabulary);
            final List<List<Word>> mutable = new ArrayList<>();
            for (final List<Word> length : byLength) {
                mutable.add(new ArrayList<>(length));
            }
            byLength = mutable;
            personalized = true;
        }
        final String lower = WordSpelling.canonical(word);
        final Word learned = new Word(lower, 500 + Math.min(1000, Math.max(0, count)) * 10);
        final Word previous = vocabulary.put(lower, learned);
        final List<Word> length = byLength.get(learned.letters().length());
        if (previous != null) {
            length.remove(previous);
        }
        length.add(learned);
    }

    public List<Candidate> compact(final List<Double> taps, final LetterLayout layout) {
        return predict(taps, layout, 1);
    }

    private record Split(String text, int count, double likelihood, double prior) {}

    /**
     * Exact-letter missing-space suggestions, bounded to 32 letters and five words. Kept separate
     * from touch decoding: never silently invent boundaries in an existing word.
     */
    public List<Candidate> missingSpaces(
            final String raw, final String previous, final boolean compact) {
        final String text = raw.toLowerCase(Locale.ROOT);
        if (text.length() < 4
                || text.length() > 32
                || !text.matches("[a-z]+")
                || vocabulary.containsKey(text) && vocabulary.get(text).frequency() >= 2000) {
            return List.of();
        }
        final List<List<Split>> paths = new ArrayList<>();
        for (int i = 0; i <= text.length(); i++) {
            paths.add(new ArrayList<>());
        }
        paths.get(0).add(new Split("", 0, 0, 0));
        final LanguageModel model = LanguageModel.get();
        for (int end = 1; end <= text.length(); end++) {
            final List<Split> found = paths.get(end);
            for (int start = 0; start < end; start++) {
                final Word word = vocabulary.get(text.substring(start, end));
                if (word == null
                        || word.frequency() < 500
                        || word.letters().length() == 2 && word.frequency() < 2000
                        || word.letters().length() == 1
                                && !word.text().equals("a")
                                && !word.text().equals("i")) {
                    continue;
                }
                for (final Split prefix : paths.get(start)) {
                    if (prefix.count() >= 5) {
                        continue;
                    }
                    final String context =
                            prefix.count() == 0
                                    ? previous
                                    : prefix.text().substring(prefix.text().lastIndexOf(' ') + 1);
                    found.add(
                            new Split(
                                    prefix.text().isEmpty()
                                            ? word.text()
                                            : prefix.text() + " " + word.text(),
                                    prefix.count() + 1,
                                    prefix.likelihood()
                                            + word.logFrequency()
                                            - 18
                                            + .2 * model.score(context, word.text()),
                                    prefix.prior()
                                            + (compact
                                                    ? word.compactPrior()
                                                    : .12 * word.logFrequency())));
                }
            }
            found.sort(
                    Comparator.comparingDouble(Split::likelihood)
                            .reversed()
                            .thenComparing(Split::text));
            if (found.size() > 8) {
                found.subList(8, found.size()).clear();
            }
        }
        final List<Candidate> result = new ArrayList<>();
        for (final Split split : paths.get(text.length())) {
            if (split.count() > 1) {
                result.add(
                        new Candidate(
                                split.text(),
                                .5 * (split.count() - 1),
                                split.prior() / split.count() - .7 - .35 * (split.count() - 2)));
            }
        }
        return result;
    }

    /** Same vocabulary, three independently implemented ranking approaches for benchmarking. */
    public List<Candidate> predict(
            final List<Double> taps, final LetterLayout layout, final int algorithm) {
        if (taps.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Candidate> list = new ArrayList<>();
        for (final Map.Entry<String, Word> e : vocabulary.entrySet()) {
            if (Math.abs(e.getValue().letters().length() - taps.size()) > 2) {
                continue;
            }
            final double cost =
                    algorithm == 0
                            ? editDistance(literal(taps, layout), e.getValue().letters()) * 3
                            : algorithm == 1
                                    ? spatialCost(taps, e.getValue().letters(), layout)
                                    : robustCost(taps, e.getValue().letters(), layout);
            if (cost / Math.max(1, taps.size()) <= 1.5) {
                list.add(
                        new Candidate(
                                e.getKey(),
                                cost,
                                -cost
                                        + (algorithm == 2 ? .28 : .12)
                                                * e.getValue().logFrequency()));
            }
        }
        list.sort(
                Comparator.comparingDouble(Candidate::score)
                        .reversed()
                        .thenComparing(Candidate::word));
        return list.subList(0, Math.min(3, list.size()));
    }

    private static final Comparator<Candidate> BEST_FIRST =
            Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::word);

    /**
     * Retain only the requested best matches rather than allocating and sorting every possible
     * word.
     */
    private static final class Shortlist {
        private final int limit;
        private final PriorityQueue<Candidate> words = new PriorityQueue<>(BEST_FIRST.reversed());

        Shortlist(final int limit) {
            this.limit = limit;
        }

        double cutoff() {
            return words.size() < limit ? Double.NEGATIVE_INFINITY : words.peek().score();
        }

        void add(final String word, final double cost, final double score) {
            final Candidate candidate = new Candidate(word, cost, score);
            if (words.size() < limit) {
                words.add(candidate);
            } else if (BEST_FIRST.compare(candidate, words.peek()) < 0) {
                words.poll();
                words.add(candidate);
            }
        }

        List<Candidate> sorted() {
            final List<Candidate> result = new ArrayList<>(words);
            result.sort(BEST_FIRST);
            return result;
        }
    }

    /** Width-aware touch tolerance, missing/repeated taps, and adjacent transpositions. */
    public List<Candidate> forgiving(
            final List<Double> taps,
            final LetterLayout layout,
            final double widthDp,
            final int limit) {
        if (taps.isEmpty() || limit <= 0) {
            return List.of();
        }
        final double sigma = Math.max(.06, Math.min(.10, 16 / Math.max(100, widthDp)));
        // Every word uses the same 26 target positions: calculate each tap/letter loss just once.
        final double[][] losses = new double[taps.size()][26];
        for (int i = 0; i < taps.size(); i++) {
            for (int letter = 0; letter < 26; letter++) {
                final double distance =
                        (taps.get(i) - layout.center((char) ('a' + letter))) / sigma;
                losses[i][letter] = Math.min(5, distance * distance);
            }
        }
        final Shortlist result = new Shortlist(limit);
        final double[][] work = new double[3][33];
        // Start with the expected length so strong candidates establish a useful pruning bound
        // early.
        for (int difference = 0; difference <= 3; difference++) {
            scoreLength(taps.size() - difference, losses, result, work);
            if (difference > 0) {
                scoreLength(taps.size() + difference, losses, result, work);
            }
        }
        return result.sorted();
    }

    private void scoreLength(
            final int length,
            final double[][] losses,
            final Shortlist result,
            final double[][] work) {
        if (length < 1 || length >= byLength.size()) {
            return;
        }
        final int taps = losses.length;
        final double difference = Math.abs(length - taps);
        final double qualityLimit = taps * (taps < 4 ? 1.5 : 2.5);
        for (final Word word : byLength.get(length)) {
            final double prior = -2.4 * difference + word.compactPrior();
            final double budget = Math.min(qualityLimit, prior - result.cutoff());
            if (difference * 2.7 > budget + 1e-9) {
                continue;
            }
            final double cost = forgivingCost(losses, word.letters(), work, budget);
            if (cost <= qualityLimit && cost <= budget + 1e-9) {
                result.add(word.text(), cost, -cost + prior);
            }
        }
    }

    private static double forgivingCost(
            final double[][] losses,
            final String word,
            final double[][] work,
            final double budget) {
        double[] current = work[0];
        double[] previous = work[1];
        double[] twoBack = work[2];

        for (int j = 0; j <= word.length(); j++) {
            previous[j] = j * 2.7;
        }
        double previousMinimum = 0;
        for (int i = 1; i <= losses.length; i++) {
            current[0] = i * 2.7;
            double minimum = current[0];
            for (int j = 1; j <= word.length(); j++) {
                final int letter = word.charAt(j - 1) - 'a';
                current[j] =
                        Math.min(
                                previous[j - 1] + losses[i - 1][letter],
                                Math.min(previous[j] + 2.7, current[j - 1] + 2.7));
                if (i > 1 && j > 1) {
                    current[j] =
                            Math.min(
                                    current[j],
                                    twoBack[j - 2]
                                            + 1.2
                                            + losses[i - 2][letter]
                                            + losses[i - 1][word.charAt(j - 2) - 'a']);
                }
                minimum = Math.min(minimum, current[j]);
            }
            // The next row can still recover through a transposition from the previous row.
            if (Math.min(minimum, previousMinimum + 1.2) > budget + 1e-9) {
                return Double.POSITIVE_INFINITY;
            }
            previousMinimum = minimum;
            final double[] spare = twoBack;
            twoBack = previous;
            previous = current;
            current = spare;
        }
        return previous[word.length()];
    }

    public List<Candidate> full(final String raw) {
        if (raw.isEmpty()) {
            return List.of();
        }
        if (!WordSpelling.valid(raw)) {
            return List.of();
        }
        final String spelling = WordSpelling.canonical(raw);
        final String lower = WordSpelling.letters(spelling);
        final boolean explicitPunctuation = !spelling.equals(lower);
        final int maximum = lower.length() < 4 ? 0 : lower.length() < 7 ? 1 : 2;
        final Shortlist result = new Shortlist(32);
        final int[][] work = new int[2][33];
        for (int length = Math.max(1, lower.length() - maximum);
                length <= Math.min(32, lower.length() + maximum);
                length++) {
            for (final Word word : byLength.get(length)) {
                // Explicit punctuation remains meaningful; omission matching is for letters-only
                // input.
                if (explicitPunctuation && !word.text().equals(spelling)) {
                    continue;
                }
                final int cost = boundedEditDistance(lower, word.letters(), maximum, work);
                if (cost <= maximum) {
                    result.add(
                            word.text(),
                            cost,
                            -cost * 4
                                    - Math.abs(lower.length() - length) * 2
                                    + .12 * word.logFrequency()
                                    - (word.text().equals(spelling) ? 0 : .15));
                }
            }
        }
        return result.sorted();
    }

    private static int boundedEditDistance(
            final String a, final String b, final int limit, final int[][] work) {
        int[] previous = work[0];
        int[] current = work[1];

        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int minimum = i;
            final int start = Math.max(1, i - limit);
            final int end = Math.min(b.length(), i + limit);

            if (start > 1) {
                current[start - 1] = limit + 1;
            }
            if (end < b.length()) {
                current[end + 1] = limit + 1;
            }
            for (int j = start; j <= end; j++) {
                current[j] =
                        Math.min(
                                previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                                Math.min(previous[j] + 1, current[j - 1] + 1));
                minimum = Math.min(minimum, current[j]);
            }
            if (minimum > limit) {
                return limit + 1;
            }
            final int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static String literal(final List<Double> taps, final LetterLayout layout) {
        final StringBuilder text = new StringBuilder();
        for (final double tap : taps) {
            text.append(layout.nearest(tap));
        }
        return text.toString();
    }

    /** Robust alignment: Gaussian near the target, capped loss for outliers, missing/extra taps. */
    public static double robustCost(
            final List<Double> taps, final String word, final LetterLayout layout) {
        final int n = word.length();
        double[] previous = new double[n + 1];
        double[] current = new double[n + 1];

        for (int j = 0; j <= n; j++) {
            previous[j] = j * 3.5;
        }
        for (int i = 1; i <= taps.size(); i++) {
            current[0] = i * 3.5;
            for (int j = 1; j <= n; j++) {
                final double delta =
                        Math.abs(taps.get(i - 1) - layout.center(word.charAt(j - 1))) / .035;
                final double loss = Math.min(6, delta < 1 ? delta * delta : 2 * delta - 1);
                current[j] =
                        Math.min(
                                previous[j - 1] + loss,
                                Math.min(previous[j] + 3.5, current[j - 1] + 3.5));
            }
            final double[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[n];
    }

    public static double spatialCost(
            final List<Double> taps, final String word, final LetterLayout layout) {
        final double[][] d = new double[taps.size() + 1][word.length() + 1];
        for (int i = 0; i <= taps.size(); i++) {
            d[i][0] = i * 3;
        }
        for (int j = 0; j <= word.length(); j++) {
            d[0][j] = j * 3;
        }
        for (int i = 1; i <= taps.size(); i++) {
            for (int j = 1; j <= word.length(); j++) {
                final double delta = (taps.get(i - 1) - layout.center(word.charAt(j - 1))) / .045;
                d[i][j] =
                        Math.min(
                                d[i - 1][j - 1] + Math.min(5, delta * delta),
                                Math.min(d[i - 1][j] + 3, d[i][j - 1] + 3));
            }
        }
        return d[taps.size()][word.length()];
    }

    public static int editDistance(final String a, final String b) {
        final int[] row = new int[b.length() + 1];
        for (int j = 0; j < row.length; j++) {
            row[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int prev = row[0];
            row[0] = i;
            for (int j = 1; j < row.length; j++) {
                final int old = row[j];
                row[j] =
                        Math.min(
                                prev + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                                Math.min(row[j] + 1, row[j - 1] + 1));
                prev = old;
            }
        }
        return row[b.length()];
    }
}
