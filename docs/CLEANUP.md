# Readability and theme cleanup

The available project has one IME and one keyboard renderer. This cleanup applies to that LineKey prototype; no separate newer renderer or obsolete renderer was present to remove.

- Theme changes now notify the active IME through a registered preference listener. The listener rebuilds the existing keyboard view without changing its current input state; it is unregistered when the service is destroyed.
- Rendering helpers now say what they do: `renderKeyboard`, `addRow`, `addButton`, and `appendLetter`. Composition and touch-position variables have descriptive names.
- Dense one-line Java methods and branches were expanded, imports were sorted, and unused imports were removed. Formatting uses [google-java-format 1.28.0](https://github.com/google/google-java-format/releases/tag/v1.28.0), kept only as a workspace tool, not an app dependency.
- Temporary composition diagnostics were removed. No keystroke logging was added to the delivered application.
- The setup/test screen now applies explicit IME insets and requests focused-editor scrolling. Screenshot review still shows editor occlusion and bottom navigation overlap on API 37; this is a partial improvement, not a complete layout fix.
- A small Android instrumentation runner exercises real editor connections, password controls, and repeated theme changes. It uses keyboard accessibility windows instead of hard-coded screen coordinates.

No keyboard feature was arbitrarily removed. To retire an older renderer in a different/newer project, inspect that project's source and callers first; its folder was not available in this workspace.
