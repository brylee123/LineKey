# Missing spaces and personal predictions

LineKey 0.7.6 adds missing-space suggestions, optional word/pair adaptation, and individual word forgetting. All processing and saved learning remain on the device.

## Missing spaces

With **Suggest missing spaces** enabled (default), joined letter sequences such as `helloworld`, `thisisatest`, and `seeyoutomorrow` can offer `hello world`, `this is a test`, and `see you tomorrow`. Tap a phrase to insert it; Backspace immediately restores the original joined input and rolls back associated learning.

The bounded segmenter uses vocabulary frequencies and the existing bigram model. It considers up to five words, 32 letters, and eight partial paths per boundary. Components must have frequency >=500 (>=2,000 for two-letter tokens, to reduce acronym fragments); single letters are restricted to `a` and `i`. Existing words with frequency >=2,000 are protected. Rare joined dictionary entries such as `thankyou` may still offer a spaced alternative. The literal input is always available.

Repairs are explicit suggestions, not automatic Space replacements. This first version repairs omitted boundaries in otherwise correctly transcribed letters. Approximate touch decoding still handles ordinary single-word input; joint typo-plus-space recovery is not implemented. Omitted punctuation is handled separately by the punctuated-word matcher.

## Optional adaptation

Enable **Learn words and word pairs on this device** in Settings. Learning remains off by default. Existing personal word counts are migrated without deleting them. Manually taught vocabulary remains available even when automatic learning is off.

- Up to 500 canonical word counts and 2,000 adjacent word-pair counts are saved in app-private preferences. Counts saturate at 1,000; new entries replace the least-used entry when full.
- Explicit suggestion choices and recorrections have weight 3. Literal word commits have weight 1. An automatically substituted guess does not teach itself.
- Word-frequency preference is capped at 1.5 score units; the pair preference is capped at 3. Spatial/spelling evidence and the corpus model remain part of the ranking. Known words retain their corpus frequency instead of receiving the old very large synthetic frequency.
- Learned pairs contribute next-word candidates even when those words are absent from the fixed corpus model. Multiword suggestions teach their component pairs.
- Immediate correction undo restores the previous learning snapshot. Later document edits/deletions do not attempt to reconstruct or unlearn the entire message history.
- Passwords, literal fields (including email/URL), raw input, and editors requesting no personalized learning never teach tokens or pairs. Learning stores lexical tokens/pairs, not full messages, cursor traces, or editor snapshots. Pairs can still reveal personal information; learning is optional and erasable. Android backup remains disabled.

## Forget a word

Long-press a single-word suggestion, then choose **Forget this word** in the Material dialog. This removes its personal count and every learned pair containing it, and hides it from correction/next-word predictions. Space-repair phrases containing it are filtered too. Current literal input and the word currently being revisited remain available so the editor text is never silently changed.

The built-in dictionary is not modified. The suppression list persists independently of the automatic-learning switch, with a maximum of 500 entries. When full, an older suppression is retired. Typing a forgotten word does not automatically restore it. Use **Teach an English word → Add word** in Settings to explicitly restore it. **Erase personal learning** clears words, pairs, and suppressions.

The action applies to individual word suggestions; multiword space-repair suggestions do not expose a whole-phrase forget action.
