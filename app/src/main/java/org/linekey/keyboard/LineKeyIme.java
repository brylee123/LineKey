// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.keyboard;

import android.content.Intent;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.widget.PopupMenu;
import com.google.android.material.textview.MaterialTextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.linekey.core.Decoder;
import org.linekey.core.FieldPolicy;
import org.linekey.core.KeyboardMode;
import org.linekey.core.LetterLayout;

public final class LineKeyIme extends InputMethodService implements CompactStrip.Listener {
  private Preferences preferences;
  private WordStore words;
  private Decoder decoder;
  private LetterLayout layout;
  private org.linekey.core.LanguageModel languageModel;
  private boolean destroyed;
  private final ClipboardSession clipboard = new ClipboardSession();
  private String toolPanel = "";
  private boolean floating, selecting;
  private int floatingX, floatingLift;
  private String composingContext = "<s>";
  private FieldPolicy policy = FieldPolicy.from(1, 0);
  private LinearLayout keyboard, suggestions, precision;
  private HorizontalScrollView suggestionScroll;
  private FrameLayout container;
  private View navigationBackdrop;
  private CompactStrip letterStrip;
  private AccentPopup accentPopup;
  private KeyboardColors colors;

  private enum KeyRole {
    INPUT,
    ACTION,
    SUGGESTION,
    SPACE
  }

  private boolean capsLock;
  private long lastShiftTap;
  private LinearLayout emojiResults;
  private android.widget.EditText emojiSearch;
  private final StringBuilder emojiQuery = new StringBuilder();
  private int navigationBottom;
  private final android.database.ContentObserver systemThemeChanges =
      new android.database.ContentObserver(
          new android.os.Handler(android.os.Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
          if (preferences != null && preferences.number("theme", 2) == 2) renderKeyboard();
        }
      };

  private final StringBuilder composingText = new StringBuilder();
  private final List<Double> touchPositions = new ArrayList<>();
  private List<Decoder.Candidate> candidates = java.util.Collections.emptyList();
  private boolean shift, emoji, preciseWord;
  private KeyboardMode mode = KeyboardMode.MINI;
  private String undoRendered = "", undoRaw = "";
  private long lastSpace;
  private int undoCursor = -1, lastSpaceCursor = -1;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener themeChanges =
      (preferences, key) -> {
        if ("dark".equals(key) || "theme".equals(key)) renderKeyboard();
      };

  @Override
  public void onCreate() {
    setTheme(R.style.KeyboardTheme);
    super.onCreate();
    preferences = new Preferences(this);
    colors = new KeyboardColors(preferences.isDark());
    words = new WordStore(this);
    reload();
    new Thread(
            () -> {
              var loaded = org.linekey.core.LanguageModel.get();
              new android.os.Handler(android.os.Looper.getMainLooper())
                  .post(
                      () -> {
                        if (destroyed) return;
                        languageModel = loaded;
                        if (composingText.length() > 0) refreshCandidates();
                        updateSuggestions();
                      });
            },
            "LineKey model loader")
        .start();
    preferences.store.registerOnSharedPreferenceChangeListener(themeChanges);
    getContentResolver()
        .registerContentObserver(
            android.provider.Settings.Secure.getUriFor("ui_night_mode"), false, systemThemeChanges);
  }

  @Override
  public void onDestroy() {
    destroyed = true;
    preferences.store.unregisterOnSharedPreferenceChangeListener(themeChanges);
    getContentResolver().unregisterContentObserver(systemThemeChanges);
    super.onDestroy();
  }

  private void reload() {
    decoder = new Decoder();
    words.load(decoder);
    layout = new LetterLayout(preferences.number("layout", 0));
  }

  @Override
  public View onCreateInputView() {
    container = new FrameLayout(this);
    container.setPadding(0, 0, 0, dp(48));
    container.setOnApplyWindowInsetsListener(
        (view, insets) -> {
          navigationBottom =
              android.os.Build.VERSION.SDK_INT >= 30
                  ? insets.getInsets(
                          android.view.WindowInsets.Type.navigationBars()
                              | android.view.WindowInsets.Type.captionBar()
                              | android.view.WindowInsets.Type.systemGestures())
                      .bottom
                  : insets.getSystemWindowInsetBottom();
          view.setPadding(0, 0, 0, Math.max(dp(48), navigationBottom));
          updateNavigationBackdrop();
          return insets;
        });
    renderKeyboard();
    return container;
  }

  @Override
  public void onComputeInsets(Insets out) {
    super.onComputeInsets(out);
    if (container == null) return;
    int[] location = new int[2];
    container.getLocationInWindow(location);
    if (floating && keyboard != null) {
      out.contentTopInsets = getWindow().getWindow().getDecorView().getHeight();
      out.visibleTopInsets = out.contentTopInsets;
      int[] panelLocation = new int[2];
      keyboard.getLocationInWindow(panelLocation);
      out.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
      out.touchableRegion.set(
          panelLocation[0],
          panelLocation[1],
          panelLocation[0] + keyboard.getWidth(),
          panelLocation[1] + keyboard.getHeight());
      out.touchableRegion.union(
          new android.graphics.Rect(
              0,
              location[1] + container.getHeight() - container.getPaddingBottom(),
              container.getWidth(),
              location[1] + container.getHeight()));
      return;
    }
    out.contentTopInsets = location[1];
    out.visibleTopInsets = location[1];
    out.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT;
  }

  @Override
  public boolean onEvaluateFullscreenMode() {
    return false;
  }

  @Override
  public void onStartInput(EditorInfo info, boolean restarting) {
    super.onStartInput(info, restarting);
    resetComposition();
    capsLock = false;
    toolPanel = "";
    selecting = false;
    floating = preferences.bool("floating", false);
    undoRendered = "";
    lastSpace = 0;
    policy = FieldPolicy.from(info.inputType, info.imeOptions);
    reload();
    mode =
        policy.numeric() || policy.phone()
            ? KeyboardMode.NUMPAD
            : preferences.bool("compact", true) && allowMini()
                ? KeyboardMode.MINI
                : KeyboardMode.ALPHABET;
    emoji = false;
    emojiQuery.setLength(0);
    shift =
        !policy.literal()
            && preferences.bool("caps", true)
            && getCurrentInputConnection() != null
            && getCurrentInputConnection().getCursorCapsMode(info.inputType) != 0;
  }

  @Override
  public void onStartInputView(EditorInfo info, boolean restarting) {
    super.onStartInputView(info, restarting);
    if (composingText.length() == 0
        && !capsLock
        && !policy.literal()
        && preferences.bool("caps", true)
        && getCurrentInputConnection() != null)
      shift = getCurrentInputConnection().getCursorCapsMode(info.inputType) != 0;
    renderKeyboard();
  }

  @Override
  public void onFinishInput() {
    resetComposition();
    undoRendered = "";
    lastSpace = 0;
    dismissAccents();
    super.onFinishInput();
  }

