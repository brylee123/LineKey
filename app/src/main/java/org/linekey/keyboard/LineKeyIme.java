// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.keyboard;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;

import org.linekey.core.Decoder;
import org.linekey.core.EmojiSearch;
import org.linekey.core.FieldPolicy;
import org.linekey.core.KeyboardMode;
import org.linekey.core.LanguageModel;
import org.linekey.core.LetterLayout;
import org.linekey.core.Personalization;
import org.linekey.core.RecorrectionHistory;
import org.linekey.core.SymbolLayout;
import org.linekey.core.WordSpelling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class LineKeyIme extends InputMethodService implements CompactStrip.Listener {
    private Preferences preferences;
    private WordStore words;
    private Decoder decoder;
    private Personalization personal;
    private Personalization undoLearning;

    private LetterLayout layout;
    private LanguageModel languageModel;
    private boolean destroyed;
    private final ClipboardSession clipboard = new ClipboardSession();
    private String toolPanel = "";
    private boolean floating;
    private boolean selecting;

    private int floatingX;
    private int floatingLift;

    private String composingContext = "<s>";
    private FieldPolicy policy = FieldPolicy.from(1, 0);
    private LinearLayout keyboard;
    private LinearLayout suggestions;
    private LinearLayout precision;

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
    private EditText emojiSearch;
    private final StringBuilder emojiQuery = new StringBuilder();
    private int navigationBottom;
    private final ContentObserver systemThemeChanges =
            new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override
                public void onChange(final boolean selfChange) {
                    if (preferences != null && preferences.number("theme", 2) == 2) {
                        renderKeyboard();
                    }
                }
            };

    private final StringBuilder composingText = new StringBuilder();
    private final List<Double> touchPositions = new ArrayList<>();
    private List<Decoder.Candidate> candidates = Collections.emptyList();
    private static final int CORRECTION_TEXT_TOKEN = 0x4c4b;
    private final RecorrectionHistory correctionHistory = new RecorrectionHistory();
    private RecorrectionHistory.Target revisitedWord;
    private RecorrectionHistory.Entry undoEvidence;
    private List<String> revisitedSuggestions = List.of();
    private boolean shift;
    private boolean emoji;
    private boolean preciseWord;

    private KeyboardMode mode = KeyboardMode.MINI;
    private String undoRendered = "";
    private String undoRaw = "";

    private long lastSpace;
    private int undoCursor = -1;
    private int lastSpaceCursor = -1;

    private final SharedPreferences.OnSharedPreferenceChangeListener themeChanges =
            (final SharedPreferences preferences, final String key) -> {
                if ("dark".equals(key) || "theme".equals(key)) {
                    renderKeyboard();
                }
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
                            final LanguageModel loaded = LanguageModel.get();
                            new Handler(Looper.getMainLooper())
                                    .post(
                                            () -> {
                                                if (destroyed) {
                                                    return;
                                                }
                                                languageModel = loaded;
                                                if (composingText.length() > 0) {
                                                    refreshCandidates();
                                                }
                                                updateSuggestions();
                                            });
                        },
                        "LineKey model loader")
                .start();
        preferences.store.registerOnSharedPreferenceChangeListener(themeChanges);
        getContentResolver()
                .registerContentObserver(
                        Settings.Secure.getUriFor("ui_night_mode"), false, systemThemeChanges);
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        clearCorrectionHistory();
        resetComposition();
        preferences.store.unregisterOnSharedPreferenceChangeListener(themeChanges);
        getContentResolver().unregisterContentObserver(systemThemeChanges);
        super.onDestroy();
    }

    private void reload() {
        decoder = new Decoder();
        words.load(decoder);
        personal = words.read();
        layout = new LetterLayout(preferences.number("layout", 0));
    }

    @Override
    public View onCreateInputView() {
        container = new FrameLayout(this);
        container.setPadding(0, 0, 0, dp(48));
        container.setOnApplyWindowInsetsListener(
                (final View view, final WindowInsets insets) -> {
                    navigationBottom =
                            Build.VERSION.SDK_INT >= 30
                                    ? insets.getInsets(
                                                    WindowInsets.Type.navigationBars()
                                                            | WindowInsets.Type.captionBar()
                                                            | WindowInsets.Type.systemGestures())
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
    public void onComputeInsets(final Insets out) {
        super.onComputeInsets(out);
        if (container == null) {
            return;
        }
        final int[] location = new int[2];
        container.getLocationInWindow(location);
        if (floating && keyboard != null) {
            out.contentTopInsets = getWindow().getWindow().getDecorView().getHeight();
            out.visibleTopInsets = out.contentTopInsets;
            final int[] panelLocation = new int[2];
            keyboard.getLocationInWindow(panelLocation);
            out.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
            out.touchableRegion.set(
                    panelLocation[0],
                    panelLocation[1],
                    panelLocation[0] + keyboard.getWidth(),
                    panelLocation[1] + keyboard.getHeight());
            out.touchableRegion.union(
                    new Rect(
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
    public void onStartInput(final EditorInfo info, final boolean restarting) {
        super.onStartInput(info, restarting);
        resetComposition();
        clearCorrectionHistory();
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
    public void onStartInputView(final EditorInfo info, final boolean restarting) {
        super.onStartInputView(info, restarting);
        if (composingText.length() == 0
                && !capsLock
                && !policy.literal()
                && preferences.bool("caps", true)
                && getCurrentInputConnection() != null) {
            shift = getCurrentInputConnection().getCursorCapsMode(info.inputType) != 0;
        }
        refreshRevisitedWord();
        renderKeyboard();
    }

    @Override
    public void onFinishInput() {
        resetComposition();
        clearCorrectionHistory();
        undoRendered = "";
        lastSpace = 0;
        dismissAccents();
        super.onFinishInput();
    }

    @Override
    public void onUpdateSelection(
            final int oldStart,
            final int oldEnd,
            final int newStart,
            final int newEnd,
            final int candidatesStart,
            final int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
        if (composingText.length() > 0 && (newStart != newEnd || newStart != candidatesEnd)) {
            if (getCurrentInputConnection() != null) {
                getCurrentInputConnection().finishComposingText();
            }
            resetComposition();
            undoRendered = "";
            lastSpace = 0;
            refreshRevisitedWord();
            updateSuggestions();
        } else if (composingText.length() == 0) {
            if (undoCursor >= 0 && (newStart != undoCursor || newEnd != undoCursor)) {
                undoRendered = "";
            }
            if (lastSpaceCursor >= 0
                    && (newStart != lastSpaceCursor || newEnd != lastSpaceCursor)) {
                lastSpace = 0;
            }
            refreshRevisitedWord();
            updateSuggestions();
        }
    }

    @Override
    public void onUpdateExtractedText(final int token, final ExtractedText text) {
        super.onUpdateExtractedText(token, text);
        // An app can change text without moving the cursor. Revalidate those ranges too.
        if (token == CORRECTION_TEXT_TOKEN && composingText.length() == 0) {
            refreshRevisitedWord();
            updateSuggestions();
        }
    }

    private int dp(final int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onConfigurationChanged(final Configuration configuration) {
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
        if (container == null) {
            return;
        }
        final Window window = getWindow().getWindow();
        if (window != null) {
            final View decor = window.getDecorView();
            if (Build.VERSION.SDK_INT >= 30 && decor.getWindowInsetsController() != null) {
                decor.getWindowInsetsController()
                        .setSystemBarsAppearance(
                                preferences.isDark()
                                        ? 0
                                        : WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            } else {
                final int appearance = decor.getSystemUiVisibility();
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
        if (getWindow().getWindow() != null) {
            getWindow().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        letterStrip = null;
        colors = new KeyboardColors(preferences.isDark());
        container.setBackgroundColor(colors.surface);
        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setBackgroundColor(colors.surface);
        if (floating) {
            container.setBackgroundColor(Color.TRANSPARENT);
        }
        keyboard.setPadding(
                dp(compactSurface() ? 3 : 4), dp(2), dp(compactSurface() ? 3 : 4), dp(4));
        final int position = preferences.number("position", 0);
        final int width =
                position == 0
                        ? FrameLayout.LayoutParams.MATCH_PARENT
                        : (int) (getResources().getDisplayMetrics().widthPixels * .78);
        final FrameLayout.LayoutParams box =
                new FrameLayout.LayoutParams(
                        width,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        position == 1
                                ? Gravity.START
                                : position == 2 ? Gravity.END : Gravity.CENTER_HORIZONTAL);
        if (floating) {
            box.width =
                    Math.min(dp(320), (int) (getResources().getDisplayMetrics().widthPixels * .82));
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
                    (final View view,
                            final int left,
                            final int top,
                            final int right,
                            final int bottom,
                            final int oldLeft,
                            final int oldTop,
                            final int oldRight,
                            final int oldBottom) -> {
                        final int x =
                                Math.max(
                                        0,
                                        Math.min(
                                                box.leftMargin,
                                                container.getWidth() - keyboard.getWidth()));
                        final int lift =
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
        } else {
            container.setClipToPadding(true);
        }
        container.addView(keyboard, box);
        if (floating) {
            addFloatingHandle(box);
        }
        final LinearLayout candidateBar = addRow();
        if (compactSurface()) {
            addShiftButton(candidateBar, 1);
        }
        suggestions = newKeyRow();
        suggestions.setOrientation(LinearLayout.HORIZONTAL);
        suggestionScroll = new HorizontalScrollView(this);
        suggestionScroll.setContentDescription("Word suggestions. Swipe to see more matches.");
        suggestionScroll.setHorizontalScrollBarEnabled(false);
        suggestionScroll.addView(suggestions, new FrameLayout.LayoutParams(-2, -2));
        candidateBar.addView(suggestionScroll, new LinearLayout.LayoutParams(0, -2, 5));
        if (compactSurface()) {
            addDeleteButton(candidateBar, 1);
        }
        if (policy.voiceAllowed()) {
            addIconButton(
                    candidateBar, R.drawable.ic_control_mic, "Voice input", this::voiceInput, .7f);
        }
        if (!VoiceDraft.get().isEmpty() && policy.voiceAllowed()) {
            final TextView draft = new TextView(this);
            draft.setText(VoiceDraft.get());
            draft.setMaxLines(2);
            draft.setTextColor(textColor());
            draft.setTypeface(KeyboardFont.get(this));
            keyboard.addView(draft);
            final LinearLayout review = addRow();
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
        if (!toolPanel.isEmpty()) {
            addToolPanel();
        }
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
            emojiSearch = new EditText(this);
            emojiSearch.setSingleLine(true);
            emojiSearch.setTypeface(KeyboardFont.get(this));
            emojiSearch.setHint("Search emoji…");
            emojiSearch.setHintTextColor(textColor());
            emojiSearch.setShowSoftInputOnFocus(false);
            emojiSearch.setFilters(new InputFilter[] {new InputFilter.LengthFilter(40)});
            emojiSearch.addTextChangedListener(
                    new TextWatcher() {
                        @Override
                        public void beforeTextChanged(
                                final CharSequence text,
                                final int start,
                                final int count,
                                final int after) {}

                        @Override
                        public void onTextChanged(
                                final CharSequence text,
                                final int start,
                                final int before,
                                final int count) {}

                        @Override
                        public void afterTextChanged(final Editable text) {
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
            final ScrollView resultsScroll = new ScrollView(this);
            resultsScroll.setFillViewport(true);
            resultsScroll.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            emojiResults.setMinimumHeight(dp(112));
            resultsScroll.addView(emojiResults);
            keyboard.addView(resultsScroll, new LinearLayout.LayoutParams(-1, dp(112)));
            updateEmojiResults();
            // Query input belongs to this IME; it never changes the application's editor focus.
            addLetterRows(true);
        } else if (mode == KeyboardMode.MINI) {
            final CompactStrip strip = new CompactStrip(this, layout, this, preferences);
            letterStrip = strip;
            strip.setUppercase(shift);
            keyboard.addView(
                    strip,
                    new LinearLayout.LayoutParams(-1, dp(preferences.number("mini_height", 48))));
        } else {
            addLetterRows(false);
        }
        final LinearLayout actions = addRow();
        addModeButton(actions);
        if (!policy.privateField()) {
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
        }
        addButton(
                actions,
                ",",
                () -> {
                    finishWord(false, true);
                    if (emoji) {
                        editEmojiQuery(",");
                    } else {
                        commit(",");
                    }
                },
                1);
        final Button spaceKey = addButton(actions, "Space", this::space, 3);
        enableCursorSwipe(spaceKey);
        addButton(
                actions,
                ".",
                () -> {
                    finishWord(false, true);
                    if (emoji) {
                        editEmojiQuery(".");
                    } else {
                        commit(".");
                    }
                },
                1);
        addEnterButton(actions);
        updateSuggestions();
    }

    /**
     * Ten equal columns, with proportional spacers that also fit floating and one-handed panels.
     */
    private void addLetterRows(final boolean query) {
        final String[] rows = layout.rows();
        for (int index = 0; index < rows.length; index++) {
            final String letters = rows[index];
            final LinearLayout row = addRow();
            final boolean bottom = index == rows.length - 1;
            final float gutter = (10 - letters.length() - (bottom ? 3 : 0)) / 2f;
            if (bottom) {
                if (query) {
                    addKeySpacer(row, 1.5f);
                } else {
                    addShiftButton(row, 1.5f);
                }
            }
            addKeySpacer(row, gutter);
            for (final char letter : letters.toCharArray()) {
                if (query) {
                    addButton(
                            row,
                            String.valueOf(letter),
                            () -> searchLetter(letter),
                            1,
                            KeyRole.INPUT);
                } else {
                    addLetterButton(row, letter);
                }
            }
            addKeySpacer(row, gutter);
            if (bottom) {
                addDeleteButton(row, 1.5f);
            }
            row.addOnLayoutChangeListener(
                    (final View view,
                            final int left,
                            final int top,
                            final int right,
                            final int bottomEdge,
                            final int oldLeft,
                            final int oldTop,
                            final int oldRight,
                            final int oldBottom) -> fitLetterLabels(row));
        }
    }

    /**
     * Keep one readable size for every letter, including W/M and uppercase, within the actual
     * panel.
     */
    private void fitLetterLabels(final LinearLayout row) {
        if (row.getWidth() <= 0) {
            return;
        }
        final Paint measure = new Paint();
        measure.setTypeface(KeyboardFont.get(this));
        final float normalPixels =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, 22, getResources().getDisplayMetrics());
        measure.setTextSize(normalPixels);
        float widest = 0;
        for (final char letter :
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray()) {
            widest = Math.max(widest, measure.measureText(String.valueOf(letter)));
        }
        int padding = 0;
        for (int index = 0; index < row.getChildCount(); index++) {
            if (row.getChildAt(index) instanceof final Button key) {
                padding =
                        Math.max(
                                padding,
                                key.getCompoundPaddingLeft() + key.getCompoundPaddingRight());
            }
        }
        final float available = Math.max(1, row.getWidth() / 10f - padding - dp(2));
        final float pixels = normalPixels * Math.min(1, available / widest);
        for (int index = 0; index < row.getChildCount(); index++) {
            if (row.getChildAt(index) instanceof final Button key
                    && Math.abs(key.getTextSize() - pixels) > .25f) {
                key.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels);
            }
        }
    }

    private void addKeySpacer(final LinearLayout row, final float columns) {
        if (columns > 0) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 0, columns));
        }
    }

    private void addModeButton(final LinearLayout row) {
        addModeButton(row, 1.2f);
    }

    private void addModeButton(final LinearLayout row, final float weight) {
        final Button modes = addButton(row, modeLabel(), this::cycleMode, weight, KeyRole.ACTION);
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
                (final View view) -> {
                    showModes(view);
                    return true;
                });
    }

    private void addEnterButton(final LinearLayout row) {
        addEnterButton(row, 1.3f);
    }

    private void addEnterButton(final LinearLayout row, final float weight) {
        final IconButton enterKey =
                addIconButton(row, actionIcon(), actionLabel(), this::enter, weight);
        enterKey.setBackgroundTintList(ColorStateList.valueOf(colors.accent));
        enterKey.setIcon(actionIcon(), colors.accentText, false);
    }

    private void selectMode(final KeyboardMode destination) {
        finishWord(false, false);
        mode = destination;
        emoji = false;
        renderKeyboard();
    }

    private void addSymbolPage() {
        final boolean secondary = mode == KeyboardMode.SYMBOLS;
        final String[] rows = SymbolLayout.rows(secondary);
        for (int index = 0; index < rows.length; index++) {
            final LinearLayout row = addRow();
            if (index == 2) {
                addButton(
                        row,
                        secondary ? "?123" : "=\\<",
                        () -> selectMode(secondary ? KeyboardMode.NUMBERS : KeyboardMode.SYMBOLS),
                        1.5f,
                        KeyRole.ACTION);
            }
            for (final char character : rows[index].toCharArray()) {
                addButton(
                        row,
                        String.valueOf(character),
                        () -> commit(String.valueOf(character)),
                        1,
                        KeyRole.INPUT);
            }
            if (index == 2) {
                addDeleteButton(row, 1.5f);
            }
        }
        final LinearLayout actions = addRow();
        addModeButton(actions, 1.5f);
        addButton(
                actions,
                secondary ? "<" : ",",
                () -> commit(secondary ? "<" : ","),
                1,
                KeyRole.ACTION);
        final Button pad =
                addButton(
                        actions,
                        "12\n34",
                        () -> selectMode(KeyboardMode.NUMPAD),
                        1.2f,
                        KeyRole.ACTION);
        pad.setSingleLine(false);
        pad.setTextSize(11);
        pad.setContentDescription("Number pad");
        final Button space = addButton(actions, "Space", this::space, 3.8f);
        enableCursorSwipe(space);
        addButton(
                actions,
                secondary ? ">" : ".",
                () -> commit(secondary ? ">" : "."),
                1,
                KeyRole.ACTION);
        addEnterButton(actions, 1.5f);
    }

    private void addNumberPad() {
        final LinearLayout body = addRow();
        if (policy.phone()) {
            final LinearLayout dialing = new LinearLayout(this);
            dialing.setOrientation(LinearLayout.VERTICAL);
            body.addView(dialing, new LinearLayout.LayoutParams(0, -2, 1.4f));
            for (final char character : SymbolLayout.operators(true).toCharArray()) {
                addPadColumnButton(dialing, String.valueOf(character));
            }
        } else {
            final ScrollView operators = new ScrollView(this);
            operators.setContentDescription("Number pad operators");
            operators.setVerticalScrollBarEnabled(true);
            operators.setScrollbarFadingEnabled(false);
            final GradientDrawable background = new GradientDrawable();
            background.setColor(colors.action);
            background.setCornerRadius(dp(10));
            operators.setBackground(background);
            operators.setClipToOutline(true);
            final FrameLayout operatorColumn = new FrameLayout(this);
            operatorColumn.setPadding(dp(2), dp(4), dp(2), dp(4));
            body.addView(operatorColumn, new LinearLayout.LayoutParams(0, -2, 1.4f));
            operatorColumn.addView(
                    operators, new FrameLayout.LayoutParams(-1, dp(3 * fullKeyHeight() - 8)));
            final LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            operators.addView(column);
            for (final char character : SymbolLayout.operators(policy.phone()).toCharArray()) {
                final Button key =
                        addButton(
                                column,
                                String.valueOf(character),
                                () -> commit(String.valueOf(character)),
                                1,
                                KeyRole.ACTION);
                key.setTextSize(20);
                key.setBackground(
                        new RippleDrawable(ColorStateList.valueOf(colors.outline), null, null));
                key.setLayoutParams(
                        new LinearLayout.LayoutParams(-1, dp((3 * fullKeyHeight() - 8) / 4)));
            }
        }
        final LinearLayout digits = new LinearLayout(this);
        digits.setOrientation(LinearLayout.VERTICAL);
        body.addView(digits, new LinearLayout.LayoutParams(0, -2, 7.2f));
        for (final String letters : new String[] {"123", "456", "789"}) {
            final LinearLayout row = newKeyRow();
            digits.addView(row, new LinearLayout.LayoutParams(-1, -2));
            for (final char digit : letters.toCharArray()) {
                final Button key =
                        addButton(
                                row,
                                String.valueOf(digit),
                                () -> commit(String.valueOf(digit)),
                                1,
                                KeyRole.INPUT);
                key.setTextSize(26);
            }
        }
        final LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        body.addView(right, new LinearLayout.LayoutParams(0, -2, 1.4f));
        final Button percent =
                addButton(
                        right,
                        policy.phone() ? "(" : "%",
                        () -> commit(policy.phone() ? "(" : "%"),
                        1,
                        KeyRole.ACTION);
        percent.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(fullKeyHeight())));
        if (policy.phone()) {
            addPadColumnButton(right, ")");
        } else {
            final IconButton space =
                    addIconButton(right, R.drawable.ic_control_space_bar, "Space", this::space, 1);
            space.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(fullKeyHeight())));
            enableCursorSwipe(space);
        }
        addDeleteButton(right, 1);
        right.getChildAt(right.getChildCount() - 1)
                .setLayoutParams(new LinearLayout.LayoutParams(-1, dp(fullKeyHeight())));
        final LinearLayout bottom = addRow();
        addModeButton(bottom, 1.4f);
        final LinearLayout middle = newKeyRow();
        bottom.addView(middle, new LinearLayout.LayoutParams(0, -2, 7.2f));
        if (policy.phone()) {
            for (final char character : "*0#".toCharArray()) {
                final Button key =
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
            final Button zero = addButton(middle, "0", () -> commit("0"), 2.4f, KeyRole.INPUT);
            zero.setTextSize(26);
            addButton(middle, "=", () -> commit("="), 1.3f, KeyRole.INPUT);
            addButton(middle, ".", () -> commit("."), 1.1f, KeyRole.ACTION);
        }
        addEnterButton(bottom, 1.4f);
    }

    private void addPadColumnButton(final LinearLayout column, final String label) {
        final Button key = addButton(column, label, () -> commit(label), 1, KeyRole.ACTION);
        key.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(fullKeyHeight())));
    }

    private int fullKeyHeight() {
        return Math.max(52, preferences.number("key_height", 48));
    }

    private boolean allowMini() {
        return !policy.literal() && !policy.privateField();
    }

    private String modeLabel() {
        if (policy.numeric() || policy.phone()) {
            return "⋯";
        }
        return emoji ? "ABC" : mode.nextLabel(allowMini());
    }

    private void cycleMode() {
        if (policy.numeric() || policy.phone()) {
            return;
        }
        finishWord(false, false);
        mode = emoji ? KeyboardMode.ALPHABET : mode.next(allowMini());
        emoji = false;
        renderKeyboard();
    }

    private IconButton addIconButton(
            final LinearLayout row,
            final int icon,
            final String description,
            final Runnable action,
            final float weight) {
        final IconButton key = new IconButton(this);
        styleKey(key, row, action, weight, KeyRole.ACTION);
        key.setPadding(dp(4), dp(4), dp(4), dp(4));
        key.setContentDescription(description);
        key.setIcon(icon, colors.text, false);
        return key;
    }

    private void addShiftButton(final LinearLayout row, final float weight) {
        final IconButton key =
                addIconButton(
                        row,
                        R.drawable.ic_control_shift_outline,
                        "Shift",
                        this::toggleShift,
                        weight);
        key.setTag("shift");
        key.setOnLongClickListener(
                (final View view) -> {
                    capsLock = true;
                    shift = true;
                    updateCase();
                    updateSuggestions();
                    return true;
                });
        updateShiftIcon(key);
    }

    private void updateShiftIcon(final IconButton key) {
        key.setSelected(shift);
        key.setBackgroundTintList(shift ? ColorStateList.valueOf(colors.accent) : null);
        key.setIcon(
                shift ? R.drawable.ic_control_shift_filled : R.drawable.ic_control_shift_outline,
                shift ? colors.accentText : colors.text,
                capsLock);
        key.setContentDescription(
                "Shift. " + (capsLock ? "Caps lock on" : shift ? "Uppercase" : "Lowercase"));
        if (Build.VERSION.SDK_INT >= 30) {
            key.setStateDescription(capsLock ? "Caps lock" : shift ? "On" : "Off");
        }
    }

    private void addDeleteButton(final LinearLayout row, final float weight) {
        final IconButton key =
                addIconButton(
                        row,
                        R.drawable.ic_control_backspace,
                        "Delete. Hold to delete the previous word.",
                        this::backspace,
                        weight);
        key.setOnLongClickListener(
                (final View view) -> {
                    deleteWord();
                    return true;
                });
    }

    private int actionIcon() {
        if (emoji) {
            return R.drawable.ic_control_search;
        }
        final EditorInfo info = getCurrentInputEditorInfo();
        if (info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return R.drawable.ic_control_keyboard_return;
        }
        return switch (info.imeOptions & EditorInfo.IME_MASK_ACTION) {
            case EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_control_search;
            case EditorInfo.IME_ACTION_DONE -> R.drawable.ic_control_check;
            case EditorInfo.IME_ACTION_SEND -> R.drawable.ic_control_send;
            case EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_PREVIOUS,
                    EditorInfo.IME_ACTION_GO ->
                    R.drawable.ic_control_arrow_forward;
            default -> R.drawable.ic_control_keyboard_return;
        };
    }

    private void showModes(final View anchor) {
        final PopupMenu menu = new PopupMenu(MaterialUi.themed(this, preferences.isDark()), anchor);
        if (!policy.literal() && !policy.privateField()) {
            menu.getMenu().add(mode == KeyboardMode.MINI ? "Full" : "Mini");
        }
        menu.getMenu().add(floating ? "Dock keyboard" : "Float keyboard");
        menu.getMenu().add("Cursor panel");
        if (!policy.privateField()) {
            menu.getMenu().add("Clipboard panel");
            if (policy.voiceAllowed()) {
                menu.getMenu().add("Voice input");
            }
        }
        menu.getMenu().add("Settings");
        menu.getMenu().add("Change keyboard");
        menu.setOnMenuItemClickListener(
                (final MenuItem item) -> {
                    final String label = item.getTitle().toString();
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
                                new Intent(this, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } else {
                        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                                .showInputMethodPicker();
                    }
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
        if (!policy.voiceAllowed()) {
            return;
        }
        finishWord(false, false);
        startActivity(
                new Intent(this, VoiceInputActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void updateNavigationBackdrop() {
        if (navigationBackdrop == null) {
            return;
        }
        final FrameLayout.LayoutParams bounds =
                new FrameLayout.LayoutParams(-1, container.getPaddingBottom(), Gravity.BOTTOM);
        bounds.bottomMargin = -container.getPaddingBottom();
        navigationBackdrop.setLayoutParams(bounds);
    }

    private void addFloatingHandle(final FrameLayout.LayoutParams box) {
        final LinearLayout handle = addRow();
        final Button drag = addPanelButton(handle, "Drag", () -> {}, 2);
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
        final float[] origin = new float[2];
        final int[] start = new int[2];
        drag.setOnTouchListener(
                (final View view, final MotionEvent event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        origin[0] = event.getRawX();
                        origin[1] = event.getRawY();
                        start[0] = box.leftMargin;
                        start[1] = box.bottomMargin;
                    } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
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
                                                container.getHeight()
                                                        - container.getPaddingBottom()
                                                        - keyboard.getHeight(),
                                                start[1] - (int) (event.getRawY() - origin[1])));
                        keyboard.setLayoutParams(box);
                        floatingX = box.leftMargin;
                        floatingLift = box.bottomMargin;
                    }
                    return true;
                });
    }

    private void cursorKey(final int code) {
        if (getCurrentInputConnection() == null) {
            return;
        }
        finishWord(false, false);
        final int meta = selecting ? KeyEvent.META_SHIFT_ON : 0;
        final long time = SystemClock.uptimeMillis();
        getCurrentInputConnection()
                .sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, meta));
        getCurrentInputConnection()
                .sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0, meta));
    }

    private void editorAction(final int action) {
        if (policy.privateField() && action != android.R.id.selectAll) {
            return;
        }
        if (getCurrentInputConnection() != null) {
            finishWord(false, false);
            getCurrentInputConnection().performContextMenuAction(action);
        }
    }

    private void enableCursorSwipe(final View spaceKey) {
        spaceKey.setContentDescription("Space. Swipe horizontally to move the cursor.");
        final float[] last = new float[1];
        final boolean[] moved = new boolean[1];
        spaceKey.setOnTouchListener(
                (final View view, final MotionEvent event) -> {
                    final int action = event.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        last[0] = event.getRawX();
                        moved[0] = false;
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        final int steps = (int) ((event.getRawX() - last[0]) / dp(12));
                        if (steps != 0) {
                            moved[0] = true;
                            last[0] += steps * dp(12);
                            for (int index = 0; index < Math.min(12, Math.abs(steps)); index++) {
                                if (emoji) {
                                    final int position =
                                            Math.max(0, emojiSearch.getSelectionStart());
                                    if (steps < 0 && position > 0
                                            || steps > 0 && position < emojiSearch.length()) {
                                        emojiSearch.setSelection(
                                                Character.offsetByCodePoints(
                                                        emojiSearch.getText(),
                                                        position,
                                                        steps < 0 ? -1 : 1));
                                    }
                                } else {
                                    cursorKey(
                                            steps < 0
                                                    ? KeyEvent.KEYCODE_DPAD_LEFT
                                                    : KeyEvent.KEYCODE_DPAD_RIGHT);
                                }
                            }
                        }
                    } else if (action == MotionEvent.ACTION_UP && !moved[0]) {
                        view.performClick();
                    }
                    return true;
                });
    }

    private void addToolPanel() {
        final Context context = MaterialUi.themed(this, preferences.isDark());
        final MaterialCardView card = MaterialUi.card(context);
        final LinearLayout panel = MaterialUi.column(context);
        panel.setPadding(dp(8), dp(4), dp(8), dp(8));
        card.addView(panel);
        keyboard.addView(card, new LinearLayout.LayoutParams(-1, -2));
        final LinearLayout title = panelRow(panel);
        final TextView caption = new MaterialTextView(context);
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
            final LinearLayout arrows = panelRow(panel);
            addPanelButton(arrows, "←", () -> cursorKey(KeyEvent.KEYCODE_DPAD_LEFT), 1);
            addPanelButton(arrows, "↑", () -> cursorKey(KeyEvent.KEYCODE_DPAD_UP), 1);
            addPanelButton(arrows, "↓", () -> cursorKey(KeyEvent.KEYCODE_DPAD_DOWN), 1);
            addPanelButton(arrows, "→", () -> cursorKey(KeyEvent.KEYCODE_DPAD_RIGHT), 1);
            final LinearLayout edits = panelRow(panel);
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
                final LinearLayout clipboardActions = panelRow(panel);
                addPanelButton(clipboardActions, "Copy", () -> editorAction(android.R.id.copy), 1);
                addPanelButton(clipboardActions, "Cut", () -> editorAction(android.R.id.cut), 1);
                addPanelButton(
                        clipboardActions, "Paste", () -> editorAction(android.R.id.paste), 1);
            }
        } else if (!policy.privateField()) {
            final String current = clipboard.read(this);
            final LinearLayout actions = panelRow(panel);
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
            final ScrollView scroll = new ScrollView(context);
            final LinearLayout clips = new LinearLayout(context);
            clips.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(clips);
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(96)));
            for (final String text : clipboard.history()) {
                final LinearLayout row = new LinearLayout(context);
                clips.addView(row);
                final Button clip =
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

    private LinearLayout panelRow(final LinearLayout panel) {
        final LinearLayout row = new LinearLayout(panel.getContext());
        row.setBaselineAligned(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private Button addPanelButton(
            final LinearLayout row,
            final String caption,
            final Runnable action,
            final float weight) {
        final MaterialButton button =
                MaterialUi.addButton(
                        MaterialUi.themed(this, preferences.isDark()), caption, action);
        row.addView(button, new LinearLayout.LayoutParams(0, dp(48), weight));
        return button;
    }

    /** Key edges align geometrically, never by glyph baselines or multiline label baselines. */
    private LinearLayout newKeyRow() {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        row.setGravity(Gravity.TOP);
        return row;
    }

    private LinearLayout addRow() {
        final LinearLayout row = newKeyRow();
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (keyboard != null) {
            keyboard.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        return row;
    }

    private Button addButton(
            final LinearLayout parent,
            final String label,
            final Runnable action,
            final float weight) {
        final KeyRole role =
                label.equals("Space")
                        ? KeyRole.SPACE
                        : label.matches("[a-zA-Z0-9\\p{Punct}]") ? KeyRole.INPUT : KeyRole.ACTION;
        return addButton(parent, label, action, weight, role);
    }

    private Button addButton(
            final LinearLayout parent,
            final String label,
            final Runnable action,
            final float weight,
            final KeyRole role) {
        return configureButton(new Button(this), parent, label, action, weight, role);
    }

    private Button configureButton(
            final Button button,
            final LinearLayout parent,
            final String label,
            final Runnable action,
            final float weight,
            final KeyRole role) {
        button.setText(label);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        final boolean letterKey = label.matches("[a-zA-Z]");
        button.setTypeface(KeyboardFont.get(this));
        button.setLetterSpacing(0);
        button.setFontFeatureSettings("kern");
        button.setTextSize(!compactSurface() && letterKey ? 22 : 14);
        button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER);
        if (role == KeyRole.ACTION
                && label.length() > 1
                && label.chars().allMatch((final int character) -> character < 128)) {
            // Single-line TextViews otherwise measure against a virtual scrolling width and never
            // shrink.
            button.setHorizontallyScrolling(false);
            button.setAutoSizeTextTypeUniformWithConfiguration(
                    10, 14, 1, TypedValue.COMPLEX_UNIT_SP);
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
            final View button,
            final LinearLayout parent,
            final Runnable action,
            final float weight,
            final KeyRole role) {
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        final GradientDrawable background = new GradientDrawable();
        final boolean accent = role == KeyRole.SPACE;
        background.setColor(
                accent
                        ? colors.accent
                        : role == KeyRole.ACTION
                                ? colors.action
                                : role == KeyRole.SUGGESTION ? colors.surface : colors.key);
        if (role == KeyRole.ACTION) {
            background.setStroke(dp(1), colors.outline);
        }
        background.setCornerRadius(dp(compactSurface() ? 6 : 10));
        final int verticalInset = dp(compactSurface() ? 2 : 4);
        button.setBackground(
                new InsetDrawable(background, dp(2), verticalInset, dp(2), verticalInset));
        button.setStateListAnimator(null);
        if (accent && button instanceof final Button text) {
            text.setTextColor(colors.accentText);
        }
        parent.addView(
                button,
                new LinearLayout.LayoutParams(
                        0,
                        dp(
                                compactSurface()
                                        ? 36
                                        : Math.max(52, preferences.number("key_height", 48))),
                        weight));
        button.setOnClickListener(
                (final View v) -> {
                    feedback();
                    action.run();
                });
    }

    private void feedback() {
        if (keyboard == null) {
            return;
        }
        if (preferences.bool("haptics", true)) {
            keyboard.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        if (preferences.bool("sound", false)) {
            ((AudioManager) getSystemService(AUDIO_SERVICE))
                    .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, .25f);
        }
    }

    @Override
    public void tap(final double x) {
        feedback();
        if (composingText.length() >= 32) {
            finishWord(false, false);
        }
        touchPositions.add(x);
        appendLetter(layout.nearest(x), false);
    }

    @Override
    public void swipe(final int direction) {
        feedback();
        if (direction == 1) {
            space();
        }
        if (direction == -1) {
            deleteWord();
        }
        if (direction == 2) {
            finishWord(false, false);
            mode = KeyboardMode.ALPHABET;
            renderKeyboard();
        }
        if (direction == -2) {
            requestHideSelf(0);
        }
    }

    private void toggleShift() {
        final long now = SystemClock.uptimeMillis();
        if (now - lastShiftTap < 350) {
            capsLock = true;
            shift = true;
        } else {
            capsLock = false;
            shift = !shift;
        }
        lastShiftTap = now;
        if (precision != null) {
            precision.setVisibility(View.GONE);
        }
        updateCase();
        updateSuggestions();
    }

    private void updateCase() {
        if (letterStrip != null) {
            letterStrip.setUppercase(shift);
        }
        updateCase(keyboard);
        if (keyboard != null) {
            keyboard.invalidate();
        }
    }

    private void updateCase(final View view) {
        if (view instanceof final IconButton icon && "shift".equals(icon.getTag())) {
            updateShiftIcon(icon);
        }
        if (view instanceof final Button button) {
            if (button.getTag() instanceof final Character letter) {
                button.setText(String.valueOf(shift ? Character.toUpperCase(letter) : letter));
            }
        }
        if (view instanceof final ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                updateCase(group.getChildAt(index));
            }
        }
    }

    private Button addLetterButton(final LinearLayout row, final char letter) {
        final Button key =
                addButton(
                        row,
                        String.valueOf(shift ? Character.toUpperCase(letter) : letter),
                        () -> appendLetter(letter, true),
                        1,
                        KeyRole.INPUT);
        key.setTag(letter);
        final boolean[] held = {false};
        final boolean[] symbolsHeld = {false};

        final float[] origin = new float[2];
        final long[] pressedAt = new long[1];
        final Runnable hold =
                () -> {
                    held[0] = true;
                    showAccentPopup(key, letter, origin[0], origin[1]);
                };
        key.setOnTouchListener(
                (final View view, final MotionEvent event) -> {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN -> {
                            held[0] = false;
                            symbolsHeld[0] = false;
                            origin[0] = event.getRawX();
                            origin[1] = event.getRawY();
                            pressedAt[0] = SystemClock.uptimeMillis();
                            view.postDelayed(hold, preferences.number("hold_delay", 450));
                        }
                        case MotionEvent.ACTION_MOVE -> {
                            if (held[0] && !symbolsHeld[0]) {
                                dragAccents(event.getRawX(), event.getRawY());
                            } else if (event.getRawY() < origin[1] - dp(16)
                                    && SystemClock.uptimeMillis() - pressedAt[0] >= 120) {
                                view.removeCallbacks(hold);
                                held[0] = true;
                                symbolsHeld[0] = true;
                                precision(keyboardX(event.getRawX()), true);
                            } else if (Math.hypot(
                                            event.getRawX() - origin[0],
                                            event.getRawY() - origin[1])
                                    > dp(12)) {
                                view.removeCallbacks(hold);
                            }
                        }
                        case MotionEvent.ACTION_UP -> {
                            view.removeCallbacks(hold);
                            if (symbolsHeld[0]) {
                                precise(keyboardX(event.getRawX()), true);
                            } else if (held[0]) {
                                finishAccents(false);
                            } else {
                                view.performClick();
                            }
                        }
                        case MotionEvent.ACTION_CANCEL -> {
                            view.removeCallbacks(hold);
                            finishAccents(true);
                        }
                    }
                    return true;
                });
        key.setOnLongClickListener(
                (final View view) -> {
                    final int[] position = new int[2];
                    key.getLocationOnScreen(position);
                    showAccentPopup(
                            key,
                            letter,
                            position[0] + key.getWidth() / 2f,
                            position[1] + key.getHeight() / 2f);
                    return true;
                });
        return key;
    }

    private double keyboardX(final float screenX) {
        final int[] position = new int[2];
        keyboard.getLocationOnScreen(position);
        return (screenX - position[0]) / keyboard.getWidth();
    }

    @Override
    public void accents(final double x, final float screenX, final float screenY) {
        showAccentPopup(letterStrip, layout.nearest(x), screenX, screenY);
    }

    private void showAccentPopup(
            final View anchor, final char letter, final float screenX, final float screenY) {
        dismissAccents();
        accentPopup = new AccentPopup(this, colors, this::appendVariation);
        accentPopup.show(anchor, letter, shift, screenX, screenY);
    }

    @Override
    public void dragAccents(final float screenX, final float screenY) {
        if (accentPopup != null) {
            accentPopup.drag(screenX, screenY);
        }
    }

    @Override
    public void finishAccents(final boolean cancelled) {
        if (accentPopup != null) {
            final AccentPopup active = accentPopup;
            accentPopup = null;
            if (cancelled) {
                active.dismiss();
            } else {
                active.release();
            }
        }
        if (cancelled && precision != null) {
            precision.setVisibility(View.GONE);
        }
    }

    private void dismissAccents() {
        finishAccents(true);
    }

    private void appendVariation(final String text) {
        dismissRevisitedWord();
        final InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        if (policy.privateField() || policy.literal()) {
            commit(text);
        } else {
            if (composingText.length() == 0) {
                composingContext = predictionContext();
            }
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
    public void precision(final double x, final boolean secondary) {
        precision.removeAllViews();
        precision.setVisibility(View.VISIBLE);
        final String letters = secondary ? SECONDARY : layout.compact();
        final int center =
                Math.max(0, Math.min(letters.length() - 1, (int) (x * letters.length())));
        for (int i = Math.max(0, center - 2);
                i <= Math.min(letters.length() - 1, center + 2);
                i++) {
            final char c = letters.charAt(i);
            final Button key =
                    addButton(
                            precision,
                            String.valueOf(c),
                            () -> precise((letters.indexOf(c) + .5) / letters.length(), secondary),
                            1);
            if (i == center) {
                highlight(key);
            }
        }
    }

    @Override
    public void precise(final double x, final boolean secondary) {
        if (secondary) {
            finishWord(false, false);
            commit(
                    String.valueOf(
                            SECONDARY.charAt(
                                    Math.max(
                                            0,
                                            Math.min(
                                                    SECONDARY.length() - 1,
                                                    (int) (x * SECONDARY.length()))))));
        } else {
            appendLetter(layout.nearest(x), true);
        }
        precision.setVisibility(View.GONE);
    }

    private void appendLetter(final char c, final boolean exact) {
        dismissRevisitedWord();
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        lastSpace = 0;
        undoRendered = "";
        if (policy.literal() || policy.privateField()) {
            commit(shift ? String.valueOf(c).toUpperCase(Locale.ROOT) : String.valueOf(c));
            shift = capsLock;
            renderKeyboard();
            return;
        }
        if (composingText.length() >= 32) {
            finishWord(false, false);
        }
        if (exact && mode == KeyboardMode.MINI) {
            preciseWord = true;
        }
        if (composingText.length() == 0) {
            composingContext = predictionContext();
        }
        composingText.append(shift ? Character.toUpperCase(c) : c);
        shift = capsLock;
        refreshCandidates();
        ic.setComposingText(composingText.toString(), 1);
        updateCase();
        updateSuggestions();
    }

    private String predictionContext() {
        if (composingText.length() > 0) {
            return composingContext;
        }
        if (policy.privateField() || policy.literal() || getCurrentInputConnection() == null) {
            return "<s>";
        }
        final CharSequence before =
                getCurrentInputConnection().getTextBeforeCursor(160 + composingText.length(), 0);
        final String rawText = before == null ? "" : before.toString();
        final String text =
                composingText.length() > 0 && rawText.endsWith(composingText.toString())
                        ? rawText.substring(0, rawText.length() - composingText.length())
                        : rawText;
        return LanguageModel.previousWord(text);
    }

    private void refreshCandidates() {
        candidates =
                mode == KeyboardMode.MINI
                                && !preciseWord
                                && touchPositions.size() == composingText.length()
                        ? decoder.forgiving(
                                touchPositions,
                                layout,
                                letterStrip == null
                                        ? 360
                                        : letterStrip.getWidth()
                                                / getResources().getDisplayMetrics().density,
                                32)
                        : decoder.full(composingText.toString());
        if (preferences.bool("missing_spaces", true)) {
            candidates = new ArrayList<>(candidates);
            candidates.addAll(
                    decoder.missingSpaces(
                            composingText.toString(),
                            predictionContext(),
                            mode == KeyboardMode.MINI));
        }
        if (languageModel != null
                && preferences.bool("contextual", true)
                && !policy.privateField()
                && !policy.literal()) {
            candidates = languageModel.rescore(candidates, predictionContext());
        }
        candidates =
                personal.rescore(
                        candidates, predictionContext(), preferences.bool("learning", false));
        if (candidates.size() > 32) {
            candidates = new ArrayList<>(candidates.subList(0, 32));
        }
    }

    private String caseWord(final String word) {
        if (word.isEmpty()) {
            return word;
        }
        if (composingText.length() == 0) {
            return capsLock
                    ? word.toUpperCase(Locale.ROOT)
                    : shift ? Character.toUpperCase(word.charAt(0)) + word.substring(1) : word;
        }
        if (capsLock
                || composingText.length() > 1
                        && composingText
                                .toString()
                                .equals(composingText.toString().toUpperCase(Locale.ROOT))) {
            return word.toUpperCase(Locale.ROOT);
        }
        return Character.isUpperCase(composingText.charAt(0))
                ? Character.toUpperCase(word.charAt(0)) + word.substring(1)
                : word;
    }

    private void updateSuggestions() {
        if (suggestions == null) {
            return;
        }
        suggestions.removeAllViews();
        suggestionScroll.scrollTo(0, 0);
        if (policy.privateField() || policy.literal()) {
            return;
        }
        if (!emoji && revisitedWord != null && composingText.length() == 0) {
            suggestions.setVisibility(View.VISIBLE);
            for (final String word : revisitedSuggestions) {
                if (!personal.isForgotten(word) || word.equals(revisitedWord.word())) {
                    addSuggestion(word);
                }
            }
            return;
        }
        if (composingText.length() == 0) {
            if (languageModel != null && preferences.bool("next_words", true) && !emoji) {
                suggestions.setVisibility(View.VISIBLE);
                for (final LanguageModel.Prediction prediction :
                        personal.next(
                                languageModel,
                                predictionContext(),
                                16,
                                preferences.bool("learning", false))) {
                    final String word = caseWord(prediction.word());
                    addSuggestion(word);
                }
            } else {
                suggestions.setVisibility(View.INVISIBLE);
            }
            return;
        }
        suggestions.setVisibility(View.VISIBLE);
        // Literal is always offered, including unknown words.
        final String literal = composingText.toString();
        final Button literalButton = addSuggestion(literal);
        if (!candidates.isEmpty() && candidates.get(0).word().equalsIgnoreCase(literal)) {
            highlight(literalButton);
        }
        for (final Decoder.Candidate c : candidates) {
            final String word = caseWord(c.word());
            if (word.equals(literal)) {
                continue;
            }
            final Button candidate = addSuggestion(word);
            if (c == candidates.get(0)) {
                highlight(candidate);
            }
        }
    }

    private Button addSuggestion(final String word) {
        final Button button =
                addButton(suggestions, word, () -> choose(word), 0, KeyRole.SUGGESTION);
        final LinearLayout.LayoutParams bounds =
                (LinearLayout.LayoutParams) button.getLayoutParams();
        bounds.width = LinearLayout.LayoutParams.WRAP_CONTENT;
        bounds.weight = 0;
        button.setLayoutParams(bounds);
        button.setOnLongClickListener(
                (final View view) -> {
                    if (!WordSpelling.valid(word)) {
                        return false;
                    }
                    final AlertDialog forgetDialog =
                            new MaterialAlertDialogBuilder(
                                            MaterialUi.themed(this, preferences.isDark()))
                                    .setTitle("Forget “" + word + "”?")
                                    .setMessage(
                                            "Remove its learned frequency and word pairs, and hide"
                                                + " it from predictions. You can still type it"
                                                + " literally. Teach it in Settings to restore it.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton(
                                            "Forget this word",
                                            (final DialogInterface dialog, final int which) -> {
                                                undoLearning = null;
                                                words.forget(word);
                                                reload();
                                                if (composingText.length() > 0) {
                                                    refreshCandidates();
                                                }
                                                dismissRevisitedWord();
                                                refreshRevisitedWord();
                                                updateSuggestions();
                                            })
                                    .create();
                    final Window window = forgetDialog.getWindow();
                    if (window != null && getWindow() != null) {
                        final WindowManager.LayoutParams attributes = window.getAttributes();
                        attributes.token = getWindow().getWindow().getDecorView().getWindowToken();
                        window.setAttributes(attributes);
                        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);
                        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
                    }
                    forgetDialog.show();
                    return true;
                });
        button.setMinWidth(dp(64));
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private void highlight(final Button button) {
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#80C7BF")));
        button.setTextColor(Color.parseColor("#183A35"));
    }

    private void choose(final String word) {
        if (getCurrentInputConnection() == null) {
            return;
        }
        if (revisitedWord != null) {
            replaceRevisitedWord(word);
            return;
        }
        final String previous = predictionContext();
        final String original = composingText.toString();
        final EditorSnapshot before = readEditorSnapshot();
        final List<String> alternatives = originalAlternatives();
        getCurrentInputConnection().commitText(word + " ", 1);
        getCurrentInputConnection().finishComposingText();
        rememberCommit(before, original, word, alternatives);
        learn(word, previous, 3);
        undoRaw = original.isEmpty() ? word + " " : original + " ";
        undoRendered = word + " ";
        undoCursor = cursorPosition();
        resetComposition();
        shift = capsLock;
        renderKeyboard();
    }

    private void finishWord(final boolean addSpace, final boolean correct) {
        if (composingText.length() == 0 || getCurrentInputConnection() == null) {
            return;
        }
        final String original = composingText.toString();
        final String word;

        // Space repairs are explicit choices and must not change single-word correction confidence.
        Decoder.Candidate best = null;
        Decoder.Candidate runnerUp = null;

        for (final Decoder.Candidate candidate : candidates) {
            if (candidate.word().contains(" ")) {
                continue;
            }
            if (best == null) {
                best = candidate;
            } else {
                runnerUp = candidate;
                break;
            }
        }
        if (correct
                && preferences.bool("correction", true)
                && !preciseWord
                && best != null
                && best.cost() <= Math.max(1, composingText.length()) * .9
                && (runnerUp == null
                        || best.score() - runnerUp.score() >= .5
                        || best.word().equalsIgnoreCase(original))) {
            word = caseWord(best.word());
        } else {
            word = original;
        }
        final String tail = addSpace ? " " : "";
        final EditorSnapshot before = readEditorSnapshot();
        final List<String> alternatives = originalAlternatives();
        getCurrentInputConnection().commitText(word + tail, 1);
        getCurrentInputConnection().finishComposingText();
        rememberCommit(before, original, word, alternatives);
        undoLearning = null;
        // Unconfirmed automatic corrections must not reinforce their own guesses.
        if (word.equalsIgnoreCase(original)) {
            learn(word, composingContext, 1);
        }
        undoRaw = original + tail;
        undoRendered = word + tail;
        undoCursor = cursorPosition();
        resetComposition();
        shift = capsLock;
    }

    private record EditorSnapshot(String text, int offset, int start, int end) {}

    private EditorSnapshot readEditorSnapshot() {
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null || policy.privateField() || policy.literal() || policy.rawKeys()) {
            return null;
        }
        final ExtractedTextRequest request = new ExtractedTextRequest();
        request.token = CORRECTION_TEXT_TOKEN;
        request.hintMaxChars = RecorrectionHistory.MAX_TEXT;
        request.hintMaxLines = 1000;
        final ExtractedText extracted =
                ic.getExtractedText(request, InputConnection.GET_EXTRACTED_TEXT_MONITOR);
        if (extracted == null
                || extracted.text == null
                || extracted.text.length() > RecorrectionHistory.MAX_TEXT) {
            return null;
        }
        return new EditorSnapshot(
                extracted.text.toString(),
                extracted.startOffset,
                extracted.startOffset + extracted.selectionStart,
                extracted.startOffset + extracted.selectionEnd);
    }

    private void dismissRevisitedWord() {
        revisitedWord = null;
        revisitedSuggestions = List.of();
    }

    private void clearCorrectionHistory() {
        dismissRevisitedWord();
        correctionHistory.clear();
        undoEvidence = null;
        undoLearning = null;
    }

    private List<String> originalAlternatives() {
        final List<String> result = new ArrayList<>();
        for (final Decoder.Candidate candidate : candidates) {
            result.add(caseWord(candidate.word()));
        }
        return result;
    }

    private void rememberCommit(
            final EditorSnapshot before,
            final String typed,
            final String word,
            final List<String> alternatives) {
        undoEvidence = null;
        if (before == null || typed.isEmpty() || before.start() != before.end()) {
            return;
        }
        correctionHistory.observe(before.text(), before.offset());
        final EditorSnapshot after = readEditorSnapshot();
        if (after == null) {
            correctionHistory.clear();
            return;
        }
        correctionHistory.observe(after.text(), after.offset());
        correctionHistory.remember(before.end() - typed.length(), word, typed, alternatives);
    }

    private static String caseLike(final String word, final String original) {
        if (original.equals(original.toUpperCase(Locale.ROOT))) {
            return word.toUpperCase(Locale.ROOT);
        }
        return Character.isUpperCase(original.charAt(0))
                ? Character.toUpperCase(word.charAt(0)) + word.substring(1)
                : word;
    }

    private void refreshRevisitedWord() {
        if (emoji || composingText.length() > 0 || policy.privateField() || policy.literal()) {
            dismissRevisitedWord();
            return;
        }
        final EditorSnapshot snapshot = readEditorSnapshot();
        if (snapshot == null) {
            clearCorrectionHistory();
            return;
        }
        correctionHistory.observe(snapshot.text(), snapshot.offset());
        final RecorrectionHistory.Target target =
                correctionHistory.target(snapshot.start(), snapshot.end());
        if (Objects.equals(target, revisitedWord)) {
            return;
        }
        dismissRevisitedWord();
        if (target == null) {
            return;
        }
        revisitedWord = target;
        final LinkedHashSet<String> options = new LinkedHashSet<>();
        options.add(target.word());
        if (target.evidence() != null) {
            options.add(target.evidence().typed());
            options.addAll(target.evidence().alternatives());
        } else {
            final List<Decoder.Candidate> unranked = decoder.full(target.word());
            final List<Decoder.Candidate> matches;
            if (languageModel != null && preferences.bool("contextual", true)) {
                final String before =
                        snapshot.text().substring(0, target.start() - snapshot.offset());
                matches = languageModel.rescore(unranked, LanguageModel.previousWord(before));
            } else {
                matches = unranked;
            }
            for (final Decoder.Candidate candidate : matches) {
                options.add(caseLike(candidate.word(), target.word()));
            }
        }
        revisitedSuggestions = List.copyOf(options);
    }

    private void replaceRevisitedWord(final String replacement) {
        final RecorrectionHistory.Target expected = revisitedWord;
        final List<String> availableAlternatives = new ArrayList<>(revisitedSuggestions);
        availableAlternatives.remove(expected.word());
        final EditorSnapshot before = readEditorSnapshot();
        if (before == null) {
            clearCorrectionHistory();
            updateSuggestions();
            return;
        }
        correctionHistory.observe(before.text(), before.offset());
        final RecorrectionHistory.Target current =
                correctionHistory.target(before.start(), before.end());
        if (!expected.equals(current)) {
            dismissRevisitedWord();
            refreshRevisitedWord();
            updateSuggestions();
            return;
        }
        final InputConnection ic = getCurrentInputConnection();
        ic.beginBatchEdit();
        boolean replaced = false;
        try {
            if (ic.setSelection(current.start(), current.end())) {
                replaced = ic.commitText(replacement, 1);
            }
        } finally {
            ic.endBatchEdit();
        }
        dismissRevisitedWord();
        if (!replaced) {
            refreshRevisitedWord();
            updateSuggestions();
            return;
        }
        undoEvidence = null;
        final EditorSnapshot after = readEditorSnapshot();
        if (after != null) {
            correctionHistory.observe(after.text(), after.offset());
            final String typed =
                    current.evidence() == null ? current.word() : current.evidence().typed();
            final List<String> alternatives =
                    current.evidence() == null
                            ? availableAlternatives
                            : current.evidence().alternatives();
            correctionHistory.remember(current.start(), replacement, typed, alternatives);
            undoEvidence =
                    new RecorrectionHistory.Entry(
                            current.start(), current.word(), typed, alternatives);
        }
        undoRaw = current.word();
        undoRendered = replacement;
        undoCursor = cursorPosition();
        lastSpace = 0;
        final String previous =
                LanguageModel.previousWord(
                        before.text().substring(0, current.start() - before.offset()));
        learn(replacement, previous, 3);
        refreshRevisitedWord();
        renderKeyboard();
    }

    private void learn(final String word, final String previous, final int weight) {
        undoLearning = null;
        final Personalization beforeLearning = words.read();
        if (!policy.privateField()
                && !policy.literal()
                && preferences.bool("learning", false)
                && words.learn(word, previous, weight)) {
            undoLearning = beforeLearning;
            reload();
        }
    }

    private void resetComposition() {
        composingText.setLength(0);
        touchPositions.clear();
        candidates = Collections.emptyList();
        preciseWord = false;
    }

    private int cursorPosition() {
        if (getCurrentInputConnection() == null || policy.privateField() || policy.rawKeys()) {
            return -1;
        }
        final ExtractedTextRequest request = new ExtractedTextRequest();
        request.hintMaxChars = 1;
        request.hintMaxLines = 1;
        final ExtractedText text = getCurrentInputConnection().getExtractedText(request, 0);
        return text == null || text.selectionStart != text.selectionEnd
                ? -1
                : text.startOffset + text.selectionEnd;
    }

    private void commit(final String text) {
        dismissRevisitedWord();
        if (getCurrentInputConnection() == null) {
            return;
        }
        lastSpace = 0;
        undoRendered = "";
        if (policy.rawKeys()) {
            for (final char c : text.toCharArray()) {
                sendKeyChar(c);
            }
        } else {
            getCurrentInputConnection().commitText(text, 1);
        }
    }

    private void space() {
        dismissRevisitedWord();
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        if (emoji) {
            editEmojiQuery(" ");
            return;
        }
        final long now = SystemClock.uptimeMillis();
        if (composingText.length() > 0) {
            finishWord(true, true);
            lastSpace = now;
            lastSpaceCursor = cursorPosition();
            renderKeyboard();
            return;
        }
        final CharSequence before = policy.privateField() ? null : ic.getTextBeforeCursor(2, 0);
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
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (emoji) {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                backspace();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                return true;
            }
            final int character = event.getUnicodeChar();
            if (character >= 32 && !event.isCtrlPressed() && !event.isAltPressed()) {
                editEmojiQuery(new String(Character.toChars(character)));
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        if (emoji
                && (keyCode == KeyEvent.KEYCODE_DEL
                        || keyCode == KeyEvent.KEYCODE_ENTER
                        || event.getUnicodeChar() >= 32
                                && !event.isCtrlPressed()
                                && !event.isAltPressed())) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void editEmojiQuery(final String text) {
        final Editable editable = emojiSearch.getText();
        final int start = Math.max(0, emojiSearch.getSelectionStart());
        final int end = Math.max(start, emojiSearch.getSelectionEnd());
        editable.replace(start, end, text);
        emojiSearch.setSelection(Math.min(emojiSearch.length(), start + text.length()));
    }

    private void searchLetter(final char letter) {
        editEmojiQuery(String.valueOf(letter));
    }

    private void updateEmojiResults() {
        if (!emojiSearch.getText().toString().contentEquals(emojiQuery)) {
            emojiSearch.setText(emojiQuery.toString());
            emojiSearch.setSelection(emojiSearch.length());
        }
        if (emojiResults == null) {
            return;
        }
        emojiResults.removeAllViews();
        final List<EmojiSearch.Entry> matches = EmojiSearch.search(emojiQuery.toString(), 96);
        LinearLayout row = null;
        for (int index = 0; index < matches.size(); index++) {
            if (index % 6 == 0) {
                row = newKeyRow();
                emojiResults.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            final EmojiSearch.Entry entry = matches.get(index);
            final Button key = addButton(row, entry.symbol(), () -> commit(entry.symbol()), 1);
            key.setTextSize(24);
            key.setContentDescription(entry.name());
        }
        if (row != null) {
            for (int index = matches.size() % 6; index > 0 && index < 6; index++) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, dp(52), 1));
            }
        }
        if (matches.isEmpty()) {
            final TextView empty = new TextView(this);
            empty.setText("No matches. Try a name such as heart, coffee, or smile.");
            empty.setTypeface(KeyboardFont.get(this));
            empty.setTextColor(textColor());
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            emojiResults.addView(empty);
        }
        keyboard.invalidate();
    }

    private void backspace() {
        dismissRevisitedWord();
        if (emoji) {
            final Editable editable = emojiSearch.getText();
            final int start = Math.max(0, emojiSearch.getSelectionStart());
            final int end = Math.max(start, emojiSearch.getSelectionEnd());
            if (end > start) {
                editable.delete(start, end);
            } else if (start > 0) {
                editable.delete(Character.offsetByCodePoints(editable, start, -1), start);
            }
            return;
        }
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
        lastSpace = 0;
        if (composingText.length() > 0) {
            composingText.deleteCharAt(composingText.length() - 1);
            if (!touchPositions.isEmpty()) {
                touchPositions.remove(touchPositions.size() - 1);
            }
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
            final CharSequence before = ic.getTextBeforeCursor(undoRendered.length(), 0);
            if (before != null && before.toString().equals(undoRendered)) {
                ic.deleteSurroundingText(undoRendered.length(), 0);
                ic.commitText(undoRaw, 1);
                if (undoLearning != null) {
                    words.save(undoLearning);
                    reload();
                    undoLearning = null;
                }
                if (undoEvidence != null) {
                    final EditorSnapshot restored = readEditorSnapshot();
                    if (restored != null) {
                        correctionHistory.observe(restored.text(), restored.offset());
                        correctionHistory.remember(
                                undoEvidence.start(),
                                undoRaw,
                                undoEvidence.typed(),
                                undoEvidence.alternatives());
                    }
                }
                undoEvidence = null;
                undoRendered = "";
                refreshRevisitedWord();
                updateSuggestions();
                return;
            }
        }
        undoRendered = "";
        if (policy.rawKeys()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
            return;
        }
        final CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
            return;
        }
        ic.deleteSurroundingTextInCodePoints(1, 0);
    }

    private void deleteWord() {
        dismissRevisitedWord();
        if (emoji && emojiQuery.length() > 0) {
            emojiQuery.setLength(0);
            updateEmojiResults();
            return;
        }
        final InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            return;
        }
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
        final CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
            return;
        }
        final CharSequence before = ic.getTextBeforeCursor(4096, 0);
        if (before == null) {
            return;
        }
        final int n = before.length();
        int i = n;

        while (i > 0 && Character.isWhitespace(before.charAt(i - 1))) {
            i--;
        }
        while (i > 0 && !Character.isWhitespace(before.charAt(i - 1))) {
            i--;
        }
        ic.deleteSurroundingText(n - i, 0);
        updateSuggestions();
    }

    private String actionLabel() {
        if (emoji) {
            return "Search";
        }
        final EditorInfo info = getCurrentInputEditorInfo();
        if (info == null) {
            return "Enter";
        }
        if ((info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return "Enter";
        }
        if (info.actionLabel != null) {
            return info.actionLabel.toString();
        }
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
        dismissRevisitedWord();
        if (emoji) {
            updateEmojiResults();
            return;
        }
        finishWord(false, true);
        lastSpace = 0;
        undoRendered = "";
        final InputConnection ic = getCurrentInputConnection();
        final EditorInfo info = getCurrentInputEditorInfo();
        if (ic == null || info == null) {
            return;
        }
        final boolean actionAllowed = (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0;
        final int action = info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (actionAllowed && info.actionLabel != null) {
            ic.performEditorAction(info.actionId);
        } else if (actionAllowed
                && action != EditorInfo.IME_ACTION_NONE
                && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else if (policy.rawKeys()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        } else {
            ic.commitText("\n", 1);
        }
        renderKeyboard();
    }
}
