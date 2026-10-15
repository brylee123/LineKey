// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.keyboard;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import org.linekey.core.LetterLayout;

public final class MainActivity extends AppCompatActivity {
  private Preferences prefs;
  private LinearLayout content, page;

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    prefs = new Preferences(this);
    boolean dark =
        (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    var systemBarsController =
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    systemBarsController.setAppearanceLightStatusBars(!dark);
    systemBarsController.setAppearanceLightNavigationBars(!dark);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setBackgroundColor(
        MaterialColors.getColor(scroll, com.google.android.material.R.attr.colorSurface));
    content = new LinearLayout(this);
    page = content;
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(24), dp(20), dp(24), dp(28));
    scroll.addView(content);
    setContentView(scroll);
    scroll.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            android.graphics.Insets keyboard = insets.getInsets(WindowInsets.Type.ime());
            view.setPadding(
                bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            if (keyboard.bottom > 0) {
              scroll.post(
                  () -> {
                    View focused = scroll.findFocus();
                    if (focused instanceof EditText) {
                      android.graphics.Rect area =
                          new android.graphics.Rect(0, 0, focused.getWidth(), focused.getHeight());
                      focused.requestRectangleOnScreen(area, true);
                      int[] editorPosition = new int[2], scrollPosition = new int[2];
                      focused.getLocationOnScreen(editorPosition);
                      scroll.getLocationOnScreen(scrollPosition);
                      int visibleBottom =
                          scrollPosition[1] + scroll.getHeight() - scroll.getPaddingBottom();
                      int overlap =
                          editorPosition[1] + focused.getHeight() + dp(12) - visibleBottom;
                      if (overlap > 0) scroll.scrollBy(0, overlap);
                    }
                  });
            }
          } else {
            view.setPadding(
                insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
          }
          return insets;
        });
    text("LINEKEY", 14);
    text("A little more room.", 32);
    paragraph(
        "An open-source compact keyboard with offline English predictions and searchable emoji.");
    heading("Set up");
    action(
        "1 · Enable LineKey",
        () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
    action(
        "2 · Choose LineKey",
        () ->
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
    paragraph(
        "Android displays its standard keyboard warning when enabling an IME. LineKey has no"
            + " network, contacts, microphone, or storage permissions.");
    heading("Try it");
    EditText test =
        field(
            "Type hello world here",
            InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    test.setMinLines(2);
    field(
        "Email · literal entry",
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    field(
        "Password · no suggestions or learning",
        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    field(
        "Number pad",
        InputType.TYPE_CLASS_NUMBER
            | InputType.TYPE_NUMBER_FLAG_DECIMAL
            | InputType.TYPE_NUMBER_FLAG_SIGNED);
    field("Phone number", InputType.TYPE_CLASS_PHONE);
    heading("Typing");
    toggle("Start in compact mode", "compact", true);
    select("Letter layout", "layout", LetterLayout.NAMES);
    toggle("Correct on space / punctuation", "correction", true);
    toggle("Capitalize at start of field", "caps", true);
    toggle("Double space for period", "double_space", true);
    toggle("Swipe gestures in compact mode", "gestures", true);
    slider("Swipe distance (dp)", "gesture_distance", 48, 24, 96, 8);
    slider("Hold for precise letters (ms)", "hold_delay", 450, 250, 1000, 50);
    toggle("Suggest missing spaces", "missing_spaces", true);
    toggle("Context-aware corrections", "contextual", true);
    toggle("Next-word predictions", "next_words", true);
    heading("Appearance & feel");
    select("Keyboard theme", "theme", new String[] {"Light", "Dark", "System"});
    select("Keyboard position", "position", new String[] {"Full width", "Left hand", "Right hand"});
    slider("Compact strip height (dp)", "mini_height", 48, 48, 96, 4);
    slider("Key height (dp)", "key_height", 48, 48, 72, 4);
    toggle("Floating keyboard", "floating", false);
    toggle("Key haptics", "haptics", true);
    toggle("System key sounds", "sound", false);
    heading("Personal words");
    toggle("Learn words and word pairs on this device", "learning", false);
    WordStore words = new WordStore(this);
    TextView count = text(words.size() + " personal words · limit 500", 14);
    paragraph(
        "Learning is optional and off by default. It stores up to 500 word counts and 2,000"
            + " adjacent word pairs locally, without saving messages. Explicit choices carry more"
            + " weight; unconfirmed autocorrections do not teach guesses. Passwords, email / URL"
            + " fields, and editors requesting no personalized learning never teach words. Android"
            + " backup is disabled for this app.");
    EditText custom =
        field(
            "Teach an English word",
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    action(
        "Add word",
        () -> {
          boolean added = words.learn(custom.getText().toString().trim());
          if (added) {
            custom.setText("");
            count.setText(words.size() + " personal words · limit 500");
          } else
            custom.setError(
                "Use 1–32 English letters, optionally joined by apostrophes, hyphens or supported"
                    + " word symbols; limit 500 words.");
        });
    action(
        "Erase personal learning",
        () ->
            new MaterialAlertDialogBuilder(this)
                .setTitle("Erase learned words?")
                .setMessage(
                    "This deletes learned words, word pairs, and forgotten-word preferences on this"
                        + " device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                    "Erase",
                    (d, w) -> {
                      words.clear();
                      count.setText("0 personal words · limit 500");
                    })
                .show());
    heading("How to type");
    paragraph(
        "Compact: tap near each letter, then choose a suggestion or press Space. The leftmost"
            + " suggestion always preserves your literal input. Hold a letter, drag to an accent,"
            + " and release to insert. Drag away to cancel. Hold the small upper lane for numbers"
            + " or symbols, or start an upward drag after a brief hold before the accent popup"
            + " opens. A brief hold and horizontal slide selects precise letters.\n\n"
            + "Type letters only for contractions and joined words: dont can suggest don't, and"
            + " wellknown can suggest well-known. Choose the punctuated suggestion to insert it."
            + " Missing spaces such as helloworld can suggest hello world; select the phrase to"
            + " insert it. Common dictionary words are protected; space repairs require selecting a"
            + " suggestion. Long-press a word suggestion to forget it, including learned context."
            + " Literal input stays available.\n\n"
            + "Tap inside an earlier word to revisit its suggestions, or select the whole word."
            + " Choosing a suggestion replaces that word without adding a space. Original"
            + " alternatives are kept for the latest 64 words in the current field session, in"
            + " memory only; private and literal fields are excluded.\n\n"
            + "Swipe left to right to confirm and space; right to left to erase one word; up for"
            + " full mode; down to hide. Tap the layout button to cycle Mini, ABC, numbers and"
            + " symbols. It shows the next layout. Hold it for floating mode, cursor and clipboard"
            + " panels, or settings. Swipe the space bar horizontally to move the cursor."
            + " Double-tap Shift for caps lock.\n\n"
            + "Tap the smiley icon to search emoji. Query typing preserves your message. Tap the"
            + " microphone for an installed speech provider, then review and insert the returned"
            + " dictation. Offline recognition is requested; the provider controls availability and"
            + " networking.");
    action("Open-source licenses", this::showLicenses);
    heading("About this prototype");
    paragraph(
        "LineKey 0.7.1 · Apache-2.0 code\n"
            + "Java 17 · Android 8+\n"
            + "Original code and starter vocabulary. Unicode Emoji 16.0 / CLDR 46 names and"
            + " keywords (Unicode-3.0). Additional English frequency data: Hermit Dave /"
            + " FrequencyWords, CC BY-SA 4.0. Google Sans is licensed under SIL OFL 1.1.\n\n"
            + "Offline English contextual correction and next-word suggestions use a lightweight"
            + " corpus-trained model (WikiText, CC BY-SA 3.0). Conversational accuracy still needs"
            + " validation. Multilingual prediction and compact screen-reader access remain future"
            + " work. Clipboard history is session-only, read when you open its panel, and hidden"
            + " in private fields. Full mode provides individually accessible letter keys.");
    paragraph(
        "Changes take effect when you return to a text field. Project documentation includes the"
            + " architecture, data sources, verification results and development roadmap.");
  }

  void showLicenses() {
    String[] names = {
      "Project notices",
      "Keyboard icons",
      "Material icon license",
      "Word data",
      "Code license",
      "Word frequency license",
      "Unicode license",
      "Language data license",
      "Language model",
      "Font license",
      "Font trademarks",
      "Emoji data",
      "UI libraries",
      "CommonMark license"
    };
    String[] files = {
      "NOTICE.txt",
      "ICON_DATA.md",
      "Material-Symbols-Apache-2.0.txt",
      "WORD_DATA.md",
      "Apache-2.0.txt",
      "CC-BY-SA-4.0.txt",
      "Unicode-3.0.txt",
      "CC-BY-SA-3.0.txt",
      "LANGUAGE_MODEL.md",
      "Google-Sans-OFL.txt",
      "Google-Sans-TRADEMARKS.md",
      "EMOJI_DATA.md",
      "UI_LIBRARIES.md",
      "commonmark-BSD-2-Clause.txt"
    };
    new MaterialAlertDialogBuilder(this)
        .setTitle("Open-source licenses")
        .setItems(
            names, (dialog, which) -> MarkdownDocuments.show(this, names[which], files[which]))
        .setNegativeButton("Close", null)
        .show();
  }

  private int dp(int n) {
    return MaterialUi.dp(this, n);
  }

  private TextView text(String label, int size) {
    TextView view = new MaterialTextView(this);
    view.setText(label);
    view.setTextSize(size);
    view.setTextColor(
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface));
    view.setPadding(0, dp(5), 0, dp(7));
    content.addView(view);
    return view;
  }

  private void paragraph(String caption) {
    TextView view = text(caption, 15);
    view.setTextColor(
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant));
    view.setLineSpacing(dp(3), 1);
  }

  private void heading(String caption) {
    var card = MaterialUi.card(this);
    LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, -2);
    bounds.topMargin = dp(12);
    page.addView(card, bounds);
    content = MaterialUi.column(this);
    content.setPadding(dp(16), dp(12), dp(16), dp(16));
    card.addView(content);
    TextView title = text(caption, 22);
    title.setPadding(0, dp(4), 0, dp(12));
  }

  private void action(String label, Runnable run) {
    var button = new MaterialButton(this);
    button.setText(label);
    button.setAllCaps(false);
    button.setOnClickListener(view -> run.run());
    LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, dp(56));
    bounds.topMargin = dp(4);
    content.addView(button, bounds);
  }

  private EditText field(String hint, int type) {
    var field =
        (TextInputLayout) getLayoutInflater().inflate(R.layout.menu_textfield, content, false);
    field.setHint(hint);
    var input = field.getEditText();
    input.setInputType(type);
    input.setTextSize(16);
    input.setMinHeight(dp(56));
    LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, -2);
    bounds.topMargin = dp(8);
    bounds.bottomMargin = dp(4);
    content.addView(field, bounds);
    return input;
  }

  private void toggle(String label, String key, boolean fallback) {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(6), 0, dp(6));
    TextView caption = new MaterialTextView(this);
    caption.setText(label);
    caption.setTextSize(16);
    row.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
    var control = new MaterialSwitch(this);
    control.setContentDescription(label);
    control.setChecked(prefs.bool(key, fallback));
    control.setOnCheckedChangeListener((button, value) -> prefs.set(key, value));
    caption.setOnClickListener(view -> control.toggle());
    row.addView(control);
    content.addView(row, new LinearLayout.LayoutParams(-1, -2));
  }

  private void select(String label, String key, String[] items) {
    var field =
        (TextInputLayout) getLayoutInflater().inflate(R.layout.menu_dropdown, content, false);
    field.setHint(label);
    var input = (MaterialAutoCompleteTextView) field.getEditText();
    input.setInputType(InputType.TYPE_NULL);
    input.setSimpleItems(items);
    input.setText(items[Math.max(0, Math.min(items.length - 1, prefs.number(key, 0)))], false);
    input.setOnItemClickListener((parent, view, position, id) -> prefs.set(key, position));
    input.setContentDescription(label);
    LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, -2);
    bounds.topMargin = dp(8);
    bounds.bottomMargin = dp(8);
    content.addView(field, bounds);
  }

  private void slider(String label, String key, int fallback, int min, int max, int step) {
    int current = Math.max(min, Math.min(max, prefs.number(key, fallback)));
    TextView caption = text(label + ": " + current, 15);
    var slider = new Slider(this);
    slider.setValueFrom(min);
    slider.setValueTo(max);
    slider.setStepSize(step);
    slider.setValue(min + Math.round((current - min) / (float) step) * step);
    slider.setContentDescription(label);
    slider.addOnChangeListener(
        (control, value, fromUser) -> {
          caption.setText(label + ": " + (int) value);
          if (fromUser) prefs.set(key, (int) value);
        });
    content.addView(slider);
  }
}
