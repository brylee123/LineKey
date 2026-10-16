# Java style

Apply these rules to application code, core code, tests, benchmarks, and Java tooling:

- Use 4 spaces, same-line opening braces, and the pinned AOSP formatter in `tools/format_java.py`.
- Default to `final` for fields and local bindings. All explicit method, constructor, lambda, and catch parameters must be final. Use final resource declarations too.
- Never reassign parameters. Use clearly named, final normalized/helper values where possible.
- Preserve efficient algorithms and behavior. Mutable counters, rolling context, reusable buffer pointers, accumulators, and Android lifecycle/interaction state may remain mutable when necessary. Do not introduce holder objects, streams, or allocations just to make a reference final.
- Use explicit types everywhere, including lambda parameters; never use `var`.
- Use explicit imports, never wildcard imports. Prefer imported type names over fully qualified names unless disambiguation is needed.
- Always brace `if`, `else`, `for`, enhanced `for`, `while`, and `do` bodies. Conventional `else if` chains and expression lambdas are allowed.
- Declare one field/local per statement (a classic `for` initializer may group counters of one type).
- Record components and their generated members are implicitly final; do not add illegal modifiers to record component syntax.
- Run `python tools/format_java.py --check` and the appropriate build/core checks after changes. Formatting checks do not replace reviewing whether remaining mutable bindings are necessary.

See `docs/CODE_STYLE.md` for commands and examples.