  @Override
  public void onUpdateSelection(
      int oldStart, int oldEnd, int newStart, int newEnd, int candidatesStart, int candidatesEnd) {
    super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
    if (composingText.length() > 0 && (newStart != newEnd || newStart != candidatesEnd)) {
      if (getCurrentInputConnection() != null) getCurrentInputConnection().finishComposingText();
      resetComposition();
      undoRendered = "";
      lastSpace = 0;
      updateSuggestions();
    } else if (composingText.length() == 0) {
      if (undoCursor >= 0 && (newStart != undoCursor || newEnd != undoCursor)) undoRendered = "";
      if (lastSpaceCursor >= 0 && (newStart != lastSpaceCursor || newEnd != lastSpaceCursor))
        lastSpace = 0;
      updateSuggestions();
    }
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  @Override
  public void onConfigurationChanged(android.content.res.Configuration configuration) {
    super.onConfigurationChanged(configuration);
    renderKeyboard();
  }

  private boolean compactSurface() {
    return mode == KeyboardMode.MINI && !emoji;
  }

  private int textColor() {
    return colors.text;
  }

  private void renderKeyboard() {
    if (container == null) return;
    android.view.Window window = getWindow().getWindow();
    if (window != null) {
      View decor = window.getDecorView();
      if (android.os.Build.VERSION.SDK_INT >= 30 && decor.getWindowInsetsController() != null) {
        decor
            .getWindowInsetsController()
            .setSystemBarsAppearance(
                preferences.isDark()
                    ? 0
                    : android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
      } else {
        int appearance = decor.getSystemUiVisibility();
        decor.setSystemUiVisibility(
            preferences.isDark()
                ? appearance & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : appearance | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
      }
    }
    dismissAccents();
    container.removeAllViews();
    navigationBackdrop = null;
    container.setMinimumHeight(floating ? dp(400) : 0);
    if (getWindow().getWindow() != null)
      getWindow()
          .getWindow()
          .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    letterStrip = null;
    colors = new KeyboardColors(preferences.isDark());
    container.setBackgroundColor(colors.surface);
    keyboard = new LinearLayout(this);
    keyboard.setOrientation(LinearLayout.VERTICAL);
    keyboard.setBackgroundColor(colors.surface);
    if (floating) container.setBackgroundColor(Color.TRANSPARENT);
    keyboard.setPadding(dp(compactSurface() ? 3 : 4), dp(2), dp(compactSurface() ? 3 : 4), dp(4));
    int position = preferences.number("position", 0);
    int width =
        position == 0
            ? FrameLayout.LayoutParams.MATCH_PARENT
            : (int) (getResources().getDisplayMetrics().widthPixels * .78);
    FrameLayout.LayoutParams box =
        new FrameLayout.LayoutParams(
            width,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            position == 1
                ? Gravity.START
                : position == 2 ? Gravity.END : Gravity.CENTER_HORIZONTAL);
    if (floating) {
      box.width = Math.min(dp(320), (int) (getResources().getDisplayMetrics().widthPixels * .82));
      box.gravity = Gravity.BOTTOM | Gravity.LEFT;
      box.leftMargin = floatingX;
      box.bottomMargin = floatingLift;
    }
    if (floating) {
      container.setClipToPadding(false);
      navigationBackdrop = new View(this);
      navigationBackdrop.setBackgroundColor(colors.surface);
      container.addView(navigationBackdrop);
      updateNavigationBackdrop();
      keyboard.addOnLayoutChangeListener(
          (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int x =
                Math.max(0, Math.min(box.leftMargin, container.getWidth() - keyboard.getWidth()));
            int lift =
                Math.max(
                    0,
                    Math.min(
                        box.bottomMargin,
                        container.getHeight()
                            - container.getPaddingBottom()
                            - keyboard.getHeight()));
            if (box.leftMargin != x || box.bottomMargin != lift) {
              floatingX = box.leftMargin = x;
              floatingLift = box.bottomMargin = lift;
              keyboard.setLayoutParams(box);
            }
          });
    } else container.setClipToPadding(true);
    container.addView(keyboard, box);
    if (floating) addFloatingHandle(box);
    LinearLayout candidateBar = addRow();
    if (compactSurface()) addShiftButton(candidateBar, 1);
    suggestions = newKeyRow();
    suggestions.setOrientation(LinearLayout.HORIZONTAL);
    suggestionScroll = new HorizontalScrollView(this);
    suggestionScroll.setContentDescription("Word suggestions. Swipe to see more matches.");
    suggestionScroll.setHorizontalScrollBarEnabled(false);
    suggestionScroll.addView(suggestions, new HorizontalScrollView.LayoutParams(-2, -2));
    candidateBar.addView(suggestionScroll, new LinearLayout.LayoutParams(0, -2, 5));
    if (compactSurface()) addDeleteButton(candidateBar, 1);
    if (policy.voiceAllowed()) {
      addIconButton(candidateBar, R.drawable.ic_control_mic, "Voice input", this::voiceInput, .7f);
    }
    if (!VoiceDraft.get().isEmpty() && policy.voiceAllowed()) {
      TextView draft = new TextView(this);
      draft.setText(VoiceDraft.get());
      draft.setMaxLines(2);
      draft.setTextColor(textColor());
      draft.setTypeface(KeyboardFont.get(this));
      keyboard.addView(draft);
      LinearLayout review = addRow();
      addPanelButton(
          review,
          "Insert dictation",
          () -> {
            finishWord(false, false);
            commit(VoiceDraft.get());
            VoiceDraft.clear();
            renderKeyboard();
          },
          2);
      addPanelButton(
          review,
          "Discard",
          () -> {
            VoiceDraft.clear();
            renderKeyboard();
          },
          1);
    }
    if (!toolPanel.isEmpty()) addToolPanel();
    precision = addRow();
    precision.setVisibility(View.GONE);
    if (!emoji && mode == KeyboardMode.NUMPAD) {
      addNumberPad();
      updateSuggestions();
      return;
    }
    if (!emoji && (mode == KeyboardMode.NUMBERS || mode == KeyboardMode.SYMBOLS)) {
      addSymbolPage();
      updateSuggestions();
      return;
    }
    if (emoji) {
      emojiSearch = new android.widget.EditText(this);
      emojiSearch.setSingleLine(true);
      emojiSearch.setTypeface(KeyboardFont.get(this));
      emojiSearch.setHint("Search emoji…");
      emojiSearch.setHintTextColor(textColor());
      emojiSearch.setShowSoftInputOnFocus(false);
      emojiSearch.setFilters(
          new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(40)});
      emojiSearch.addTextChangedListener(
          new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable text) {
              if (!text.toString().contentEquals(emojiQuery)) {
                emojiQuery.setLength(0);
                emojiQuery.append(text);
                updateEmojiResults();
              }
            }
          });
      emojiSearch.setTextColor(textColor());
      emojiSearch.setTextSize(16);
      emojiSearch.setPadding(dp(12), dp(8), dp(12), dp(8));
      emojiSearch.setContentDescription("Emoji search query");
      keyboard.addView(emojiSearch);
      emojiResults = new LinearLayout(this);
      emojiResults.setOrientation(LinearLayout.VERTICAL);
      android.widget.ScrollView resultsScroll = new android.widget.ScrollView(this);
      resultsScroll.setFillViewport(true);
      resultsScroll.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
      emojiResults.setMinimumHeight(dp(112));
      resultsScroll.addView(emojiResults);
      keyboard.addView(resultsScroll, new LinearLayout.LayoutParams(-1, dp(112)));
      updateEmojiResults();
      // Query input belongs to this IME; it never changes the application's editor focus.
      addLetterRows(true);
    } else if (mode == KeyboardMode.MINI) {
      CompactStrip strip = new CompactStrip(this, layout, this, preferences);
      letterStrip = strip;
      strip.setUppercase(shift);
      keyboard.addView(
          strip, new LinearLayout.LayoutParams(-1, dp(preferences.number("mini_height", 48))));
    } else {
      addLetterRows(false);
    }
    LinearLayout actions = addRow();
    addModeButton(actions);
    if (!policy.privateField())
      addIconButton(
          actions,
          R.drawable.ic_control_sentiment_satisfied,
          "Emoji menu",
          () -> {
            finishWord(false, false);
            emoji = !emoji;
            mode = KeyboardMode.ALPHABET;
            emojiQuery.setLength(0);
            renderKeyboard();
          },
          1);
    addButton(
        actions,
        ",",
        () -> {
          finishWord(false, true);
          if (emoji) editEmojiQuery(",");
          else commit(",");
        },
        1);
    Button spaceKey = addButton(actions, "Space", this::space, 3);
    enableCursorSwipe(spaceKey);
    addButton(
        actions,
        ".",
        () -> {
          finishWord(false, true);
          if (emoji) editEmojiQuery(".");
          else commit(".");
        },
        1);
    addEnterButton(actions);
    updateSuggestions();
  }

