// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Immutable corpus-trained bigram model. Data is separately CC BY-SA 3.0. */
public final class LanguageModel {
    public record Prediction(String word, double logProbability) {}

    private static final class Holder {
        static final LanguageModel INSTANCE = new LanguageModel();
    }

    public static LanguageModel get() {
        return Holder.INSTANCE;
    }

    private final Map<String, Double> unigrams = new HashMap<>();
    private final Map<String, List<Prediction>> successors = new HashMap<>();
    private final int pairCount;
    private final Map<String, Double> backoffs = new HashMap<>();
    private final List<Prediction> fallback = new ArrayList<>();

    private LanguageModel() {
        int loadedPairCount = 0;
        try (final InputStream input =
                LanguageModel.class.getResourceAsStream("/english-model.bin")) {
            if (input == null) {
                throw new IllegalStateException("Missing language model");
            }
            final DataInputStream reader = new DataInputStream(new BufferedInputStream(input));
            if (reader.readInt() != 0x4c4b4c4d) {
                throw new IOException("Invalid model signature");
            }
            final int count = reader.readInt();
            if (count < 1 || count > 65535) {
                throw new IOException("Invalid model vocabulary");
            }
            final String[] words = new String[count];
            for (int index = 0; index < count; index++) {
                words[index] = reader.readUTF();
                unigrams.put(words[index], (double) reader.readFloat());
                backoffs.put(words[index], (double) reader.readFloat());
            }
            for (final String word : words) {
                final int size = reader.readUnsignedByte();
                final List<Prediction> predictions = new ArrayList<>(size);
                for (int index = 0; index < size; index++) {
                    final int id = reader.readUnsignedShort();
                    if (id >= count) {
                        throw new IOException("Invalid model word index");
                    }
                    predictions.add(new Prediction(words[id], reader.readFloat()));
                    loadedPairCount++;
                }
                if (!predictions.isEmpty()) {
                    successors.put(word, predictions);
                }
            }
        } catch (final IOException error) {
            throw new IllegalStateException("Cannot read language model", error);
        }
        pairCount = loadedPairCount;
        for (final Map.Entry<String, Double> entry : unigrams.entrySet()) {
            if (!entry.getKey().startsWith("<")) {
                fallback.add(new Prediction(entry.getKey(), entry.getValue()));
            }
        }
        fallback.sort(
                Comparator.comparingDouble(Prediction::logProbability)
                        .reversed()
                        .thenComparing(Prediction::word));
        for (final List<Prediction> words : successors.values()) {
            words.sort(Comparator.comparingDouble(Prediction::logProbability).reversed());
        }
    }

    public int pairCount() {
        return pairCount;
    }

    public static String previousWord(final CharSequence context) {
        final String text =
                context == null ? "" : context.toString().toLowerCase(Locale.ROOT).trim();
        if (text.isEmpty() || text.matches(".*[.!?]$")) {
            return "<s>";
        }
        final String[] words = text.split("[^a-z']+");
        return words.length == 0 ? "<s>" : words[words.length - 1];
    }

    public double score(final String previous, final String word) {
        final String adjustedPrevious = backoffs.containsKey(previous) ? previous : "<unk>";
        for (final Prediction prediction :
                successors.getOrDefault(adjustedPrevious, Collections.emptyList())) {
            if (prediction.word().equals(word)) {
                return prediction.logProbability();
            }
        }
        return unigrams.getOrDefault(word, -8.0) + backoffs.getOrDefault(adjustedPrevious, 0.0);
    }

    public List<Prediction> next(final String previous, final int limit) {
        final String adjustedPrevious = backoffs.containsKey(previous) ? previous : "<unk>";
        final List<Prediction> contextual = successors.get(adjustedPrevious);
        final List<Prediction> result =
                contextual == null || contextual.isEmpty() ? fallback : contextual;
        return new ArrayList<>(result.subList(0, Math.min(limit, result.size())));
    }

    public List<Decoder.Candidate> rescore(
            final List<Decoder.Candidate> candidates, final String previous) {
        final List<Decoder.Candidate> result = new ArrayList<>();
        for (final Decoder.Candidate candidate : candidates) {
            double contextScore = 0;
            String context = previous;
            final String[] tokens = candidate.word().split(" ");
            for (final String token : tokens) {
                contextScore += score(context, token);
                context = token;
            }
            result.add(
                    new Decoder.Candidate(
                            candidate.word(),
                            candidate.cost(),
                            candidate.score() + .35 * contextScore / tokens.length));
        }
        result.sort(
                Comparator.comparingDouble(Decoder.Candidate::score)
                        .reversed()
                        .thenComparing(Decoder.Candidate::word));
        return result;
    }
}
