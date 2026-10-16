// SPDX-License-Identifier: Apache-2.0
package org.linekey.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AdaptationChecks {
    private static int assertions;

    private static void check(final boolean value, final String message) {
        assertions++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    static int run() {
        final Decoder decoder = new Decoder();
        for (final String phrase :
                List.of(
                        "hello world",
                        "this is a test",
                        "see you tomorrow",
                        "thank you",
                        "good morning")) {
            for (final boolean compact : new boolean[] {false, true}) {
                final List<Decoder.Candidate> matches =
                        decoder.missingSpaces(phrase.replace(" ", ""), "<s>", compact);
                check(
                        matches.stream()
                                .anyMatch((final Decoder.Candidate c) -> c.word().equals(phrase)),
                        "Missing spaces: " + phrase);
            }
        }
        for (final String word :
                List.of(
                        "therapist",
                        "together",
                        "install",
                        "keyboard",
                        "a",
                        "hello-world",
                        "1234",
                        "")) {
            check(decoder.missingSpaces(word, "<s>", true).isEmpty(), "No unsafe split: " + word);
        }
        check(decoder.missingSpaces("a".repeat(33), "<s>", true).isEmpty(), "Bounded segmentation");
        check(
                decoder.missingSpaces("hellp", "<s>", false).isEmpty(),
                "Do not split an ordinary typo into rare two-letter fragments");
        final Personalization data = new Personalization();
        check(data.learn("linekey", "use", 3), "Learn explicit choice");
        check(data.pairs().get("use\tlinekey") == 3, "Adjacent pair stored");
        check(
                data.boost("use", "linekey") > data.boost("other", "linekey"),
                "Context-specific boost");
        check(
                data.learn("hello world", "say", 1) && data.pairs().containsKey("hello\tworld"),
                "Phrase tokenization");
        check(!data.learn("not a valid 123", "<s>", 1), "Reject invalid phrase atomically");
        check(!data.words().containsKey("not"), "Invalid phrase leaves no partial counts");
        final LanguageModel model = LanguageModel.get();
        for (int i = 0; i < 20; i++) {
            data.learn("qzxlinekey", "use", 3);
        }
        check(
                data.next(model, "use", 16, true).stream()
                        .anyMatch(
                                (final LanguageModel.Prediction p) ->
                                        p.word().equals("qzxlinekey")),
                "Personal next-word outside corpus");
        check(
                data.next(model, "use", 16, false).stream()
                        .noneMatch(
                                (final LanguageModel.Prediction p) ->
                                        p.word().equals("qzxlinekey")),
                "Learning switch disables pair adaptation");
        final List<Decoder.Candidate> candidates =
                List.of(
                        new Decoder.Candidate("linekey", 1, 0),
                        new Decoder.Candidate("other", 1, 1));
        check(
                data.rescore(candidates, "use", true).get(0).word().equals("linekey"),
                "Learned context reranks matches");
        data.forget("LINEKEY");
        check(!data.words().containsKey("linekey"), "Forget removes count");
        check(
                data.pairs().keySet().stream()
                        .noneMatch((final String p) -> p.contains("linekey") && !p.contains("qzx")),
                "Forget removes related pairs");
        check(
                data.rescore(candidates, "use", true).stream()
                        .noneMatch((final Decoder.Candidate c) -> c.word().equals("linekey")),
                "Forget filters matching");
        check(!data.learn("linekey", "use", 3), "Typing cannot silently restore forgotten word");
        final Personalization restored = new Personalization();
        restored.restore(data.words(), data.pairs(), data.forgotten());
        check(
                restored.isForgotten("linekey") && restored.pairs().equals(data.pairs()),
                "Persistence round-trip");
        check(
                restored.teach("linekey") && !restored.isForgotten("linekey"),
                "Manual teach restores word");
        data.forget("i");
        check(
                data.teach("I") && !data.isForgotten("i"),
                "Manual teaching can restore one-letter words");
        data.forget("world");
        check(
                data.rescore(List.of(new Decoder.Candidate("hello world", 0, 5)), "say", true)
                        .isEmpty(),
                "Forgotten token excluded from phrases");
        data.forget("you");
        check(
                data.next(model, "<s>", 16, false).stream()
                        .noneMatch((final LanguageModel.Prediction p) -> p.word().equals("you")),
                "Forget filters corpus next words");
        for (int i = 0; i < 1500; i++) {
            data.learn("hello", "world", 3);
        }
        check(
                data.words().get("hello") <= 1000 && data.boost("world", "hello") <= 4.5,
                "Learning saturates");
        final Map<String, Integer> many = new LinkedHashMap<>();
        for (int i = 0; i < 700; i++) {
            many.put("word" + letters(i), 1);
        }
        restored.restore(many, Map.of("bad pair", 1, "use\tword", -1), Set.of());
        check(
                restored.words().size() == 500 && restored.pairs().isEmpty(),
                "Bounded validated import");
        for (final String word : many.keySet()) {
            restored.forget(word);
        }
        check(restored.forgotten().size() == 500, "Bounded forgotten list");
        restored.forget(many.keySet().stream().skip(699).findFirst().orElseThrow());
        check(
                restored.forgotten().size() == 500,
                "Repeated forget does not evict another suppression");
        for (int i = 0; i < 2200; i++) {
            data.learn("word" + letters(i % 500), "context" + letters(i / 500), 1);
        }
        check(
                data.words().size() <= 500 && data.pairs().size() == 2000,
                "Learning caps and replaces bounded word/pair entries");
        return assertions;
    }

    private static String letters(final int value) {
        return ""
                + (char) ('a' + value / 676)
                + (char) ('a' + value / 26 % 26)
                + (char) ('a' + value % 26);
    }
}