  /** Ten equal columns, with proportional spacers that also fit floating and one-handed panels. */
  private void addLetterRows(boolean query) {
    String[] rows = layout.rows();
    for (int index = 0; index < rows.length; index++) {
      String letters = rows[index];
      LinearLayout row = addRow();
      boolean bottom = index == rows.length - 1;
      float gutter = (10 - letters.length() - (bottom ? 3 : 0)) / 2f;
      if (bottom) {
        if (query) addKeySpacer(row, 1.5f);
        else addShiftButton(row, 1.5f);
      }
      addKeySpacer(row, gutter);
      for (char letter : letters.toCharArray()) {
        if (query)
          addButton(row, String.valueOf(letter), () -> searchLetter(letter), 1, KeyRole.INPUT);
        else addLetterButton(row, letter);
      }
      addKeySpacer(row, gutter);
      if (bottom) addDeleteButton(row, 1.5f);
      row.addOnLayoutChangeListener(
          (view, left, top, right, bottomEdge, oldLeft, oldTop, oldRight, oldBottom) ->
              fitLetterLabels(row));
    }
  }

  /**
   * Keep one readable size for every letter, including W/M and uppercase, within the actual panel.
   */
  private void fitLetterLabels(LinearLayout row) {
    if (row.getWidth() <= 0) return;
    android.graphics.Paint measure = new android.graphics.Paint();
    measure.setTypeface(KeyboardFont.get(this));
    float normalPixels =
        android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 22, getResources().getDisplayMetrics());
    measure.setTextSize(normalPixels);
    float widest = 0;
    for (char letter : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray())
      widest = Math.max(widest, measure.measureText(String.valueOf(letter)));
    int padding = 0;
    for (int index = 0; index < row.getChildCount(); index++) {
      if (row.getChildAt(index) instanceof Button key)
        padding = Math.max(padding, key.getCompoundPaddingLeft() + key.getCompoundPaddingRight());
    }
    float available = Math.max(1, row.getWidth() / 10f - padding - dp(2));
    float pixels = normalPixels * Math.min(1, available / widest);
    for (int index = 0; index < row.getChildCount(); index++) {
      if (row.getChildAt(index) instanceof Button key
          && Math.abs(key.getTextSize() - pixels) > .25f)
        key.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, pixels);
    }
  }

  private void addKeySpacer(LinearLayout row, float columns) {
    if (columns > 0) row.addView(new View(this), new LinearLayout.LayoutParams(0, 0, columns));
  }

  private void addModeButton(LinearLayout row) {
    addModeButton(row, 1.2f);
  }

  private void addModeButton(LinearLayout row, float weight) {
    Button modes = addButton(row, modeLabel(), this::cycleMode, weight, KeyRole.ACTION);
    modes.setContentDescription(
        "Keyboard mode. "
            + (emoji ? "Emoji" : mode.name())
            + ". Next: "
            + modeLabel()
            + ". Hold for tools and settings.");
    if (policy.numeric() || policy.phone()) {
      modes.setContentDescription("Keyboard mode. Open tools and settings.");
      modes.setTextSize(20);
      modes.setOnClickListener(this::showModes);
    }
    modes.setOnLongClickListener(
        view -> {
          showModes(view);
          return true;
        });
  }

  private void addEnterButton(LinearLayout row) {
    addEnterButton(row, 1.3f);
  }

  private void addEnterButton(LinearLayout row, float weight) {
    IconButton enterKey = addIconButton(row, actionIcon(), actionLabel(), this::enter, weight);
    enterKey.setBackgroundTintList(android.content.res.ColorStateList.valueOf(colors.accent));
    enterKey.setIcon(actionIcon(), colors.accentText, false);
  }

  private void selectMode(KeyboardMode destination) {
    finishWord(false, false);
    mode = destination;
    emoji = false;
    renderKeyboard();
  }

  private void addSymbolPage() {
    boolean secondary = mode == KeyboardMode.SYMBOLS;
    String[] rows = org.linekey.core.SymbolLayout.rows(secondary);
    for (int index = 0; index < rows.length; index++) {
      LinearLayout row = addRow();
      if (index == 2)
        addButton(
            row,
            secondary ? "?123" : "=\\<",
            () -> selectMode(secondary ? KeyboardMode.NUMBERS : KeyboardMode.SYMBOLS),
            1.5f,
            KeyRole.ACTION);
      for (char character : rows[index].toCharArray())
        addButton(
            row,
            String.valueOf(character),
            () -> commit(String.valueOf(character)),
            1,
            KeyRole.INPUT);
      if (index == 2) addDeleteButton(row, 1.5f);
    }
    LinearLayout actions = addRow();
    addModeButton(actions, 1.5f);
    addButton(
        actions, secondary ? "<" : ",", () -> commit(secondary ? "<" : ","), 1, KeyRole.ACTION);
    Button pad =
        addButton(actions, "12\n34", () -> selectMode(KeyboardMode.NUMPAD), 1.2f, KeyRole.ACTION);
    pad.setSingleLine(false);
    pad.setTextSize(11);
    pad.setContentDescription("Number pad");
    Button space = addButton(actions, "Space", this::space, 3.8f);
    enableCursorSwipe(space);
    addButton(
        actions, secondary ? ">" : ".", () -> commit(secondary ? ">" : "."), 1, KeyRole.ACTION);
    addEnterButton(actions, 1.5f);
  }

  private void addNumberPad() {
    LinearLayout body = addRow();
    if (policy.phone()) {
      LinearLayout dialing = new LinearLayout(this);
      dialing.setOrientation(LinearLayout.VERTICAL);
      body.addView(dialing, new LinearLayout.LayoutParams(0, -2, 1.4f));
      for (char character : org.linekey.core.SymbolLayout.operators(true).toCharArray())
        addPadColumnButton(dialing, String.valueOf(character));
    } else {
      android.widget.ScrollView operators = new android.widget.ScrollView(this);
      operators.setContentDescription("Number pad operators");
      operators.setVerticalScrollBarEnabled(true);
      operators.setScrollbarFadingEnabled(false);
      android.graphics.drawable.GradientDrawable background =
          new android.graphics.drawable.GradientDrawable();
      background.setColor(colors.action);
      background.setCornerRadius(dp(10));
      operators.setBackground(background);
      operators.setClipToOutline(true);
      FrameLayout operatorColumn = new FrameLayout(this);
      operatorColumn.setPadding(dp(2), dp(4), dp(2), dp(4));
      body.addView(operatorColumn, new LinearLayout.LayoutParams(0, -2, 1.4f));
      operatorColumn.addView(
          operators, new FrameLayout.LayoutParams(-1, dp(3 * fullKeyHeight() - 8)));
      LinearLayout column = new LinearLayout(this);
      column.setOrientation(LinearLayout.VERTICAL);
      operators.addView(column);
      for (char character : org.linekey.core.SymbolLayout.operators(policy.phone()).toCharArray()) {
        Button key =
            addButton(
                column,
                String.valueOf(character),
                () -> commit(String.valueOf(character)),
                1,
                KeyRole.ACTION);
        key.setTextSize(20);
        key.setBackground(
            new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(colors.outline), null, null));
        key.setLayoutParams(new LinearLayout.LayoutParams(-1, dp((3 * fullKeyHeight() - 8) / 4)));
      }
    }
    LinearLayout digits = new LinearLayout(this);
    digits.setOrientation(LinearLayout.VERTICAL);
    body.addView(digits, new LinearLayout.LayoutParams(0, -2, 7.2f));
    for (String letters : new String[] {"123", "456", "789"}) {
      LinearLayout row = newKeyRow();
      digits.addView(row, new LinearLayout.LayoutParams(-1, -2));
      for (char digit : letters.toCharArray()) {
        Button key =
            addButton(
                row, String.valueOf(digit), () -> commit(String.valueOf(digit)), 1, KeyRole.INPUT);
        key.setTextSize(26);
      }
    }
    LinearLayout right = new LinearLayout(this);
    right.setOrientation(LinearLayout.VERTICAL);
    body.addView(right, new LinearLayout.LayoutParams(0, -2, 1.4f));
    Button percent =
        addButton(
            right,
            policy.phone() ? "(" : "%",
            () -> commit(policy.phone() ? "(" : "%"),
            1,
            KeyRole.ACTION);
    percent.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(fullKeyHeight())));
    if (policy.phone()) addPadColumnButton(right, ")");
    else {
      IconButton space =
          addIconButton(right, R.drawable.ic_control_space_bar, "Space", this::space, 1);
      space.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(fullKeyHeight())));
      enableCursorSwipe(space);
    }
    addDeleteButton(right, 1);
    right
        .getChildAt(right.getChildCount() - 1)
        .setLayoutParams(new LinearLayout.LayoutParams(-1, dp(fullKeyHeight())));
    LinearLayout bottom = addRow();
    addModeButton(bottom, 1.4f);
    LinearLayout middle = newKeyRow();
    bottom.addView(middle, new LinearLayout.LayoutParams(0, -2, 7.2f));
    if (policy.phone()) {
      for (char character : "*0#".toCharArray()) {
        Button key =
            addButton(
                middle,
                String.valueOf(character),
                () -> commit(String.valueOf(character)),
                2.4f,
                KeyRole.INPUT);
        key.setTextSize(26);
      }
    } else {
      addButton(middle, ",", () -> commit(","), 1.1f, KeyRole.ACTION);
      addButton(middle, "!?#", () -> selectMode(KeyboardMode.NUMBERS), 1.3f, KeyRole.ACTION);
      Button zero = addButton(middle, "0", () -> commit("0"), 2.4f, KeyRole.INPUT);
      zero.setTextSize(26);
      addButton(middle, "=", () -> commit("="), 1.3f, KeyRole.INPUT);
      addButton(middle, ".", () -> commit("."), 1.1f, KeyRole.ACTION);
    }
    addEnterButton(bottom, 1.4f);
  }

  private void addPadColumnButton(LinearLayout column, String label) {
    Button key = addButton(column, label, () -> commit(label), 1, KeyRole.ACTION);
    key.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(fullKeyHeight())));
  }

  private int fullKeyHeight() {
    return Math.max(52, preferences.number("key_height", 48));
  }

  private boolean allowMini() {
    return !policy.literal() && !policy.privateField();
  }

  private String modeLabel() {
    if (policy.numeric() || policy.phone()) return "⋯";
    return emoji ? "ABC" : mode.nextLabel(allowMini());
  }

  private void cycleMode() {
    if (policy.numeric() || policy.phone()) return;
    finishWord(false, false);
    mode = emoji ? KeyboardMode.ALPHABET : mode.next(allowMini());
    emoji = false;
    renderKeyboard();
  }

  private IconButton addIconButton(
      LinearLayout row, int icon, String description, Runnable action, float weight) {
    IconButton key = new IconButton(this);
    styleKey(key, row, action, weight, KeyRole.ACTION);
    key.setPadding(dp(4), dp(4), dp(4), dp(4));
    key.setContentDescription(description);
    key.setIcon(icon, colors.text, false);
    return key;
  }

  private void addShiftButton(LinearLayout row, float weight) {
    IconButton key =
        addIconButton(row, R.drawable.ic_control_shift_outline, "Shift", this::toggleShift, weight);
    key.setTag("shift");
    key.setOnLongClickListener(
        view -> {
          capsLock = true;
          shift = true;
          updateCase();
          updateSuggestions();
          return true;
        });
    updateShiftIcon(key);
  }

  private void updateShiftIcon(IconButton key) {
    key.setSelected(shift);
    key.setBackgroundTintList(
        shift ? android.content.res.ColorStateList.valueOf(colors.accent) : null);
    key.setIcon(
        shift ? R.drawable.ic_control_shift_filled : R.drawable.ic_control_shift_outline,
        shift ? colors.accentText : colors.text,
        capsLock);
    key.setContentDescription(
        "Shift. " + (capsLock ? "Caps lock on" : shift ? "Uppercase" : "Lowercase"));
    if (android.os.Build.VERSION.SDK_INT >= 30)
      key.setStateDescription(capsLock ? "Caps lock" : shift ? "On" : "Off");
  }

  private void addDeleteButton(LinearLayout row, float weight) {
    IconButton key =
        addIconButton(
            row,
            R.drawable.ic_control_backspace,
            "Delete. Hold to delete the previous word.",
            this::backspace,
            weight);
    key.setOnLongClickListener(
        view -> {
          deleteWord();
          return true;
        });
  }

  private int actionIcon() {
    if (emoji) return R.drawable.ic_control_search;
    EditorInfo info = getCurrentInputEditorInfo();
    if (info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0)
      return R.drawable.ic_control_keyboard_return;
    return switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
      case EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_control_search;
      case EditorInfo.IME_ACTION_DONE -> R.drawable.ic_control_check;
      case EditorInfo.IME_ACTION_SEND -> R.drawable.ic_control_send;
      case EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS, EditorInfo.IME_ACTION_GO ->
          R.drawable.ic_control_arrow_forward;
      default -> R.drawable.ic_control_keyboard_return;
    };
  }

  private void showModes(View anchor) {
    PopupMenu menu = new PopupMenu(MaterialUi.themed(this, preferences.isDark()), anchor);
    if (!policy.literal() && !policy.privateField())
      menu.getMenu().add(mode == KeyboardMode.MINI ? "Full" : "Mini");
    menu.getMenu().add(floating ? "Dock keyboard" : "Float keyboard");
    menu.getMenu().add("Cursor panel");
    if (!policy.privateField()) {
      menu.getMenu().add("Clipboard panel");
      if (policy.voiceAllowed()) menu.getMenu().add("Voice input");
    }
    menu.getMenu().add("Settings");
    menu.getMenu().add("Change keyboard");
    menu.setOnMenuItemClickListener(
        item -> {
          String label = item.getTitle().toString();
          if (label.equals("Float keyboard") || label.equals("Dock keyboard")) {
            floating = !floating;
            preferences.set("floating", floating);
            renderKeyboard();
          } else if (label.equals("Cursor panel") || label.equals("Clipboard panel")) {
            toolPanel = label.equals("Cursor panel") ? "cursor" : "clipboard";
            renderKeyboard();
          } else if (label.equals("Voice input")) {
            voiceInput();
          } else if (label.equals("Full") || label.equals("Mini")) {
            finishWord(false, false);
            mode = label.equals("Full") ? KeyboardMode.ALPHABET : KeyboardMode.MINI;
            emoji = false;
            renderKeyboard();
          } else if (label.equals("Settings")) {
            startActivity(
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
          } else
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
          return true;
        });
    menu.show();
  }

  @Override
  public void onWindowShown() {
    super.onWindowShown();
    renderKeyboard();
  }

  private void voiceInput() {
    if (!policy.voiceAllowed()) return;
    finishWord(false, false);
    startActivity(
        new Intent(this, VoiceInputActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  private void updateNavigationBackdrop() {
    if (navigationBackdrop == null) return;
    FrameLayout.LayoutParams bounds =
        new FrameLayout.LayoutParams(-1, container.getPaddingBottom(), Gravity.BOTTOM);
    bounds.bottomMargin = -container.getPaddingBottom();
    navigationBackdrop.setLayoutParams(bounds);
  }

  private void addFloatingHandle(FrameLayout.LayoutParams box) {
    LinearLayout handle = addRow();
    Button drag = addPanelButton(handle, "Drag", () -> {}, 2);
    drag.setContentDescription("Move floating keyboard");
    addPanelButton(
        handle,
        "Dock",
        () -> {
          floating = false;
          preferences.set("floating", false);
          renderKeyboard();
        },
        1);
    float[] origin = new float[2];
    int[] start = new int[2];
    drag.setOnTouchListener(
        (view, event) -> {
          if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            origin[0] = event.getRawX();
            origin[1] = event.getRawY();
            start[0] = box.leftMargin;
            start[1] = box.bottomMargin;
          } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
            box.leftMargin =
                Math.max(
                    0,
                    Math.min(
                        container.getWidth() - keyboard.getWidth(),
                        start[0] + (int) (event.getRawX() - origin[0])));
            box.bottomMargin =
                Math.max(
                    0,
                    Math.min(
                        container.getHeight() - container.getPaddingBottom() - keyboard.getHeight(),
                        start[1] - (int) (event.getRawY() - origin[1])));
            keyboard.setLayoutParams(box);
            floatingX = box.leftMargin;
            floatingLift = box.bottomMargin;
          }
          return true;
        });
  }

  private void cursorKey(int code) {
    if (getCurrentInputConnection() == null) return;
    finishWord(false, false);
    int meta = selecting ? KeyEvent.META_SHIFT_ON : 0;
    long time = android.os.SystemClock.uptimeMillis();
    getCurrentInputConnection()
        .sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, meta));
    getCurrentInputConnection()
        .sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0, meta));
  }

  private void editorAction(int action) {
    if (policy.privateField() && action != android.R.id.selectAll) return;
    if (getCurrentInputConnection() != null) {
      finishWord(false, false);
      getCurrentInputConnection().performContextMenuAction(action);
    }
  }

  private void enableCursorSwipe(View spaceKey) {
    spaceKey.setContentDescription("Space. Swipe horizontally to move the cursor.");
    float[] last = new float[1];
    boolean[] moved = new boolean[1];
    spaceKey.setOnTouchListener(
        (view, event) -> {
          int action = event.getActionMasked();
          if (action == android.view.MotionEvent.ACTION_DOWN) {
            last[0] = event.getRawX();
            moved[0] = false;
          } else if (action == android.view.MotionEvent.ACTION_MOVE) {
            int steps = (int) ((event.getRawX() - last[0]) / dp(12));
            if (steps != 0) {
              moved[0] = true;
              last[0] += steps * dp(12);
              for (int index = 0; index < Math.min(12, Math.abs(steps)); index++) {
                if (emoji) {
                  int position = Math.max(0, emojiSearch.getSelectionStart());
                  if (steps < 0 && position > 0 || steps > 0 && position < emojiSearch.length())
                    emojiSearch.setSelection(
                        Character.offsetByCodePoints(
                            emojiSearch.getText(), position, steps < 0 ? -1 : 1));
                } else
                  cursorKey(steps < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
              }
            }
          } else if (action == android.view.MotionEvent.ACTION_UP && !moved[0]) view.performClick();
          return true;
        });
  }

  private void addToolPanel() {
    android.content.Context context = MaterialUi.themed(this, preferences.isDark());
    var card = MaterialUi.card(context);
    LinearLayout panel = MaterialUi.column(context);
    panel.setPadding(dp(8), dp(4), dp(8), dp(8));
    card.addView(panel);
    keyboard.addView(card, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout title = panelRow(panel);
    TextView caption = new MaterialTextView(context);
    caption.setText(toolPanel.equals("clipboard") ? "Clipboard" : "Cursor");
    caption.setTextSize(18);
    caption.setPadding(dp(8), dp(8), dp(8), dp(8));
    title.addView(caption, new LinearLayout.LayoutParams(0, -2, 2));
    addPanelButton(
        title,
        "Close panel",
        () -> {
          toolPanel = "";
          selecting = false;
          renderKeyboard();
        },
        1);
    if (toolPanel.equals("cursor")) {
      LinearLayout arrows = panelRow(panel);
      addPanelButton(arrows, "←", () -> cursorKey(KeyEvent.KEYCODE_DPAD_LEFT), 1);
      addPanelButton(arrows, "↑", () -> cursorKey(KeyEvent.KEYCODE_DPAD_UP), 1);
      addPanelButton(arrows, "↓", () -> cursorKey(KeyEvent.KEYCODE_DPAD_DOWN), 1);
      addPanelButton(arrows, "→", () -> cursorKey(KeyEvent.KEYCODE_DPAD_RIGHT), 1);
      LinearLayout edits = panelRow(panel);
      addPanelButton(
          edits,
          selecting ? "Selecting" : "Select",
          () -> {
            selecting = !selecting;
            renderKeyboard();
          },
          1);
      addPanelButton(edits, "Select all", () -> editorAction(android.R.id.selectAll), 1);
      if (!policy.privateField()) {
        LinearLayout clipboardActions = panelRow(panel);
        addPanelButton(clipboardActions, "Copy", () -> editorAction(android.R.id.copy), 1);
        addPanelButton(clipboardActions, "Cut", () -> editorAction(android.R.id.cut), 1);
        addPanelButton(clipboardActions, "Paste", () -> editorAction(android.R.id.paste), 1);
      }
    } else if (!policy.privateField()) {
      String current = clipboard.read(this);
      LinearLayout actions = panelRow(panel);
      addPanelButton(
          actions,
          "Paste current",
          () -> {
            finishWord(false, false);
            commit(current);
          },
          2);
      addPanelButton(
          actions,
          "Clear history",
          () -> {
            clipboard.clear();
            toolPanel = "";
            renderKeyboard();
          },
          1);
      android.widget.ScrollView scroll = new android.widget.ScrollView(context);
      LinearLayout clips = new LinearLayout(context);
      clips.setOrientation(LinearLayout.VERTICAL);
      scroll.addView(clips);
      panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(96)));
      for (String text : clipboard.history()) {
        LinearLayout row = new LinearLayout(context);
        clips.addView(row);
        Button clip =
            addPanelButton(
                row,
                text.length() > 60 ? text.substring(0, 60) + "…" : text,
                () -> {
                  finishWord(false, false);
                  commit(text);
                },
                1);
        clip.setContentDescription("Paste saved clipboard text");
      }
    }
  }

  private LinearLayout panelRow(LinearLayout panel) {
    LinearLayout row = new LinearLayout(panel.getContext());
    row.setBaselineAligned(false);
    row.setGravity(Gravity.CENTER_VERTICAL);
    panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
    return row;
  }

  private Button addPanelButton(LinearLayout row, String caption, Runnable action, float weight) {
    var button =
        MaterialUi.addButton(MaterialUi.themed(this, preferences.isDark()), caption, action);
    row.addView(button, new LinearLayout.LayoutParams(0, dp(48), weight));
    return button;
  }

  /** Key edges align geometrically, never by glyph baselines or multiline label baselines. */
  private LinearLayout newKeyRow() {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setBaselineAligned(false);
    row.setGravity(Gravity.TOP);
    return row;
  }

  private LinearLayout addRow() {
    LinearLayout row = newKeyRow();
    row.setOrientation(LinearLayout.HORIZONTAL);
    if (keyboard != null) keyboard.addView(row, new LinearLayout.LayoutParams(-1, -2));
    return row;
  }

  private Button addButton(LinearLayout parent, String label, Runnable action, float weight) {
    KeyRole role =
        label.equals("Space")
            ? KeyRole.SPACE
            : label.matches("[a-zA-Z0-9\\p{Punct}]") ? KeyRole.INPUT : KeyRole.ACTION;
    return addButton(parent, label, action, weight, role);
  }

  private Button addButton(
      LinearLayout parent, String label, Runnable action, float weight, KeyRole role) {
    return configureButton(new Button(this), parent, label, action, weight, role);
  }

  private Button configureButton(
      Button button,
      LinearLayout parent,
      String label,
      Runnable action,
      float weight,
      KeyRole role) {
    button.setText(label);
    button.setAllCaps(false);
    button.setSingleLine(true);
    button.setEllipsize(android.text.TextUtils.TruncateAt.END);
    boolean letterKey = label.matches("[a-zA-Z]");
    button.setTypeface(KeyboardFont.get(this));
    button.setLetterSpacing(0);
    button.setFontFeatureSettings("kern");
    button.setTextSize(!compactSurface() && letterKey ? 22 : 14);
    button.setIncludeFontPadding(false);
    button.setGravity(Gravity.CENTER);
    if (role == KeyRole.ACTION
        && label.length() > 1
        && label.chars().allMatch(character -> character < 128)) {
      // Single-line TextViews otherwise measure against a virtual scrolling width and never shrink.
      button.setHorizontallyScrolling(false);
      button.setAutoSizeTextTypeUniformWithConfiguration(
          10, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
    }
    button.setTextColor(textColor());
    button.setMinHeight(0);
    button.setMinimumHeight(0);
    button.setMinWidth(0);
    button.setMinimumWidth(0);
    button.setPadding(0, 0, 0, 0);
    styleKey(button, parent, action, weight, role);
    return button;
  }

  private void styleKey(
      View button, LinearLayout parent, Runnable action, float weight, KeyRole role) {
    button.setMinimumWidth(0);
    button.setMinimumHeight(0);
    android.graphics.drawable.GradientDrawable background =
        new android.graphics.drawable.GradientDrawable();
    boolean accent = role == KeyRole.SPACE;
    background.setColor(
        accent
            ? colors.accent
            : role == KeyRole.ACTION
                ? colors.action
                : role == KeyRole.SUGGESTION ? colors.surface : colors.key);
    if (role == KeyRole.ACTION) background.setStroke(dp(1), colors.outline);
    background.setCornerRadius(dp(compactSurface() ? 6 : 10));
    int verticalInset = dp(compactSurface() ? 2 : 4);
    button.setBackground(
        new android.graphics.drawable.InsetDrawable(
            background, dp(2), verticalInset, dp(2), verticalInset));
    button.setStateListAnimator(null);
    if (accent && button instanceof Button text) text.setTextColor(colors.accentText);
    parent.addView(
        button,
        new LinearLayout.LayoutParams(
            0,
            dp(compactSurface() ? 36 : Math.max(52, preferences.number("key_height", 48))),
            weight));
    button.setOnClickListener(
        v -> {
          feedback();
          action.run();
        });
  }

  private void feedback() {
    if (keyboard == null) return;
    if (preferences.bool("haptics", true))
      keyboard.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    if (preferences.bool("sound", false))
      ((AudioManager) getSystemService(AUDIO_SERVICE))
          .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, .25f);
  }

  @Override
  public void tap(double x) {
    feedback();
    if (composingText.length() >= 32) finishWord(false, false);
    touchPositions.add(x);
    appendLetter(layout.nearest(x), false);
  }

  @Override
  public void swipe(int direction) {
    feedback();
    if (direction == 1) space();
    if (direction == -1) deleteWord();
    if (direction == 2) {
      finishWord(false, false);
      mode = KeyboardMode.ALPHABET;
      renderKeyboard();
    }
    if (direction == -2) requestHideSelf(0);
  }

  private void toggleShift() {
    long now = android.os.SystemClock.uptimeMillis();
    if (now - lastShiftTap < 350) {
      capsLock = true;
      shift = true;
    } else {
      capsLock = false;
      shift = !shift;
    }
    lastShiftTap = now;
    if (precision != null) precision.setVisibility(View.GONE);
    updateCase();
    updateSuggestions();
  }

  private void updateCase() {
    if (letterStrip != null) letterStrip.setUppercase(shift);
    updateCase(keyboard);
    if (keyboard != null) keyboard.invalidate();
  }

  private void updateCase(View view) {
    if (view instanceof IconButton icon && "shift".equals(icon.getTag())) updateShiftIcon(icon);
    if (view instanceof Button button) {
      if (button.getTag() instanceof Character letter)
        button.setText(String.valueOf(shift ? Character.toUpperCase(letter) : letter));
    }
    if (view instanceof android.view.ViewGroup group)
      for (int index = 0; index < group.getChildCount(); index++)
        updateCase(group.getChildAt(index));
  }

  private Button addLetterButton(LinearLayout row, char letter) {
    Button key =
        addButton(
            row,
            String.valueOf(shift ? Character.toUpperCase(letter) : letter),
            () -> appendLetter(letter, true),
            1,
            KeyRole.INPUT);
    key.setTag(letter);
    boolean[] held = {false}, symbolsHeld = {false};
    float[] origin = new float[2];
    long[] pressedAt = new long[1];
    Runnable hold =
        () -> {
          held[0] = true;
          showAccentPopup(key, letter, origin[0], origin[1]);
        };
    key.setOnTouchListener(
        (view, event) -> {
          switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN -> {
              held[0] = false;
              symbolsHeld[0] = false;
              origin[0] = event.getRawX();
              origin[1] = event.getRawY();
              pressedAt[0] = android.os.SystemClock.uptimeMillis();
              view.postDelayed(hold, preferences.number("hold_delay", 450));
            }
            case android.view.MotionEvent.ACTION_MOVE -> {
              if (held[0] && !symbolsHeld[0]) dragAccents(event.getRawX(), event.getRawY());
              else if (event.getRawY() < origin[1] - dp(16)
                  && android.os.SystemClock.uptimeMillis() - pressedAt[0] >= 120) {
                view.removeCallbacks(hold);
                held[0] = true;
                symbolsHeld[0] = true;
                precision(keyboardX(event.getRawX()), true);
              } else if (Math.hypot(event.getRawX() - origin[0], event.getRawY() - origin[1])
                  > dp(12)) view.removeCallbacks(hold);
            }
            case android.view.MotionEvent.ACTION_UP -> {
              view.removeCallbacks(hold);
              if (symbolsHeld[0]) precise(keyboardX(event.getRawX()), true);
              else if (held[0]) finishAccents(false);
              else view.performClick();
            }
            case android.view.MotionEvent.ACTION_CANCEL -> {
              view.removeCallbacks(hold);
              finishAccents(true);
            }
          }
          return true;
        });
    key.setOnLongClickListener(
        view -> {
          int[] position = new int[2];
          key.getLocationOnScreen(position);
          showAccentPopup(
              key, letter, position[0] + key.getWidth() / 2f, position[1] + key.getHeight() / 2f);
          return true;
        });
    return key;
  }

  private double keyboardX(float screenX) {
    int[] position = new int[2];
    keyboard.getLocationOnScreen(position);
    return (screenX - position[0]) / keyboard.getWidth();
  }

  @Override
  public void accents(double x, float screenX, float screenY) {
    showAccentPopup(letterStrip, layout.nearest(x), screenX, screenY);
  }

  private void showAccentPopup(View anchor, char letter, float screenX, float screenY) {
    dismissAccents();
    accentPopup = new AccentPopup(this, colors, this::appendVariation);
    accentPopup.show(anchor, letter, shift, screenX, screenY);
  }

  @Override
  public void dragAccents(float screenX, float screenY) {
    if (accentPopup != null) accentPopup.drag(screenX, screenY);
  }

  @Override
  public void finishAccents(boolean cancelled) {
    if (accentPopup != null) {
      AccentPopup active = accentPopup;
      accentPopup = null;
      if (cancelled) active.dismiss();
      else active.release();
    }
    if (cancelled && precision != null) precision.setVisibility(View.GONE);
  }

  private void dismissAccents() {
    finishAccents(true);
  }

  private void appendVariation(String text) {
    InputConnection connection = getCurrentInputConnection();
    if (connection == null) return;
    if (policy.privateField() || policy.literal()) commit(text);
    else {
      if (composingText.length() == 0) composingContext = predictionContext();
      composingText.append(text);
      touchPositions.clear();
      preciseWord = true;
      connection.setComposingText(composingText.toString(), 1);
      refreshCandidates();
      updateSuggestions();
    }
    shift = capsLock;
    updateCase();
  }

  private static final String SECONDARY = "1234567890@#()!?/";

  @Override
  public void precision(double x, boolean secondary) {
    precision.removeAllViews();
    precision.setVisibility(View.VISIBLE);
    String letters = secondary ? SECONDARY : layout.compact();
    int center = Math.max(0, Math.min(letters.length() - 1, (int) (x * letters.length())));
    for (int i = Math.max(0, center - 2); i <= Math.min(letters.length() - 1, center + 2); i++) {
      char c = letters.charAt(i);
      Button key =
          addButton(
              precision,
              String.valueOf(c),
              () -> precise((letters.indexOf(c) + .5) / letters.length(), secondary),
              1);
      if (i == center) highlight(key);
    }
  }

  @Override
  public void precise(double x, boolean secondary) {
    if (secondary) {
      finishWord(false, false);
      commit(
          String.valueOf(
              SECONDARY.charAt(
                  Math.max(0, Math.min(SECONDARY.length() - 1, (int) (x * SECONDARY.length()))))));
    } else appendLetter(layout.nearest(x), true);
    precision.setVisibility(View.GONE);
  }

  private void appendLetter(char c, boolean exact) {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    lastSpace = 0;
    undoRendered = "";
    if (policy.literal() || policy.privateField()) {
      commit(shift ? String.valueOf(c).toUpperCase(Locale.ROOT) : String.valueOf(c));
      shift = capsLock;
      renderKeyboard();
      return;
    }
    if (composingText.length() >= 32) finishWord(false, false);
    if (exact && mode == KeyboardMode.MINI) preciseWord = true;
    if (composingText.length() == 0) composingContext = predictionContext();
    composingText.append(shift ? Character.toUpperCase(c) : c);
    shift = capsLock;
    refreshCandidates();
    ic.setComposingText(composingText.toString(), 1);
    updateCase();
    updateSuggestions();
  }

  private String predictionContext() {
    if (composingText.length() > 0) return composingContext;
    if (policy.privateField() || policy.literal() || getCurrentInputConnection() == null)
      return "<s>";
    CharSequence before =
        getCurrentInputConnection().getTextBeforeCursor(160 + composingText.length(), 0);
    String text = before == null ? "" : before.toString();
    if (composingText.length() > 0 && text.endsWith(composingText.toString()))
      text = text.substring(0, text.length() - composingText.length());
    return org.linekey.core.LanguageModel.previousWord(text);
  }

  private void refreshCandidates() {
    candidates =
        mode == KeyboardMode.MINI && !preciseWord && touchPositions.size() == composingText.length()
            ? decoder.forgiving(
                touchPositions,
                layout,
                letterStrip == null
                    ? 360
                    : letterStrip.getWidth() / getResources().getDisplayMetrics().density,
                32)
            : decoder.full(composingText.toString());
    if (languageModel != null
        && preferences.bool("contextual", true)
        && !policy.privateField()
        && !policy.literal()) candidates = languageModel.rescore(candidates, predictionContext());
  }

  private String caseWord(String word) {
    if (word.isEmpty()) return word;
    if (composingText.length() == 0)
      return capsLock
          ? word.toUpperCase(Locale.ROOT)
          : shift ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word;
    if (capsLock
        || composingText.length() > 1
            && composingText.toString().equals(composingText.toString().toUpperCase(Locale.ROOT)))
      return word.toUpperCase(Locale.ROOT);
    return Character.isUpperCase(composingText.charAt(0))
        ? Character.toUpperCase(word.charAt(0)) + word.substring(1)
        : word;
  }

  private void updateSuggestions() {
    if (suggestions == null) return;
    suggestions.removeAllViews();
    suggestionScroll.scrollTo(0, 0);
    if (policy.privateField() || policy.literal()) return;
    if (composingText.length() == 0) {
      if (languageModel != null && preferences.bool("next_words", true) && !emoji) {
        suggestions.setVisibility(View.VISIBLE);
        for (var prediction : languageModel.next(predictionContext(), 16)) {
          String word = caseWord(prediction.word());
          addSuggestion(word);
        }
      } else suggestions.setVisibility(View.INVISIBLE);
      return;
    }
    suggestions.setVisibility(View.VISIBLE);
    // Literal is always offered, including unknown words.
    String literal = composingText.toString();
    Button literalButton = addSuggestion(literal);
    if (!candidates.isEmpty() && candidates.get(0).word().equalsIgnoreCase(literal))
      highlight(literalButton);
    for (Decoder.Candidate c : candidates) {
      String word = caseWord(c.word());
      if (word.equals(literal)) continue;
      Button candidate = addSuggestion(word);
      if (c == candidates.get(0)) highlight(candidate);
    }
  }

  private Button addSuggestion(String word) {
    Button button = addButton(suggestions, word, () -> choose(word), 0, KeyRole.SUGGESTION);
    LinearLayout.LayoutParams bounds = (LinearLayout.LayoutParams) button.getLayoutParams();
    bounds.width = LinearLayout.LayoutParams.WRAP_CONTENT;
    bounds.weight = 0;
    button.setLayoutParams(bounds);
    button.setMinWidth(dp(64));
    button.setPadding(dp(12), 0, dp(12), 0);
    return button;
  }

  private void highlight(Button button) {
    button.setBackgroundTintList(
        android.content.res.ColorStateList.valueOf(Color.parseColor("#80C7BF")));
    button.setTextColor(Color.parseColor("#183A35"));
  }

  private void choose(String word) {
    if (getCurrentInputConnection() == null) return;
    String original = composingText.toString();
    getCurrentInputConnection().commitText(word + " ", 1);
    getCurrentInputConnection().finishComposingText();
    learn(word);
    undoRaw = original.isEmpty() ? word + " " : original + " ";
    undoRendered = word + " ";
    undoCursor = cursorPosition();
    resetComposition();
    shift = capsLock;
    renderKeyboard();
  }

  private void finishWord(boolean addSpace, boolean correct) {
    if (composingText.length() == 0 || getCurrentInputConnection() == null) return;
    String original = composingText.toString(), word = original;
    if (correct
        && preferences.bool("correction", true)
        && !preciseWord
        && !candidates.isEmpty()
        && candidates.get(0).cost() <= Math.max(1, composingText.length()) * .9
        && (candidates.size() == 1
            || candidates.get(0).score() - candidates.get(1).score() >= .5
            || candidates.get(0).word().equalsIgnoreCase(original)))
      word = caseWord(candidates.get(0).word());
    String tail = addSpace ? " " : "";
    getCurrentInputConnection().commitText(word + tail, 1);
    getCurrentInputConnection().finishComposingText();
    learn(word);
    undoRaw = original + tail;
    undoRendered = word + tail;
    undoCursor = cursorPosition();
    resetComposition();
    shift = capsLock;
  }

  private void learn(String word) {
    if (!policy.privateField()
        && !policy.literal()
        && preferences.bool("learning", false)
        && words.learn(word)) reload();
  }

  private void resetComposition() {
    composingText.setLength(0);
    touchPositions.clear();
    candidates = java.util.Collections.emptyList();
    preciseWord = false;
  }

  private int cursorPosition() {
    if (getCurrentInputConnection() == null || policy.privateField() || policy.rawKeys()) return -1;
    ExtractedTextRequest request = new ExtractedTextRequest();
    request.hintMaxChars = 1;
    request.hintMaxLines = 1;
    ExtractedText text = getCurrentInputConnection().getExtractedText(request, 0);
    return text == null || text.selectionStart != text.selectionEnd
        ? -1
        : text.startOffset + text.selectionEnd;
  }

  private void commit(String text) {
    if (getCurrentInputConnection() == null) return;
    lastSpace = 0;
    undoRendered = "";
    if (policy.rawKeys()) {
      for (char c : text.toCharArray()) sendKeyChar(c);
    } else getCurrentInputConnection().commitText(text, 1);
  }

  private void space() {
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    if (emoji) {
      editEmojiQuery(" ");
      return;
    }
    long now = android.os.SystemClock.uptimeMillis();
    if (composingText.length() > 0) {
      finishWord(true, true);
      lastSpace = now;
      lastSpaceCursor = cursorPosition();
      renderKeyboard();
      return;
    }
    CharSequence before = policy.privateField() ? null : ic.getTextBeforeCursor(2, 0);
    if (!policy.literal()
        && preferences.bool("double_space", true)
        && lastSpaceCursor >= 0
        && cursorPosition() == lastSpaceCursor
        && now - lastSpace < 650
        && before != null
        && before.length() == 2
        && before.charAt(1) == ' '
        && Character.isLetterOrDigit(before.charAt(0))) {
      ic.deleteSurroundingText(1, 0);
      ic.commitText(". ", 1);
      undoRendered = "";
      lastSpace = 0;
      shift = capsLock || preferences.bool("caps", true);
      renderKeyboard();
    } else {
      commit(" ");
      lastSpace = now;
      lastSpaceCursor = cursorPosition();
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (emoji) {
      if (keyCode == KeyEvent.KEYCODE_DEL) {
        backspace();
        return true;
      }
      if (keyCode == KeyEvent.KEYCODE_ENTER) return true;
      int character = event.getUnicodeChar();
      if (character >= 32 && !event.isCtrlPressed() && !event.isAltPressed()) {
        editEmojiQuery(new String(Character.toChars(character)));
        return true;
      }
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (emoji
        && (keyCode == KeyEvent.KEYCODE_DEL
            || keyCode == KeyEvent.KEYCODE_ENTER
            || event.getUnicodeChar() >= 32 && !event.isCtrlPressed() && !event.isAltPressed()))
      return true;
    return super.onKeyUp(keyCode, event);
  }

  private void editEmojiQuery(String text) {
    android.text.Editable editable = emojiSearch.getText();
    int start = Math.max(0, emojiSearch.getSelectionStart());
    int end = Math.max(start, emojiSearch.getSelectionEnd());
    editable.replace(start, end, text);
    emojiSearch.setSelection(Math.min(emojiSearch.length(), start + text.length()));
  }

  private void searchLetter(char letter) {
    editEmojiQuery(String.valueOf(letter));
  }

  private void updateEmojiResults() {
    if (!emojiSearch.getText().toString().contentEquals(emojiQuery)) {
      emojiSearch.setText(emojiQuery.toString());
      emojiSearch.setSelection(emojiSearch.length());
    }
    if (emojiResults == null) return;
    emojiResults.removeAllViews();
    var matches = org.linekey.core.EmojiSearch.search(emojiQuery.toString(), 96);
    LinearLayout row = null;
    for (int index = 0; index < matches.size(); index++) {
      if (index % 6 == 0) {
        row = newKeyRow();
        emojiResults.addView(row, new LinearLayout.LayoutParams(-1, -2));
      }
      var entry = matches.get(index);
      Button key = addButton(row, entry.symbol(), () -> commit(entry.symbol()), 1);
      key.setTextSize(24);
      key.setContentDescription(entry.name());
    }
    if (row != null)
      for (int index = matches.size() % 6; index > 0 && index < 6; index++) {
        row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(52), 1));
      }
    if (matches.isEmpty()) {
      TextView empty = new TextView(this);
      empty.setText("No matches. Try a name such as heart, coffee, or smile.");
      empty.setTypeface(KeyboardFont.get(this));
      empty.setTextColor(textColor());
      empty.setPadding(dp(12), dp(12), dp(12), dp(12));
      emojiResults.addView(empty);
    }
    keyboard.invalidate();
  }

  private void backspace() {
    if (emoji) {
      android.text.Editable editable = emojiSearch.getText();
      int start = Math.max(0, emojiSearch.getSelectionStart());
      int end = Math.max(start, emojiSearch.getSelectionEnd());
      if (end > start) editable.delete(start, end);
      else if (start > 0) editable.delete(Character.offsetByCodePoints(editable, start, -1), start);
      return;
    }
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    lastSpace = 0;
    if (composingText.length() > 0) {
      composingText.deleteCharAt(composingText.length() - 1);
      if (!touchPositions.isEmpty()) touchPositions.remove(touchPositions.size() - 1);
      if (composingText.length() == 0) {
        ic.commitText("", 1);
        ic.finishComposingText();
        resetComposition();
      } else {
        refreshCandidates();
        ic.setComposingText(composingText.toString(), 1);
      }
      updateSuggestions();
      return;
    }
    if (!policy.privateField()
        && undoCursor >= 0
        && cursorPosition() == undoCursor
        && !undoRendered.isEmpty()
        && !undoRendered.equals(undoRaw)) {
      CharSequence before = ic.getTextBeforeCursor(undoRendered.length(), 0);
      if (before != null && before.toString().equals(undoRendered)) {
        ic.deleteSurroundingText(undoRendered.length(), 0);
        ic.commitText(undoRaw, 1);
        undoRendered = "";
        return;
      }
    }
    undoRendered = "";
    if (policy.rawKeys()) {
      sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
      return;
    }
    CharSequence selected = ic.getSelectedText(0);
    if (selected != null && selected.length() > 0) {
      ic.commitText("", 1);
      return;
    }
    ic.deleteSurroundingTextInCodePoints(1, 0);
  }

  private void deleteWord() {
    if (emoji && emojiQuery.length() > 0) {
      emojiQuery.setLength(0);
      updateEmojiResults();
      return;
    }
    InputConnection ic = getCurrentInputConnection();
    if (ic == null) return;
    lastSpace = 0;
    undoRendered = "";
    if (composingText.length() > 0) {
      ic.commitText("", 1);
      ic.finishComposingText();
      resetComposition();
      updateSuggestions();
      return;
    }
    if (policy.privateField() || policy.rawKeys()) {
      backspace();
      return;
    }
    CharSequence selected = ic.getSelectedText(0);
    if (selected != null && selected.length() > 0) {
      ic.commitText("", 1);
      return;
    }
    CharSequence before = ic.getTextBeforeCursor(4096, 0);
    if (before == null) return;
    int n = before.length(), i = n;
    while (i > 0 && Character.isWhitespace(before.charAt(i - 1))) i--;
    while (i > 0 && !Character.isWhitespace(before.charAt(i - 1))) i--;
    ic.deleteSurroundingText(n - i, 0);
    updateSuggestions();
  }

  private String actionLabel() {
    if (emoji) return "Search";
    EditorInfo info = getCurrentInputEditorInfo();
    if (info == null) return "Enter";
    if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "Enter";
    if (info.actionLabel != null) return info.actionLabel.toString();
    return switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
      case EditorInfo.IME_ACTION_DONE -> "Done";
      case EditorInfo.IME_ACTION_GO -> "Go";
      case EditorInfo.IME_ACTION_SEND -> "Send";
      case EditorInfo.IME_ACTION_SEARCH -> "Search";
      case EditorInfo.IME_ACTION_NEXT -> "Next";
      default -> "Enter";
    };
  }

  private void enter() {
    if (emoji) {
      updateEmojiResults();
      return;
    }
    finishWord(false, true);
    lastSpace = 0;
    undoRendered = "";
    InputConnection ic = getCurrentInputConnection();
    EditorInfo info = getCurrentInputEditorInfo();
    if (ic == null || info == null) return;
    boolean actionAllowed = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0;
    int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
    if (actionAllowed && info.actionLabel != null) ic.performEditorAction(info.actionId);
    else if (actionAllowed
        && action != EditorInfo.IME_ACTION_NONE
        && action != EditorInfo.IME_ACTION_UNSPECIFIED) ic.performEditorAction(action);
    else if (policy.rawKeys()) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    else ic.commitText("\n", 1);
    renderKeyboard();
  }
}
