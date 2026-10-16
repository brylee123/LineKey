# Java code style

The project uses 4-space indentation and same-line braces, with google-java-format **1.28.0 in AOSP mode** (100-column wrapping and 8-space continuations). The formatter is pinned by SHA-256. `.editorconfig` supplies editor defaults; `AGENTS.md` records the conventions for future changes.

## Final by default

All method/constructor parameters, explicit lambda parameters, catch parameters, and resource declarations use `final`. Use final local values and fields whenever their bindings do not need to change. Normalize an argument into a separately named value instead of reassigning it:

```java
public double score(final String previous, final String word) {
    final String context = backoffs.containsKey(previous) ? previous : "<unk>";
    return unigrams.getOrDefault(word, -8.0) + backoffs.getOrDefault(context, 0.0);
}
```

A final collection or array reference still permits updating its contents. This migration does not change collection semantics or force unnecessary defensive copies. Record components are inherently final and cannot be written with an explicit final component modifier.

Keep real algorithm state mutable: loop counters, minimum/maximum accumulators, rolling word context, lazy buffers, and reused dynamic-programming row pointers. Android lifecycle fields initialized after construction and interactive state also remain mutable. Do not add wrappers, streams, allocations, repeated calculations, or unsafe constructor/context work just to increase the number of final modifiers. Immutable model state such as the loaded pair count is computed locally and assigned to a final field once.

## Types, imports, and braces

Use explicit types instead of `var`, including lambda parameters. Use explicit imports; keep fully qualified types only when needed to distinguish names (for example, the app's `R` and `android.R`). Declare one field/local per statement; classic loop initializers can group counters.

Always brace control-flow bodies, including one-line bodies. Conventional `else if` chains remain flat. Expression lambdas remain permitted, with explicit final parameters.

## Format and check

Use Python 3 and a JDK 17 or newer. Set `JAVA_HOME` or put `java` on PATH:

```text
python tools/format_java.py
python tools/format_java.py --check
```

The first run downloads the official formatter into ignored `.gradle/style` and verifies its pinned checksum. For an offline run, supply the existing formatter with `--formatter-jar PATH`. Use `--java PATH` to select a JDK executable explicitly.

The command runs both the formatter and a JDK syntax-tree checker. It checks final parameters/resources, explicit lambda types, absence of `var`/wildcard imports, and control-flow braces across app/core/test/benchmark/tool sources. It needs no Android SDK or extra parser dependency. The checker deliberately does not claim full definite-assignment or lifecycle analysis: compiler errors, code review, and behavioral checks still determine which local bindings and fields truly require mutation.

The formatter fixes layout/import ordering; it does not automatically change parameter or variable semantics. Existing Gradle build/core verification commands remain unchanged.
