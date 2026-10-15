// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.io.*;
import java.util.*;

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
  private int pairCount;
  private final Map<String, Double> backoffs = new HashMap<>();
  private final List<Prediction> fallback = new ArrayList<>();

  private LanguageModel() {
    try (InputStream input = LanguageModel.class.getResourceAsStream("/english-model.bin")) {
      if (input == null) throw new IllegalStateException("Missing language model");
      DataInputStream reader = new DataInputStream(new BufferedInputStream(input));
      if (reader.readInt() != 0x4c4b4c4d) throw new IOException("Invalid model signature");
      int count = reader.readInt();
      if (count < 1 || count > 65535) throw new IOException("Invalid model vocabulary");
      String[] words = new String[count];
      for (int index = 0; index < count; index++) {
        words[index] = reader.readUTF();
        unigrams.put(words[index], (double) reader.readFloat());
        backoffs.put(words[index], (double) reader.readFloat());
      }
      for (String word : words) {
        int size = reader.readUnsignedByte();
        List<Prediction> predictions = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
          int id = reader.readUnsignedShort();
          if (id >= count) throw new IOException("Invalid model word index");
          predictions.add(new Prediction(words[id], reader.readFloat()));
          pairCount++;
        }
        if (!predictions.isEmpty()) successors.put(word, predictions);
      }
    } catch (IOException error) {
      throw new IllegalStateException("Cannot read language model", error);
    }
    for (var entry : unigrams.entrySet())
      if (!entry.getKey().startsWith("<"))
        fallback.add(new Prediction(entry.getKey(), entry.getValue()));
    fallback.sort(
        Comparator.comparingDouble(Prediction::logProbability)
            .reversed()
            .thenComparing(Prediction::word));
    for (List<Prediction> words : successors.values())
      words.sort(Comparator.comparingDouble(Prediction::logProbability).reversed());
  }

  public int pairCount() {
    return pairCount;
  }

  public static String previousWord(CharSequence context) {
    String text = context == null ? "" : context.toString().toLowerCase(Locale.ROOT).trim();
    if (text.isEmpty() || text.matches(".*[.!?]$")) return "<s>";
    String[] words = text.split("[^a-z']+");
    return words.length == 0 ? "<s>" : words[words.length - 1];
  }

  public double score(String previous, String word) {
    if (!backoffs.containsKey(previous)) previous = "<unk>";
    for (Prediction prediction : successors.getOrDefault(previous, Collections.emptyList()))
      if (prediction.word().equals(word)) return prediction.logProbability();
    return unigrams.getOrDefault(word, -8.0) + backoffs.getOrDefault(previous, 0.0);
  }

  public List<Prediction> next(String previous, int limit) {
    if (!backoffs.containsKey(previous)) previous = "<unk>";
    List<Prediction> result = successors.get(previous);
    if (result == null || result.isEmpty()) result = fallback;
    return new ArrayList<>(result.subList(0, Math.min(limit, result.size())));
  }

  public List<Decoder.Candidate> rescore(List<Decoder.Candidate> candidates, String previous) {
    List<Decoder.Candidate> result = new ArrayList<>();
    for (var candidate : candidates) {
      double contextScore = 0;
      String context = previous;
      String[] tokens = candidate.word().split(" ");
      for (String token : tokens) {
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
