// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.linekey.core.LetterLayout;

/** Emulator smoke test with real editor connections and all accessibility windows. */
public final class KeyboardSmokeTest extends Instrumentation {
  private UiAutomation automation;
  private volatile Activity activity;
  private InputMethodManager inputMethods;
  private final StringBuilder results = new StringBuilder();
  private int screenHeight, screenWidth;
  private boolean layoutAudit;
  private boolean recorrectionOnly, adaptationOnly;
  private boolean auditDark;
  private final org.json.JSONArray auditSnapshots = new org.json.JSONArray();
  private String auditDirectory;
  private org.linekey.core.Personalization savedLearning;
  private boolean savedLearningEnabled;

  @Override
  public void onCreate(Bundle arguments) {
    super.onCreate(arguments);
    layoutAudit = arguments != null && "true".equals(arguments.getString("layout_audit"));
    adaptationOnly = arguments != null && "true".equals(arguments.getString("adaptation_only"));
    recorrectionOnly = arguments != null && "true".equals(arguments.getString("recorrection_only"));
    auditDark = arguments != null && "true".equals(arguments.getString("audit_dark"));
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      automation = getUiAutomation();
      cancelTouch();
      AccessibilityServiceInfo info = automation.getServiceInfo();
      info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
      automation.setServiceInfo(info);
      Bitmap initialFrame = automation.takeScreenshot();
      screenHeight = initialFrame.getHeight();
      screenWidth = initialFrame.getWidth();
      initialFrame.recycle();
      ((android.app.Application) getTargetContext().getApplicationContext())
          .registerActivityLifecycleCallbacks(
              new android.app.Application.ActivityLifecycleCallbacks() {
                public void onActivityResumed(Activity current) {
                  if (current instanceof MainActivity) activity = current;
                }

                public void onActivityCreated(Activity current, Bundle state) {}

                public void onActivityStarted(Activity current) {}

                public void onActivityPaused(Activity current) {}

                public void onActivityStopped(Activity current) {}

                public void onActivitySaveInstanceState(Activity current, Bundle state) {}

                public void onActivityDestroyed(Activity current) {}
              });
      WordStore baselineWords = new WordStore(getTargetContext());
      savedLearning = baselineWords.read();
      savedLearningEnabled = new Preferences(getTargetContext()).bool("learning", false);
      baselineWords.clear();
      new Preferences(getTargetContext()).set("caps", true);
      // Baseline checks must not inherit adaptation from an earlier manual/test run.
      new Preferences(getTargetContext()).set("learning", false);
      new Preferences(getTargetContext()).set("gesture_distance", 48);
      new Preferences(getTargetContext()).set("floating", false);
      long dictionaryStart = System.nanoTime();
      var dictionary = new org.linekey.core.Decoder();
      results
          .append("Android dictionary entries: ")
          .append(dictionary.seedSize())
          .append(" first-load ms: ")
          .append((System.nanoTime() - dictionaryStart) / 1e6)
          .append('\n');
      java.util.List<Double> lookupTimes = new java.util.ArrayList<>();
      var runtimeLayout = new LetterLayout(0);
      for (String word : java.util.List.of("install", "keyboard", "application", "permission"))
        for (int length : new int[] {2, 4, word.length()}) {
          java.util.List<Double> samples = new java.util.ArrayList<>();
          for (char c : word.substring(0, length).toCharArray())
            samples.add(runtimeLayout.center(c));
          long started = System.nanoTime();
          dictionary.forgiving(samples, runtimeLayout, 240, 32);
          lookupTimes.add((System.nanoTime() - started) / 1e6);
        }
      java.util.Collections.sort(lookupTimes);
      results
          .append("Android compact lookup sample p50/p95 ms: ")
          .append(lookupTimes.get(lookupTimes.size() / 2))
          .append(" / ")
          .append(lookupTimes.get((int) Math.ceil(lookupTimes.size() * .95) - 1))
          .append('\n');
      check(dictionary.seedSize() >= 170000, "Android loads the expanded 170k dictionary");
      long modelStart = System.nanoTime();
      var model = org.linekey.core.LanguageModel.get();
      results
          .append("Android model first-load ms: ")
          .append((System.nanoTime() - modelStart) / 1e6)
          .append('\n');
      long lookupStart = System.nanoTime();
      for (int index = 0; index < 10000; index++)
        model.next(index % 2 == 0 ? "according" : "unknown-context", 3);
      results
          .append("Android mean prediction lookup ms: ")
          .append((System.nanoTime() - lookupStart) / 1e6 / 10000)
          .append('\n');
      activity =
          startActivitySync(
              new Intent(getTargetContext(), MainActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      inputMethods = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);

      if (!layoutAudit) checkMaterialDocuments();
      EditText editor = findEditor("Type hello world here");
      runOnMainSync(() -> editor.setText(""));
      focus(editor);
      shell("ime enable org.linekey.keyboard/.LineKeyIme");
      shell("ime set org.linekey.keyboard/.LineKeyIme");
      focus(editor);
      SystemClock.sleep(400);
      if (layoutAudit) {
        auditAllLayouts(editor);
        result.putString("stream", "\n" + results + "PASS: layout audit completed\n");
        finish(Activity.RESULT_OK, result);
        return;
      }
      if (adaptationOnly) {
        checkAdaptation(editor);
        result.putString("stream", "\n" + results + "PASS: adaptation checks completed\n");
        finish(Activity.RESULT_OK, result);
        return;
      }
      if (recorrectionOnly) {
        checkRecorrection(editor);
        result.putString("stream", "\n" + results + "PASS: recorrection checks completed\n");
        finish(Activity.RESULT_OK, result);
        return;
      }
      AccessibilityNodeInfo strip = waitForStrip();
      Rect bounds = new Rect();
      strip.getBoundsInScreen(bounds);
      results.append("Compact strip bounds: ").append(bounds).append('\n');
      LetterLayout layout = new LetterLayout(0);
      check(
          strip.getContentDescription().toString().contains("Uppercase"),
          "Compact letters match initial uppercase input");
      for (char letter : "hello".toCharArray()) {
        tap(bounds.left + (float) layout.center(letter) * bounds.width(), bounds.exactCenterY());
      }
      check(
          findStrip().getContentDescription().toString().contains("Lowercase"),
          "Compact labels switch to lowercase after initial capital");
      press("Space");
      check(readText(editor).equals("Hello "), "Compact input commits Hello: " + readText(editor));
      Rect spaceBounds = new Rect();
      waitForKey("Space").getBoundsInScreen(spaceBounds);
      float density = getTargetContext().getResources().getDisplayMetrics().density;
      check(
          spaceBounds.bottom <= screenHeight - Math.round(20 * density),
          "Bottom actions clear the system navigation controls: " + spaceBounds);
      check(
          spaceBounds.bottom - bounds.top <= Math.round(150 * density),
          "Compact strip and bottom actions stay small");
      check(
          findImeText("LineKey · English demo · hold for precision") == null,
          "Idle demo branding removed");
      Bitmap spaceFrame = automation.takeScreenshot();
      int spaceColor =
          spaceFrame.getPixel(spaceBounds.centerX(), spaceBounds.top + Math.round(5 * density));
      check(spaceColor == Color.parseColor("#80C7BF"), "Space bar fills the bottom row height");
      spaceFrame.recycle();
      Rect modeBounds = new Rect();
      waitForModeButton().getBoundsInScreen(modeBounds);
      Bitmap roles = automation.takeScreenshot();
      int inputColor = roles.getPixel(bounds.left + 2, bounds.top + 2);
      check(
          roles.getPixel(modeBounds.centerX(), modeBounds.top + Math.round(8 * density))
              != inputColor,
          "Action buttons are visually distinct from standard input");
      roles.recycle();
      screenshot("compact.png");

      strip = waitForStrip();
      strip.getBoundsInScreen(bounds);
      hold(
          bounds.left + (float) layout.center('a') * bounds.width(),
          bounds.top + (float) layout.vertical('a') * bounds.height(),
          bounds.left + (float) layout.center('z') * bounds.width(),
          bounds.exactCenterY(),
          200);
      check(readText(editor).equals("Hello z"), "Hold and release selects a precise letter");
      press("⌫");
      strip = waitForStrip();
      strip.getBoundsInScreen(bounds);
      hold(
          bounds.left + bounds.width() / 34f,
          bounds.exactCenterY(),
          bounds.left + bounds.width() / 34f,
          bounds.top - 80,
          200);
      check(readText(editor).equals("Hello 1"), "Hold and drag upward inserts a number");
      press("⌫");
      strip = waitForStrip();
      strip.getBoundsInScreen(bounds);
      holdAccent(
          bounds.left + (float) layout.center('e') * bounds.width(),
          bounds.exactCenterY(),
          "é",
          false);
      check(
          readText(editor).equals("Hello é"),
          "Hold drag and release inserts an accent in one touch");
      check(findImeNode("Accent é", true) == null, "Accent popup closes on release");
      press("⌫");
      strip = waitForStrip();
      strip.getBoundsInScreen(bounds);
      String beforeCancel = readText(editor);
      holdAccent(
          bounds.left + (float) layout.center('e') * bounds.width(),
          bounds.exactCenterY(),
          "é",
          true);
      check(readText(editor).equals(beforeCancel), "Dragging away cancels accent insertion");

      waitForModeButton().performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
      waitForIdleSync();
      press("Full");
      Rect fullKey = new Rect();
      waitForKey("q").getBoundsInScreen(fullKey);
      Bitmap expandedPalette = automation.takeScreenshot();
      check(
          expandedPalette.getPixel(fullKey.centerX(), fullKey.top + Math.round(8 * density))
              == inputColor,
          "Compact and full keyboards use the same input colors");
      expandedPalette.recycle();
      for (char letter : "world".toCharArray()) press(String.valueOf(letter));
      press("Space");
      check(
          readText(editor).equals("Hello world "), "Full input commits world: " + readText(editor));
      Rect expandedSpace = new Rect();
      waitForKey("Space").getBoundsInScreen(expandedSpace);
      check(
          expandedSpace.bottom <= screenHeight - Math.round(48 * density),
          "Expanded actions clear system buttons");
      checkModeCycle(editor);
      checkReferencePages(editor);
      returnToMini();
      waitForStrip();
      waitForModeButton().performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
      waitForIdleSync();
      press("Full");
      screenshot("full.png");

      press("Emoji menu");
      String beforeSearch = readText(editor);
      for (char letter : "coffee".toCharArray()) press(String.valueOf(letter));
      check(findImeText("coffee") != null, "Emoji query is visible in search bar");
      check(findImeText("q") != null, "Top emoji query row stays available after filtering");
      Rect queryKey = new Rect();
      waitForKey("q").getBoundsInScreen(queryKey);
      Bitmap queryFrame = automation.takeScreenshot();
      int queryPixel = queryFrame.getPixel(queryKey.centerX(), queryKey.top + 16);
      check(
          queryPixel
              == Color.parseColor(
                  new Preferences(getTargetContext()).isDark() ? "#182D30" : "#DFEBE8"),
          "Filtered emoji query keys remain painted: "
              + queryKey
              + " pixel="
              + Integer.toHexString(queryPixel));
      queryFrame.recycle();
      check(readText(editor).equals(beforeSearch), "Emoji search does not modify host editor");
      press("Search");
      check(readText(editor).equals(beforeSearch), "Search action preserves host message");
      press("☕");
      check(readText(editor).equals(beforeSearch + "☕"), "Search result inserts coffee emoji");
      press("⌫");
      check(findImeText("coffe") != null, "Delete edits emoji query only");
      check(readText(editor).equals(beforeSearch + "☕"), "Query deletion preserves host text");
      screenshot("emoji-search.png");
      for (int index = 0; index < 5; index++) press("⌫");
      for (char letter : "cofee".toCharArray()) press(String.valueOf(letter));
      check(findImeText("☕") != null, "Misspelled emoji query finds coffee");
      for (int index = 0; index < 5; index++) press("⌫");
      shell("input text dog");
      SystemClock.sleep(200);
      check(findImeText("dog") != null, "Hardware typing edits the emoji search field");
      check(readText(editor).equals(beforeSearch + "☕"), "Hardware search preserves host message");
      press("ABC");

      EditText password = findEditor("Password · no suggestions or learning");
      focus(password);
      waitForKey("q");
      check(findStrip() == null, "Password uses full keyboard");
      check(findImeNode("Emoji menu", true) == null, "Password hides emoji shortcut");
      check(
          findImeText("LineKey · English demo · full keyboard") == null,
          "Password hides suggestion hint");
      screenshot("password.png");
      checkSpecialFields();

      MaterialAutoCompleteTextView theme = findThemeSelector(activity.getWindow().getDecorView());
      for (int index = 0; index < 4; index++) {
        boolean dark = index % 2 == 1;
        runOnMainSync(
            () -> {
              selectTheme(theme, dark ? 1 : 0);
              editor.requestFocus();
              inputMethods.hideSoftInputFromWindow(editor.getWindowToken(), 0);
            });
        waitForIdleSync();
        SystemClock.sleep(400);
        focus(editor);
        strip = waitForStrip();
        strip.getBoundsInScreen(bounds);
        Bitmap image = automation.takeScreenshot();
        int actual = image.getPixel(bounds.left + 2, bounds.top + 2);
        int expected = Color.parseColor(dark ? "#182D30" : "#DFEBE8");
        check(
            actual == expected,
            "Theme toggle "
                + (index + 1)
                + " keeps compact renderer and applies color (actual="
                + Integer.toHexString(actual)
                + ", expected="
                + Integer.toHexString(expected)
                + ")");
        image.recycle();
      }
      runOnMainSync(() -> editor.setText(""));
      focus(editor);
      strip = waitForStrip();
      strip.getBoundsInScreen(bounds);
      checkOmittedPunctuation(editor);
      checkRecorrection(editor);
      checkAdaptation(editor);
      runOnMainSync(
          () -> {
            editor.setText("");
            inputMethods.restartInput(editor);
          });
      focus(editor);
      SystemClock.sleep(350);
      strip = waitForStrip();
      strip.getBoundsInScreen(bounds);
      for (char letter : "ankrj".toCharArray())
        tap(bounds.left + (float) layout.center(letter) * bounds.width(), bounds.exactCenterY());
      check(
          readText(editor).equalsIgnoreCase("ankrj"),
          "Sloppy fixture transcribes ankrj: " + readText(editor));
      check(
          findImeText("Quick") != null || findImeText("quick") != null,
          "Sloppy ankrj exposes quick among visible context-ranked suggestions");
      swipe(
          bounds.exactCenterX(),
          bounds.exactCenterY(),
          bounds.exactCenterX() - 140,
          bounds.exactCenterY());
      for (String raw : java.util.List.of("insgzoo", "inxtzoo")) {
        for (char letter : raw.toCharArray())
          tap(bounds.left + (float) layout.center(letter) * bounds.width(), bounds.exactCenterY());
        check(
            findImeText("Install") != null || findImeText("install") != null,
            "Expanded vocabulary visibly suggests install for " + raw);
        swipe(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            bounds.exactCenterX() - 140,
            bounds.exactCenterY());
      }
      for (char letter : "there".toCharArray())
        tap(bounds.left + (float) layout.center(letter) * bounds.width(), bounds.exactCenterY());
      AccessibilityNodeInfo suggestionList = findImeNode("Word suggestions.", true);
      check(
          suggestionList != null && suggestionList.isScrollable(),
          "Suggestions expose a horizontal scrolling list");
      check(
          suggestionList.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD),
          "Additional matches scroll into view");
      waitForIdleSync();
      SystemClock.sleep(300);
      suggestionList = findImeNode("Word suggestions.", true);
      check(
          (suggestionList.getActions() & AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) != 0,
          "Suggestions can scroll back to the best matches");
      swipe(
          bounds.exactCenterX(),
          bounds.exactCenterY(),
          bounds.exactCenterX() - 140,
          bounds.exactCenterY());
      for (char letter : "dog".toCharArray())
        tap(bounds.left + (float) layout.center(letter) * bounds.width(), bounds.exactCenterY());
      swipe(
          bounds.exactCenterX(),
          bounds.exactCenterY(),
          bounds.exactCenterX() + 140,
          bounds.exactCenterY());
      check(
          readText(editor).equalsIgnoreCase("dog "),
          "Left-to-right swipe confirms a word and inserts space: " + readText(editor));
      strip = waitForStrip();
      strip.getBoundsInScreen(bounds);
      tap(bounds.left + (float) layout.center('x') * bounds.width(), bounds.exactCenterY());
      swipe(
          bounds.exactCenterX(),
          bounds.exactCenterY(),
          bounds.exactCenterX() - 140,
          bounds.exactCenterY());
      check(
          readText(editor).equalsIgnoreCase("dog "),
          "Right-to-left swipe erases the composing word");
      runOnMainSync(() -> editor.getText().replace(0, editor.length(), "one two three  "));
      waitForIdleSync();
      for (String remaining : new String[] {"one two ", "one ", "", ""}) {
        swipe(
            bounds.exactCenterX(),
            bounds.exactCenterY(),
            bounds.exactCenterX() - 140,
            bounds.exactCenterY());
        check(
            readText(editor).equals(remaining),
            "Repeated right-to-left swipe removes one committed word: " + remaining);
      }
      swipe(
          bounds.exactCenterX(),
          bounds.exactCenterY(),
          bounds.exactCenterX(),
          bounds.exactCenterY() - 140);
      waitForKey("q");
      check(findStrip() == null, "Swipe up expands to full keyboard");
      waitForModeButton().performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
      waitForIdleSync();
      press("Mini");
      strip = waitForStrip();
      strip.getBoundsInScreen(bounds);
      swipe(
          bounds.exactCenterX(),
          bounds.exactCenterY(),
          bounds.exactCenterX(),
          bounds.exactCenterY() + 140);
      SystemClock.sleep(250);
      check(findStrip() == null, "Swipe down hides the keyboard");
      focus(editor);
      waitForStrip();
      checkNewTools(editor);
      checkSystemTheme();
      result.putString("stream", "\n" + results + "PASS: emulator smoke checks completed\n");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable failure) {
      cancelTouch();
      try {
        screenshot("failure.png");
      } catch (Exception ignored) {
      }
      result.putString("stream", "\n" + results + "FAIL: " + failure + "\n");
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  private AccessibilityNodeInfo waitForModeButton() {
    for (int index = 0; index < 50; index++) {
      var button = findImeNode("Keyboard mode.", true);
      if (button != null) return button;
      SystemClock.sleep(100);
    }
    throw new AssertionError("Mode button not visible");
  }

  @Override
  public void finish(int resultCode, Bundle result) {
    if (savedLearning != null) {
      new WordStore(getTargetContext()).save(savedLearning);
      new Preferences(getTargetContext()).set("learning", savedLearningEnabled);
    }
    super.finish(resultCode, result);
  }

  private AccessibilityNodeInfo findSuggestion(String word) {
    AccessibilityNodeInfo list = findImeNode("Word suggestions.", true);
    for (int page = 0;
        list != null
            && page < 25
            && (list.getActions() & AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) != 0;
        page++) {
      if (!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break;
      waitForIdleSync();
      SystemClock.sleep(75);
      list = findImeNode("Word suggestions.", true);
    }
    for (int page = 0; page < 40; page++) {
      AccessibilityNodeInfo candidate = findImeText(word);
      if (candidate == null)
        candidate = findImeText(Character.toUpperCase(word.charAt(0)) + word.substring(1));
      if (candidate != null) return candidate;
      list = findImeNode("Word suggestions.", true);
      if (list == null || !list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) break;
      waitForIdleSync();
      SystemClock.sleep(75);
    }
    throw new AssertionError("Suggestion did not appear: " + word);
  }

  private void selectSuggestion(String word) {
    check(
        findSuggestion(word).performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Choose suggestion " + word);
    waitForIdleSync();
    SystemClock.sleep(250);
  }

  private void tapEditorWord(EditText editor, int offset) {
    float[] point = new float[2];
    runOnMainSync(
        () -> {
          int[] location = new int[2];
          editor.getLocationOnScreen(location);
          android.text.Layout text = editor.getLayout();
          int line = text.getLineForOffset(offset);
          point[0] =
              location[0]
                  + editor.getTotalPaddingLeft()
                  + text.getPrimaryHorizontal(offset)
                  - editor.getScrollX();
          point[1] =
              location[1]
                  + editor.getTotalPaddingTop()
                  + (text.getLineTop(line) + text.getLineBottom(line)) / 2f
                  - editor.getScrollY();
        });
    tap(point[0], point[1]);
    SystemClock.sleep(250);
  }

  private void checkAdaptation(EditText editor) {
    WordStore store = new WordStore(getTargetContext());
    var saved = store.read();
    Preferences prefs = new Preferences(getTargetContext());
    boolean oldLearning = prefs.bool("learning", false);
    try {
      store.clear();
      prefs.set("learning", true);
      check(store.learn("qzxlinekey", "use", 3), "Persist personal context");
      var roundTrip = new WordStore(getTargetContext()).read();
      check(roundTrip.pairs().get("use\tqzxlinekey") == 3, "Word-pair counts survive store reload");
      runOnMainSync(() -> editor.setText(""));
      runOnMainSync(() -> inputMethods.restartInput(editor));
      focus(editor);
      SystemClock.sleep(250);
      returnToMini();
      pressMode();
      typeFullLetters("hellp");
      press("Space");
      check(
          readText(editor).equalsIgnoreCase("hello "),
          "Learning-enabled Space still corrects an unconfirmed guess");
      check(
          !store.read().words().containsKey("hello"),
          "An unconfirmed autocorrection never reinforces its own guess");
      for (boolean full : new boolean[] {false, true}) {
        runOnMainSync(
            () -> {
              editor.setText("");
              inputMethods.restartInput(editor);
            });
        focus(editor);
        SystemClock.sleep(250);
        returnToMini();
        if (full) pressMode();
        if (full) typeFullLetters("helloworld");
        else {
          Rect strip = new Rect();
          waitForStrip().getBoundsInScreen(strip);
          for (char c : "helloworld".toCharArray())
            tap(
                strip.left + (float) new LetterLayout(0).center(c) * strip.width(),
                strip.exactCenterY());
        }
        check(
            readText(editor).equalsIgnoreCase("helloworld"),
            "Spacing fixture transcribes joined letters: " + readText(editor));
        selectSuggestion("hello world");
        check(
            readText(editor).equalsIgnoreCase("hello world "),
            "Missing-space phrase inserts in " + (full ? "full" : "Mini"));
        check(
            store.read().pairs().containsKey("hello\tworld"),
            "Chosen phrase learns adjacent tokens");
        Rect delete = new Rect();
        waitForKey("⌫").getBoundsInScreen(delete);
        tap(delete.exactCenterX(), delete.exactCenterY());
        check(readText(editor).equalsIgnoreCase("helloworld "), "Undo restores joined letters");
        check(!store.read().pairs().containsKey("hello\tworld"), "Undo rolls back phrase learning");
      }
      runOnMainSync(
          () -> {
            editor.setText("use ");
            editor.setSelection(editor.length());
          });
      focus(editor);
      returnToMini();
      AccessibilityNodeInfo prediction = findSuggestion("qzxlinekey");
      check(prediction != null, "Learned word pair contributes a next-word suggestion");
      selectSuggestion("qzxlinekey");
      check(
          store.read().pairs().get("use\tqzxlinekey") == 6
              && !store.read().pairs().containsKey("qzxlinekey\tqzxlinekey"),
          "Choosing a next word learns the preceding context, not the inserted word itself");
      runOnMainSync(
          () -> {
            editor.setText("use ");
            editor.setSelection(editor.length());
          });
      focus(editor);
      returnToMini();
      prediction = findSuggestion("qzxlinekey");
      check(
          prediction.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK),
          "Suggestion supports long press");
      SystemClock.sleep(300);
      AccessibilityNodeInfo forget = null;
      for (AccessibilityWindowInfo window : automation.getWindows()) {
        AccessibilityNodeInfo found = findNode(window.getRoot(), "Forget this word", false);
        if (found != null) forget = found;
      }
      check(forget != null, "Material forget dialog opens from IME window");
      forget.performAction(AccessibilityNodeInfo.ACTION_CLICK);
      waitForIdleSync();
      SystemClock.sleep(300);
      check(store.read().isForgotten("qzxlinekey"), "Forget preference persists");
      check(
          !store.read().words().containsKey("qzxlinekey")
              && !store.read().pairs().containsKey("use\tqzxlinekey"),
          "Forget removes learned frequency and context");
      boolean stillSuggested = false;
      for (AccessibilityWindowInfo window : automation.getWindows()) {
        if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
        stillSuggested |=
            findNode(window.getRoot(), "qzxlinekey", false) != null
                || findNode(window.getRoot(), "Qzxlinekey", false) != null;
      }
      check(!stillSuggested, "Forgotten next-word suggestion disappears");
      check(store.learn("qzxlinekey"), "Manual teaching restores forgotten word");
      check(
          !store.read().isForgotten("qzxlinekey"),
          "Manual restoration clears forgotten preference");
      store.forget("i");
      check(
          store.learn("I") && !store.read().isForgotten("i"),
          "Manual teaching restores one-letter words too");
      prefs.set("learning", false);
      int disabledWords = store.size(), disabledPairs = store.read().pairs().size();
      runOnMainSync(() -> editor.setText(""));
      focus(editor);
      returnToMini();
      pressMode();
      typeFullLetters("hello");
      selectSuggestion("hello");
      check(
          store.size() == disabledWords && store.read().pairs().size() == disabledPairs,
          "Learning disabled prevents token and pair collection");
      prefs.set("learning", true);
      int beforePrivate = store.size(), pairsBeforePrivate = store.read().pairs().size();
      EditText password = findEditor("Password · no suggestions or learning");
      focus(password);
      waitForKey("q");
      typeFullLetters("secret");
      press("Space");
      check(
          store.size() == beforePrivate && store.read().pairs().size() == pairsBeforePrivate,
          "Password input never learns tokens or context");
      store.clear();
      check(
          store.read().words().isEmpty()
              && store.read().pairs().isEmpty()
              && store.read().forgotten().isEmpty(),
          "Erase clears all adaptation data");
    } finally {
      store.save(saved);
      prefs.set("learning", oldLearning);
      runOnMainSync(() -> editor.setText(""));
      focus(editor);
      returnToMini();
    }
  }

  private void checkRecorrection(EditText editor) {
    runOnMainSync(() -> editor.setText(""));
    focus(editor);
    returnToMini();
    Rect strip = new Rect();
    waitForStrip().getBoundsInScreen(strip);
    double[] samples = {
      .1799276459113081,
      .08584676992198302,
      .48265731518434524,
      .2574006819782018,
      .33315713397792074,
      .6464690049366381
    };
    for (double x : samples) tap(strip.left + (float) x * strip.width(), strip.exactCenterY());
    check(readText(editor).equalsIgnoreCase("sztech"), "Recorrection fixture transcribes sztech");
    check(findSuggestion("watch") != null, "Original sztech suggestions include watch");
    selectSuggestion("eaten");
    check(readText(editor).equalsIgnoreCase("eaten "), "Initially choose eaten from sztech");
    waitForStrip().getBoundsInScreen(strip);
    for (char c : "now".toCharArray())
      tap(strip.left + (float) new LetterLayout(0).center(c) * strip.width(), strip.exactCenterY());
    selectSuggestion("now");
    check(
        readText(editor).equalsIgnoreCase("eaten now "), "Later typing follows the initial choice");
    tapEditorWord(editor, 2);
    check(findSuggestion("sztech") != null, "Tapping eaten restores its original literal input");
    selectSuggestion("watch");
    check(
        readText(editor).equalsIgnoreCase("watch now "),
        "Mini recorrection replaces only eaten, preserving later words and spacing: "
            + readText(editor));
    Rect delete = new Rect();
    waitForKey("⌫").getBoundsInScreen(delete);
    tap(delete.exactCenterX(), delete.exactCenterY());
    check(
        readText(editor).equalsIgnoreCase("eaten now "),
        "Delete undoes a recorrection to the previous chosen word");
    runOnMainSync(() -> editor.setSelection(0, 5));
    waitForIdleSync();
    SystemClock.sleep(250);
    if (findStrip() != null) pressMode();
    check(findStrip() == null, "Recorrection also runs in full alphabet mode");
    selectSuggestion("watch");
    check(
        readText(editor).equalsIgnoreCase("watch now "),
        "Whole-word selection can reuse watch after undo");
    selectSuggestion("watery");
    check(
        readText(editor).equalsIgnoreCase("watery now "),
        "Longer replacement preserves the following word");
    selectSuggestion("watch");
    check(readText(editor).equalsIgnoreCase("watch now "), "Shorter replacement preserves spacing");
    runOnMainSync(() -> editor.getText().insert(0, "Please "));
    waitForIdleSync();
    SystemClock.sleep(250);
    runOnMainSync(() -> editor.getText().append("eaten "));
    waitForIdleSync();
    SystemClock.sleep(250);
    tapEditorWord(editor, 9);
    check(findSuggestion("sztech") != null, "An edit before the word rebases original evidence");
    selectSuggestion("eaten");
    check(
        readText(editor).equalsIgnoreCase("Please eaten now eaten "),
        "Replacing an earlier duplicate preserves the later identical word: " + readText(editor));
    int lastWord = readText(editor).toLowerCase(java.util.Locale.ROOT).lastIndexOf("eaten");
    tapEditorWord(editor, lastWord + 2);
    check(
        findImeText("Sztech") == null && findImeText("sztech") == null,
        "Another eaten occurrence does not inherit the first word's original trace");
    tapEditorWord(editor, 9);
    check(findSuggestion("sztech") != null, "First occurrence still retains its alternatives");
    runOnMainSync(
        () -> {
          editor.getText().replace(8, 9, "x");
          editor.setSelection(9);
        });
    waitForIdleSync();
    SystemClock.sleep(250);
    check(
        findImeText("Sztech") == null && findImeText("sztech") == null,
        "Editing inside the word invalidates stale original evidence");
    focus(findEditor("Password · no suggestions or learning"));
    waitForKey("q");
    check(
        findImeText("Sztech") == null && findImeText("sztech") == null,
        "Private editor does not expose recorrection evidence");
    runOnMainSync(
        () -> {
          editor.setText("hellp, tail");
          editor.setSelection(2);
        });
    focus(editor);
    waitForStrip();
    tapEditorWord(editor, 2);
    selectSuggestion("hello");
    check(
        readText(editor).equals("hello, tail"),
        "Untracked text offers spelling corrections without changing punctuation or spacing");
    runOnMainSync(() -> editor.setText(""));
    focus(editor);
    waitForStrip();
    pressMode();
    typeFullLetters("hellp");
    press("Space");
    check(
        readText(editor).equalsIgnoreCase("hello "),
        "Space automatically corrects hellp: " + readText(editor));
    tapEditorWord(editor, 2);
    check(
        findSuggestion("hellp") != null, "Automatic correction also preserves its original input");
    selectSuggestion("help");
    check(
        readText(editor).equalsIgnoreCase("help "),
        "Automatic correction alternatives can replace the word later");
    runOnMainSync(() -> editor.setText(""));
    focus(editor);
    waitForStrip();
  }

  // Suggestion labels can also be single letters; select the actual alphabet row.
  private AccessibilityNodeInfo findLetterBelow(AccessibilityNodeInfo node, char letter, int top) {
    if (node == null) return null;
    Rect bounds = new Rect();
    node.getBoundsInScreen(bounds);
    if (node.getText() != null
        && node.getText().toString().equalsIgnoreCase(String.valueOf(letter))
        && node.isVisibleToUser()
        && bounds.top >= top
        && !bounds.isEmpty()) return node;
    for (int index = 0; index < node.getChildCount(); index++) {
      AccessibilityNodeInfo found = findLetterBelow(node.getChild(index), letter, top);
      if (found != null) return found;
    }
    return null;
  }

  private void typeFullLetters(String letters) {
    for (char c : letters.toCharArray()) {
      AccessibilityNodeInfo key = null;
      for (int attempt = 0; attempt < 30 && key == null; attempt++) {
        Rect header = new Rect();
        AccessibilityNodeInfo suggestions = findImeNode("Word suggestions.", true);
        if (suggestions != null) suggestions.getBoundsInScreen(header);
        for (AccessibilityWindowInfo window : automation.getWindows())
          if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
            key = findLetterBelow(window.getRoot(), c, header.bottom);
        if (key == null) SystemClock.sleep(100);
      }
      if (key == null) throw new AssertionError("Missing full alphabet letter " + c);
      Rect keyBounds = new Rect();
      key.getBoundsInScreen(keyBounds);
      tap(keyBounds.exactCenterX(), keyBounds.exactCenterY());
    }
  }

  private void checkOmittedPunctuation(EditText editor) {
    LetterLayout layout = new LetterLayout(0);
    for (boolean full : new boolean[] {false, true}) {
      for (String spelling :
          java.util.List.of("don't", "well-known", "rock'n'roll", "u.s.a.", "and/or", "r&d")) {
        runOnMainSync(() -> editor.setText(""));
        focus(editor);
        String letters = org.linekey.core.WordSpelling.letters(spelling);
        if (full) {
          // Showing the editor can restart input and restore its default Mini mode.
          SystemClock.sleep(200);
          if (findStrip() != null) pressMode();
          check(findStrip() == null, "Omission test is using full alphabet mode");
          typeFullLetters(letters);
        } else {
          Rect stripBounds = new Rect();
          waitForStrip().getBoundsInScreen(stripBounds);
          for (char c : letters.toCharArray())
            tap(
                stripBounds.left + (float) layout.center(c) * stripBounds.width(),
                stripBounds.exactCenterY());
        }
        AccessibilityNodeInfo candidate = null;
        for (int page = 0; page < 20; page++) {
          candidate = findImeText(spelling);
          if (candidate == null)
            candidate =
                findImeText(Character.toUpperCase(spelling.charAt(0)) + spelling.substring(1));
          if (candidate != null) break;
          AccessibilityNodeInfo list = findImeNode("Word suggestions.", true);
          if (list == null || !list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
            break;
          waitForIdleSync();
          SystemClock.sleep(100);
        }
        check(
            candidate != null, (full ? "Full" : "Compact") + " offers punctuation for " + letters);
        check(candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Select " + spelling);
        waitForIdleSync();
        check(
            readText(editor).equalsIgnoreCase(spelling + " "),
            "Candidate inserts punctuated spelling: " + readText(editor));
        // Choosing a word rebuilds the header; wait for its new Delete view, then
        // inject a real tap rather than invoking an accessibility node being replaced.
        SystemClock.sleep(200);
        Rect deleteBounds = new Rect();
        waitForKey("⌫").getBoundsInScreen(deleteBounds);
        tap(deleteBounds.exactCenterX(), deleteBounds.exactCenterY());
        check(
            readText(editor).equalsIgnoreCase(letters + " "),
            "Correction undo restores omitted punctuation input");
      }
      runOnMainSync(() -> editor.setText(""));
      focus(editor);
      if (full) returnToMini();
    }
    runOnMainSync(
        () -> {
          editor.setText("one mother-in-law rock'n'roll u.s.a. ");
          editor.setSelection(editor.length());
        });
    focus(editor);
    Rect bounds = new Rect();
    waitForStrip().getBoundsInScreen(bounds);
    for (String remaining :
        new String[] {"one mother-in-law rock'n'roll ", "one mother-in-law ", "one ", ""}) {
      swipe(
          bounds.exactCenterX(),
          bounds.exactCenterY(),
          bounds.exactCenterX() - 140,
          bounds.exactCenterY());
      check(
          readText(editor).equals(remaining),
          "Word swipe deletes a whole punctuated word: " + readText(editor));
    }
  }

  private void pressMode() {
    check(
        waitForModeButton().performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Mode cycle accepts tap");
    waitForIdleSync();
    SystemClock.sleep(150);
  }

  private void returnToMini() {
    for (int index = 0; index < 4 && findStrip() == null; index++) pressMode();
    waitForStrip();
  }

  private int countDescription(AccessibilityNodeInfo node, String prefix) {
    if (node == null) return 0;
    int count =
        node.getContentDescription() != null
                && node.getContentDescription().toString().startsWith(prefix)
            ? 1
            : 0;
    for (int index = 0; index < node.getChildCount(); index++)
      count += countDescription(node.getChild(index), prefix);
    return count;
  }

  private void checkIconPainted(String description, int color) {
    SystemClock.sleep(200);
    Rect bounds = new Rect();
    findImeNode(description, true).getBoundsInScreen(bounds);
    Bitmap image = automation.takeScreenshot();
    int pixels = 0;
    for (int y = bounds.top + bounds.height() / 5; y < bounds.bottom - bounds.height() / 5; y++)
      for (int x = bounds.left + bounds.width() / 5; x < bounds.right - bounds.width() / 5; x++)
        if (image.getPixel(x, y) == color) pixels++;
    image.recycle();
    check(pixels > 20, "Vector icon is visibly painted: " + description + " pixels=" + pixels);
  }

  private void checkModeCycle(EditText editor) throws Exception {
    check(findImeText("Compact") == null, "Full header has no Compact shortcut");
    int deletes = 0;
    for (var window : automation.getWindows())
      if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        deletes += countDescription(window.getRoot(), "Delete.");
    check(deletes == 1, "Full keyboard has one body Delete control");
    Rect delete = new Rect(), letter = new Rect();
    findImeNode("Delete.", true).getBoundsInScreen(delete);
    waitForKey("q").getBoundsInScreen(letter);
    check(delete.top > letter.bottom, "Delete is below the full alphabet header");
    var shiftKey = findImeNode("Shift.", true);
    check(
        shiftKey != null
            && !shiftKey.isSelected()
            && (shiftKey.getText() == null || shiftKey.getText().length() == 0),
        "Shift uses an icon without visible state text");
    shiftKey.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
    waitForIdleSync();
    check(
        findImeNode("Shift.", true).isSelected()
            && findImeNode("Shift.", true).getContentDescription().toString().contains("Caps lock"),
        "Caps Lock is visually selected and accessible");
    checkIconPainted("Shift.", Color.parseColor("#183A35"));
    screenshot("caps-lock.png");
    press("Z");
    check(findImeNode("Shift.", true).isSelected(), "Caps Lock stays selected after a letter");
    press("⌫");
    findImeNode("Shift.", true).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    waitForIdleSync();
    check(
        findImeText("q") != null && !findImeNode("Shift.", true).isSelected(),
        "Shift returns to lowercase without text labels");
    checkIconPainted(
        "Shift.",
        Color.parseColor(new Preferences(getTargetContext()).isDark() ? "#F0FAF8" : "#122D2A"));
    checkIconPainted("Enter", Color.parseColor("#183A35"));
    int foreground =
        Color.parseColor(new Preferences(getTargetContext()).isDark() ? "#F0FAF8" : "#122D2A");
    checkIconPainted("Voice input", foreground);
    checkIconPainted("Emoji menu", foreground);
    var enter = findImeNode("Enter", true);
    check(
        enter != null && (enter.getText() == null || enter.getText().length() == 0),
        "Enter uses an editor-aware vector icon");
    check(
        waitForModeButton().getText().toString().equals("123"),
        "Alphabet mode button offers numbers");
    pressMode();
    waitForKey("1");
    check(
        findStrip() == null && waitForModeButton().getText().toString().equals("#+="),
        "Numbers use full layout and offer symbols");
    press("1");
    press("⌫");
    screenshot("numbers.png");
    pressMode();
    waitForKey("€");
    check(
        findStrip() == null && waitForModeButton().getText().toString().equals("Mini"),
        "Symbols use full layout and offer Mini");
    press("€");
    press("⌫");
    screenshot("symbols.png");
    pressMode();
    waitForStrip();
    check(waitForModeButton().getText().toString().equals("ABC"), "Mini mode offers full alphabet");
    check(readText(editor).equals("Hello world "), "Mode cycling preserves editor text");
  }

  private void menu(String item) {
    waitForModeButton().performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
    waitForIdleSync();
    press(item);
  }

  private int selection(EditText editor) {
    java.util.concurrent.atomic.AtomicInteger result =
        new java.util.concurrent.atomic.AtomicInteger();
    runOnMainSync(() -> result.set(editor.getSelectionStart()));
    return result.get();
  }

  private void seed(EditText editor, String text) {
    runOnMainSync(
        () -> {
          inputMethods.hideSoftInputFromWindow(editor.getWindowToken(), 0);
          editor.setText(text);
          editor.setSelection(text.length());
        });
    SystemClock.sleep(200);
    focus(editor);
    waitForStrip();
  }

  private void checkNewTools(EditText editor) throws Exception {
    seed(editor, "according ");
    press("to");
    check(readText(editor).equals("according to "), "Contextual next-word choice inserts to");
    seed(editor, "abcd");
    Rect space = new Rect();
    waitForKey("Space").getBoundsInScreen(space);
    float density = getTargetContext().getResources().getDisplayMetrics().density;
    swipe(
        space.exactCenterX(),
        space.exactCenterY(),
        space.exactCenterX() - 24 * density,
        space.exactCenterY());
    check(
        selection(editor) == 2 && readText(editor).equals("abcd"),
        "Space swipe moves cursor without adding a space");
    menu("Cursor panel");
    press("←");
    check(selection(editor) == 1, "Cursor panel moves left");
    press("Select all");
    java.util.concurrent.atomic.AtomicInteger end = new java.util.concurrent.atomic.AtomicInteger();
    runOnMainSync(() -> end.set(editor.getSelectionEnd()));
    check(selection(editor) == 0 && end.get() == 4, "Cursor panel selects all text");
    press("Close panel");
    seed(editor, "");
    runOnMainSync(
        () ->
            ((android.content.ClipboardManager)
                    activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE))
                .setPrimaryClip(android.content.ClipData.newPlainText("test", "clipboard test")));
    menu("Clipboard panel");
    press("Paste current");
    check(
        readText(editor).equals("clipboard test"), "Clipboard panel pastes explicit current text");
    check(
        findImeNode("Paste saved clipboard text", true) != null,
        "Clipboard keeps a session history entry");
    press("Clear history");
    check(findImeText("Close panel") == null, "Clear history closes clipboard panel");
    seed(editor, "");
    menu("Float keyboard");
    Rect before = new Rect();
    waitForStrip().getBoundsInScreen(before);
    check(before.width() < 650, "Floating keyboard uses a narrower panel");
    Rect drag = new Rect();
    waitForKey("Drag").getBoundsInScreen(drag);
    results.append("Floating drag bounds: ").append(drag).append('\n');
    screenshot("floating-before.png");
    check(
        before.left >= 0 && before.right <= screenWidth,
        "Floating panel fits the display width: " + before);
    float dx = before.right + 40 <= screenWidth ? 40 : -40;
    float dy = before.top > screenHeight * .55f ? -70 : 70;
    // Android's transient clipboard preview can cover the handle's left side.
    float dragX = drag.left + drag.width() * .85f;
    swipe(dragX, drag.exactCenterY(), dragX + dx, drag.exactCenterY() + dy);
    Rect after = new Rect();
    waitForStrip().getBoundsInScreen(after);
    check(
        Math.abs(after.left - before.left) >= 10 && Math.abs(after.top - before.top) >= 20,
        "Floating handle moves within bounds: " + before + " -> " + after);
    Bitmap floatingFrame = automation.takeScreenshot();
    float navigationLuminance = Color.luminance(floatingFrame.getPixel(3, screenHeight - 30));
    check(
        new Preferences(getTargetContext()).isDark()
            ? navigationLuminance < .25f
            : navigationLuminance > .5f,
        "Floating system controls keep a contrasting backdrop");
    floatingFrame.recycle();
    screenshot("floating.png");
    press("Dock");
    waitForStrip().getBoundsInScreen(after);
    check(after.width() > before.width(), "Dock restores full keyboard width");
    check(findImeNode("Voice input", true) != null, "Ordinary editor exposes voice button");
    results
        .append("Speech provider available: ")
        .append(VoiceInputActivity.available(getTargetContext()))
        .append('\n');
    runOnMainSync(() -> VoiceDraft.set("test dictation"));
    seed(editor, "");
    press("Insert dictation");
    check(
        readText(editor).equals("test dictation") && VoiceDraft.get().isEmpty(),
        "Reviewed dictation inserts once and clears draft");
    seed(editor, "");
    menu("Full");
    Rect eBounds = new Rect();
    waitForKey("E").getBoundsInScreen(eBounds);
    holdAccent(eBounds.exactCenterX(), eBounds.exactCenterY(), "É", false);
    check(readText(editor).equals("É"), "Expanded uppercase accent matches input case");
    check(findImeText("e") != null, "Expanded letters become lowercase after initial capital");
    for (char letter : "cole".toCharArray()) press(String.valueOf(letter));
    press("Space");
    check(
        readText(editor).equals("École "),
        "Later typing preserves an explicitly selected diacritic");
    returnToMini();
    focus(findEditor("Password · no suggestions or learning"));
    waitForKey("q");
    check(findImeNode("Voice input", true) == null, "Private editor hides voice button");
    waitForModeButton().performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
    waitForIdleSync();
    check(findImeText("Clipboard panel") == null, "Private editor hides clipboard history");
    automation.performGlobalAction(
        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
    focus(editor);
    waitForStrip();
  }

  private void checkSystemTheme() throws Exception {
    MaterialAutoCompleteTextView selector = findThemeSelector(activity.getWindow().getDecorView());
    runOnMainSync(() -> selectTheme(selector, 2));
    waitForIdleSync();
    for (boolean dark : new boolean[] {true, false}) {
      shell("cmd uimode night " + (dark ? "yes" : "no"));
      SystemClock.sleep(1000);
      waitForIdleSync();
      focus(findEditor("Type hello world here"));
      AccessibilityNodeInfo strip = waitForStrip();
      Rect bounds = new Rect();
      strip.getBoundsInScreen(bounds);
      Bitmap frame = automation.takeScreenshot();
      int expectedTheme = Color.parseColor(dark ? "#182D30" : "#DFEBE8");
      for (int attempt = 0;
          attempt < 20 && frame.getPixel(bounds.left + 2, bounds.top + 2) != expectedTheme;
          attempt++) {
        frame.recycle();
        SystemClock.sleep(200);
        strip = waitForStrip();
        strip.getBoundsInScreen(bounds);
        frame = automation.takeScreenshot();
      }
      check(
          frame.getPixel(bounds.left + 2, bounds.top + 2)
              == Color.parseColor(dark ? "#182D30" : "#DFEBE8"),
          "System theme follows night mode " + dark);
      frame.recycle();
    }
    MaterialAutoCompleteTextView current = findThemeSelector(activity.getWindow().getDecorView());
    runOnMainSync(() -> selectTheme(current, 1));
    waitForIdleSync();
  }

  private record KeyBox(String label, Rect bounds) {}

  private void auditSetText(EditText editor, String text) {
    runOnMainSync(
        () -> {
          editor.getText().replace(0, editor.length(), text);
          editor.setSelection(text.length());
        });
    waitForIdleSync();
    SystemClock.sleep(150);
  }

  private void auditAllLayouts(EditText editor) throws Exception {
    auditDirectory = "layout-audit-" + (screenWidth < 600 ? "narrow" : "normal");
    new File(getTargetContext().getCacheDir(), auditDirectory).mkdirs();
    runOnMainSync(
        () -> {
          Preferences prefs = new Preferences(getTargetContext());
          prefs.set("theme", auditDark ? 1 : 0);
          prefs.set("caps", false);
        });
    for (int index = 0; index < LetterLayout.NAMES.length; index++) {
      String name = LetterLayout.NAMES[index].toLowerCase(java.util.Locale.ROOT);
      auditProfile(editor, index, 0, false);
      auditState(name + "-compact");
      pressMode();
      auditAlphabet(name + "-full", index, false);
      String[] rows = new LetterLayout(index).rows();
      auditSetText(editor, "");
      String sample =
          "" + rows[0].charAt(0) + rows[1].charAt(rows[1].length() - 1) + rows[2].charAt(0);
      for (char letter : sample.toCharArray()) press(String.valueOf(letter));
      check(readText(editor).equals(sample), name + " alphabet keys emit their displayed letters");
      auditSetText(editor, "");
      press("Emoji menu");
      auditAlphabet(name + "-emoji", index, true);
      press("ABC");
      for (int placement = 1; placement <= 3; placement++) {
        auditProfile(editor, index, placement == 3 ? 0 : placement, placement == 3);
        pressMode();
        auditAlphabet(
            name + (placement == 1 ? "-left" : placement == 2 ? "-right" : "-floating"),
            index,
            false);
      }
    }
    auditProfile(editor, 0, 0, false);
    pressMode();
    pressMode();
    auditState("punctuation");
    auditSymbolInput(editor, false);
    press("=\\<");
    auditState("special-symbols");
    auditSymbolInput(editor, true);
    press("Number pad");
    auditState("number-pad");
    Rect caption = keyBounds("ABC");
    Bitmap captionFrame = automation.takeScreenshot();
    int captionForeground = Color.parseColor(auditDark ? "#F0FAF8" : "#122D2A");
    int endingInk = 0;
    for (int y = caption.top + caption.height() / 4; y < caption.centerY(); y++)
      for (int x = caption.left + caption.width() * 2 / 3; x < caption.right - 2; x++)
        if (captionFrame.getPixel(x, y) == captionForeground) endingInk++;
    captionFrame.recycle();
    check(endingInk > 0, "Number pad ABC caption paints its final C instead of an ellipsis");
    Rect five = keyBounds("5"), zero = keyBounds("0");
    check(
        Math.abs(five.centerX() - zero.centerX()) <= 2,
        "Number pad zero remains centered below five");
    var operators = findImeNode("Number pad operators", true);
    check(operators != null && operators.isScrollable(), "Calculator operators remain scrollable");
    operators.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    waitForIdleSync();
    SystemClock.sleep(250);
    check(
        findImeText("(") != null && findImeText(")") != null,
        "Calculator parentheses remain reachable");
    for (String hint :
        new String[] {
          "Email · literal entry",
          "Password · no suggestions or learning",
          "Number pad",
          "Phone number"
        }) {
      focus(findEditor(hint));
      SystemClock.sleep(200);
      String name =
          hint.startsWith("Email")
              ? "email"
              : hint.startsWith("Password")
                  ? "password"
                  : hint.equals("Phone number") ? "phone" : "numeric-field";
      if (name.equals("email") || name.equals("password")) auditAlphabet(name, 0, false);
      else auditState(name);
      check(findImeNode("Voice input", true) == null, name + " omits voice controls");
      if (name.equals("numeric-field") || name.equals("phone")) {
        press("⋯");
        check(waitForKey("Cursor panel") != null, name + " tools key opens its menu with a tap");
        automation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        for (int attempt = 0; attempt < 30 && findImeText("Cursor panel") != null; attempt++)
          SystemClock.sleep(100);
        waitForIdleSync();
        check(
            findImeText("Cursor panel") == null,
            name + " tools menu dismisses before the next input check");
      }
      if (name.equals("phone")) {
        check(
            countVisibleLabel("#") == 1 && countVisibleLabel("*") == 1,
            "Phone pad has one hash and one star key");
        check(
            findImeText("=") == null && findImeText("!?#") == null,
            "Phone pad omits calculator-only shortcuts");
        check(
            Math.abs(keyBounds("5").centerX() - keyBounds("0").centerX()) <= 2,
            "Phone zero aligns with middle digits");
        for (char character : "+,;()*#".toCharArray()) {
          auditSetText(findEditor(hint), "");
          press(String.valueOf(character));
          check(
              readText(findEditor(hint)).equals(String.valueOf(character)),
              "Phone key inserts " + character);
        }
      }
    }
    try (FileOutputStream output =
        new FileOutputStream(
            new File(getTargetContext().getCacheDir(), auditDirectory + "/manifest.json"))) {
      output.write(auditSnapshots.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    runOnMainSync(
        () -> {
          Preferences prefs = new Preferences(getTargetContext());
          prefs.set("layout", 0);
          prefs.set("position", 0);
          prefs.set("floating", false);
          prefs.set("caps", true);
          prefs.set("theme", 1);
        });
    focus(editor);
  }

  private void auditProfile(EditText editor, int layoutIndex, int position, boolean floating) {
    runOnMainSync(
        () -> {
          Preferences prefs = new Preferences(getTargetContext());
          prefs.set("layout", layoutIndex);
          prefs.set("position", position);
          prefs.set("floating", floating);
          prefs.set("compact", true);
        });
    focus(findEditor("Password · no suggestions or learning"));
    focus(editor);
    SystemClock.sleep(180);
    waitForStrip();
  }

  private void auditAlphabet(String name, int layoutIndex, boolean query) throws Exception {
    auditState(name);
    String[] rows = new LetterLayout(layoutIndex).rows();
    Rect first = keyBounds(rows[0].substring(0, 1));
    Rect last = keyBounds(rows[0].substring(rows[0].length() - 1));
    double cell = (last.right - first.left) / 10.0;
    for (int index = 0; index < rows.length; index++) {
      double expectedStart =
          first.left
              + cell
                  * ((10 - rows[index].length() - (index == 2 ? 3 : 0)) / 2.0
                      + (index == 2 ? 1.5 : 0));
      Rect leading = keyBounds(rows[index].substring(0, 1));
      boolean uniform = Math.abs(leading.left - expectedStart) <= 2;
      for (char letter : rows[index].toCharArray())
        uniform &= Math.abs(keyBounds(String.valueOf(letter)).width() - cell) <= 1.5;
      check(
          uniform, name + " row " + (index + 1) + " uses equal letter cells and centered gutters");
    }
    check(query || findImeNode("Shift.", true) != null, name + " has the expected case controls");
    Bitmap frame = automation.takeScreenshot();
    int foreground = Color.parseColor(auditDark ? "#F0FAF8" : "#122D2A");
    StringBuilder missing = new StringBuilder();
    for (String letters : rows)
      for (char letter : letters.toCharArray()) {
        Rect bounds = keyBounds(String.valueOf(letter));
        int ink = 0;
        for (int y = bounds.top + 2; y < bounds.bottom - 2; y++)
          for (int x = bounds.left + 2; x < bounds.right - 2; x++)
            if (frame.getPixel(x, y) == foreground) ink++;
        if (ink < 7) missing.append(letter);
      }
    frame.recycle();
    check(missing.length() == 0, name + " visibly paints every letter, including W/M: " + missing);
    if (name.endsWith("-floating"))
      check(
          keyBounds("Dock").width()
              >= Math.round(56 * getTargetContext().getResources().getDisplayMetrics().density),
          name + " gives Dock enough label space");
  }

  private void auditSymbolInput(EditText editor, boolean secondary) {
    auditSetText(editor, "");
    StringBuilder expected = new StringBuilder();
    boolean matches = true;
    for (String row : org.linekey.core.SymbolLayout.rows(secondary))
      for (char character : row.toCharArray()) {
        press(String.valueOf(character));
        expected.append(character);
        matches &= readText(editor).equals(expected.toString());
      }
    check(
        matches,
        (secondary ? "Secondary" : "Primary")
            + " symbol keys each insert their displayed character");
    auditSetText(editor, "");
  }

  private int countVisibleLabel(String caption) {
    java.util.List<KeyBox> boxes = new java.util.ArrayList<>();
    for (var window : automation.getWindows())
      if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        collectKeyBoxes(window.getRoot(), boxes);
    return (int) boxes.stream().filter(box -> box.label().equals(caption)).count();
  }

  private void collectKeyBoxes(AccessibilityNodeInfo node, java.util.List<KeyBox> boxes) {
    if (node == null || !node.isVisibleToUser()) return;
    // Operator strips and emoji result grids have their own scrolling geometry.
    if (node.getClassName() != null && node.getClassName().toString().contains("ScrollView"))
      return;
    if (node.isClickable()
        && node.getClassName() != null
        && node.getClassName().toString().contains("Button")) {
      Rect bounds = new Rect();
      node.getBoundsInScreen(bounds);
      int gutter = Math.round(48 * getTargetContext().getResources().getDisplayMetrics().density);
      if (!bounds.isEmpty() && bounds.bottom <= screenHeight - gutter) {
        CharSequence caption =
            node.getText() == null || node.getText().length() == 0
                ? node.getContentDescription()
                : node.getText();
        boxes.add(new KeyBox(caption == null ? "" : caption.toString(), bounds));
      }
    }
    for (int index = 0; index < node.getChildCount(); index++)
      collectKeyBoxes(node.getChild(index), boxes);
  }

  private void auditState(String name) throws Exception {
    waitForIdleSync();
    SystemClock.sleep(180);
    java.util.List<KeyBox> boxes = new java.util.ArrayList<>();
    for (var window : automation.getWindows())
      if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
        collectKeyBoxes(window.getRoot(), boxes);
    boxes.sort(java.util.Comparator.comparingInt(box -> box.bounds().top));
    java.util.List<java.util.List<KeyBox>> rows = new java.util.ArrayList<>();
    for (KeyBox box : boxes) {
      if (rows.isEmpty()
          || box.bounds().centerY() - rows.get(rows.size() - 1).get(0).bounds().centerY()
              > Math.min(box.bounds().height(), rows.get(rows.size() - 1).get(0).bounds().height())
                  / 2) rows.add(new java.util.ArrayList<>());
      rows.get(rows.size() - 1).add(box);
    }
    java.util.List<String> errors = new java.util.ArrayList<>();
    for (var row : rows) {
      row.sort(java.util.Comparator.comparingInt(box -> box.bounds().left));
      Rect anchor = row.get(0).bounds();
      int previousRight = -1;
      for (KeyBox box : row) {
        Rect bounds = box.bounds();
        if (Math.abs(bounds.top - anchor.top) > 1 || Math.abs(bounds.bottom - anchor.bottom) > 1)
          errors.add("uneven row: " + box.label() + " " + bounds + " vs " + anchor);
        if (bounds.left < previousRight - 1 || bounds.left < 0 || bounds.right > screenWidth)
          errors.add("overlap/offscreen: " + box.label() + " " + bounds);
        previousRight = bounds.right;
      }
    }
    screenshot(auditDirectory + "/" + name + ".png");
    org.json.JSONObject snapshot = new org.json.JSONObject();
    snapshot
        .put("name", name)
        .put("width", screenWidth)
        .put("height", screenHeight)
        .put("dark", auditDark)
        .put("keys", boxes.size())
        .put("rows", rows.size())
        .put("top", boxes.isEmpty() ? 0 : boxes.get(0).bounds().top);
    auditSnapshots.put(snapshot);
    check(
        !boxes.isEmpty() && errors.isEmpty(),
        name
            + " aligns all "
            + boxes.size()
            + " controls across "
            + rows.size()
            + " rows: "
            + errors);
  }

  private void selectTheme(MaterialAutoCompleteTextView selector, int index) {
    selector.setText(new String[] {"Light", "Dark", "System"}[index], false);
    selector.getOnItemClickListener().onItemClick(null, selector, index, index);
  }

  private void checkMaterialDocuments() throws Exception {
    check(
        findThemeSelector(activity.getWindow().getDecorView()) != null,
        "Settings use a Material exposed dropdown");
    screenshot("material-settings.png");
    runOnMainSync(() -> ((MainActivity) activity).showLicenses());
    waitForIdleSync();
    AccessibilityNodeInfo item = findAnyText("Keyboard icons", false);
    check(
        item != null && item.performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Material license menu opens a document");
    waitForIdleSync();
    SystemClock.sleep(200);
    AccessibilityNodeInfo document = findAnyText("Keyboard control icons", true);
    check(
        document != null && !document.getText().toString().startsWith("# "),
        "Bundled Markdown heading is rendered without source markers");
    check(
        !document.getText().toString().contains("[Material Design Icons repository]"),
        "Markdown links display their readable labels");
    screenshot("material-licenses.png");
    AccessibilityNodeInfo close = findAnyText("Close", false);
    check(
        close != null && close.performAction(AccessibilityNodeInfo.ACTION_CLICK),
        "Material document dialog closes");
    waitForIdleSync();
  }

  private AccessibilityNodeInfo findAnyText(String text, boolean contains) {
    for (var window : automation.getWindows()) {
      var found = findContainingText(window.getRoot(), text, contains);
      if (found != null && found.isVisibleToUser()) return found;
    }
    return null;
  }

  private AccessibilityNodeInfo findContainingText(
      AccessibilityNodeInfo node, String text, boolean contains) {
    if (node == null) return null;
    if (node.getText() != null
        && (contains
            ? node.getText().toString().contains(text)
            : node.getText().toString().equals(text))) return node;
    for (int index = 0; index < node.getChildCount(); index++) {
      var found = findContainingText(node.getChild(index), text, contains);
      if (found != null) return found;
    }
    return null;
  }

  private Rect keyBounds(String caption) {
    Rect bounds = new Rect();
    waitForKey(caption).getBoundsInScreen(bounds);
    return bounds;
  }

  private void checkReferencePages(EditText editor) throws Exception {
    pressMode();
    pressMode();
    Rect at = keyBounds("@"), slash = keyBounds("/"), one = keyBounds("1");
    check(
        at.top > one.top && at.left < slash.left,
        "Primary symbols put @ at left and slash at right below digits");
    check(
        keyBounds("_").left < keyBounds("&").left && keyBounds("!").left < keyBounds("?").left,
        "Punctuation order matches reference");
    press("=\\<");
    check(
        keyBounds("~").left < keyBounds("√").left && keyBounds("√").left < keyBounds("Δ").left,
        "Secondary math row matches reference");
    check(
        keyBounds("£").left < keyBounds("€").left && keyBounds("€").left < keyBounds("\\").left,
        "Secondary currency and bracket row matches reference");
    press("Number pad");
    Rect five = keyBounds("5"),
        zero = keyBounds("0"),
        three = keyBounds("3"),
        percent = keyBounds("%");
    check(
        Math.abs(five.exactCenterX() - zero.exactCenterX()) < 4,
        "Number pad zero aligns with middle digit column: " + five + " / " + zero);
    check(
        percent.left > three.left && keyBounds("Space").top > percent.top,
        "Number pad right column places percent, Space and Delete");
    var operators = findImeNode("Number pad operators", true);
    check(
        operators != null && operators.isScrollable(), "Number pad operator column is scrollable");
    operators.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    waitForIdleSync();
    SystemClock.sleep(250);
    check(
        findImeText("(") != null && findImeText(")") != null, "Scrolling reveals both parentheses");
    String before = readText(editor);
    press("(");
    check(readText(editor).equals(before + "("), "Scrolled operator inserts into the editor");
    press("⌫");
    check(readText(editor).equals(before), "Number pad Delete preserves prior text");
    screenshot("numpad.png");
    pressMode();
    returnToMini();
  }

  private void checkSpecialFields() throws Exception {
    check(findImeNode("Voice input", true) == null, "Password has no microphone control");
    for (String hint : new String[] {"Email · literal entry", "Number pad", "Phone number"}) {
      focus(findEditor(hint));
      waitForKey(hint.equals("Email · literal entry") ? "q" : "1");
      check(
          findImeNode("Voice input", true) == null, "Specialized field hides microphone: " + hint);
      waitForModeButton().performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
      waitForIdleSync();
      check(findImeText("Voice input") == null, "Specialized field menu hides voice: " + hint);
      automation.performGlobalAction(
          android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
      waitForIdleSync();
    }
    focus(findEditor("Type hello world here"));
    waitForStrip();
  }

  private EditText findEditor(String hint) {
    EditText found = findEditor(activity.getWindow().getDecorView(), hint);
    if (found == null) throw new AssertionError("Missing editor: " + hint);
    return found;
  }

  private EditText findEditor(View view, String hint) {
    if (view instanceof EditText editor && hint.contentEquals(editor.getHint())) return editor;
    if (view instanceof ViewGroup group) {
      for (int i = 0; i < group.getChildCount(); i++) {
        EditText found = findEditor(group.getChildAt(i), hint);
        if (found != null) return found;
      }
    }
    return null;
  }

  private MaterialAutoCompleteTextView findThemeSelector(View view) {
    if (view instanceof MaterialAutoCompleteTextView selector
        && "Keyboard theme".contentEquals(selector.getContentDescription())) return selector;
    if (view instanceof ViewGroup group)
      for (int index = 0; index < group.getChildCount(); index++) {
        var found = findThemeSelector(group.getChildAt(index));
        if (found != null) return found;
      }
    return null;
  }

  private void focus(EditText editor) {
    runOnMainSync(
        () -> {
          editor.requestFocus();
          editor.requestRectangleOnScreen(
              new Rect(0, 0, editor.getWidth(), editor.getHeight()), true);
          inputMethods.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        });
    waitForIdleSync();
  }

  private String readText(EditText editor) {
    AtomicReference<String> text = new AtomicReference<>();
    runOnMainSync(() -> text.set(editor.getText().toString()));
    return text.get();
  }

  private AccessibilityNodeInfo findNode(
      AccessibilityNodeInfo node, String text, boolean description) {
    if (node == null) return null;
    CharSequence label = description ? node.getContentDescription() : node.getText();
    if (label != null
        && (description ? label.toString().startsWith(text) : label.toString().equals(text)))
      return node;
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfo found = findNode(node.getChild(i), text, description);
      if (found != null) return found;
    }
    return null;
  }

  private AccessibilityNodeInfo findImeNode(String text, boolean description) {
    for (AccessibilityWindowInfo window : automation.getWindows()) {
      if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD
          || text.startsWith("Accent ")
          || text.equals("Full")
          || java.util.List.of(
                  "Mini",
                  "Float keyboard",
                  "Dock keyboard",
                  "Cursor panel",
                  "Clipboard panel",
                  "Voice input")
              .contains(text)) {
        AccessibilityNodeInfo found = findNode(window.getRoot(), text, description);
        if (found != null && found.isVisibleToUser()) {
          Rect bounds = new Rect();
          found.getBoundsInScreen(bounds);
          if (!bounds.isEmpty() && bounds.top >= 0 && bounds.bottom <= screenHeight) return found;
        }
      }
    }
    return null;
  }

  private AccessibilityNodeInfo findStrip() {
    return findImeNode("Compact letter strip.", true);
  }

  private AccessibilityNodeInfo findImeText(String text) {
    return findImeNode(text, false);
  }

  private AccessibilityNodeInfo waitForStrip() {
    for (int i = 0; i < 50; i++) {
      AccessibilityNodeInfo strip = findStrip();
      if (strip != null && findImeText("Space") != null) return strip;
      SystemClock.sleep(100);
    }
    for (AccessibilityWindowInfo window : automation.getWindows()) {
      Rect windowBounds = new Rect();
      window.getBoundsInScreen(windowBounds);
      results
          .append("Window type ")
          .append(window.getType())
          .append(" bounds ")
          .append(windowBounds)
          .append('\n');
      if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
        for (String label : new String[] {"Compact letter strip.", "Space"}) {
          AccessibilityNodeInfo node =
              findNode(window.getRoot(), label, label.startsWith("Compact"));
          if (node != null) {
            Rect nodeBounds = new Rect();
            node.getBoundsInScreen(nodeBounds);
            results
                .append(label)
                .append(" bounds ")
                .append(nodeBounds)
                .append(" visible ")
                .append(node.isVisibleToUser())
                .append('\n');
          }
        }
      }
    }
    try {
      screenshot("waiting.png");
    } catch (Exception ignored) {
    }
    throw new AssertionError("Compact strip did not appear");
  }

  private AccessibilityNodeInfo waitForKey(String text) {
    for (int i = 0; i < 50; i++) {
      AccessibilityNodeInfo key =
          text.equals("⌫") ? findImeNode("Delete.", true) : findImeText(text);
      if (key == null) key = findImeNode(text, true);
      if (key != null) return key;
      SystemClock.sleep(100);
    }
    throw new AssertionError("Key did not appear: " + text);
  }

  private void press(String text) {
    AccessibilityNodeInfo target = waitForKey(text);
    while (!target.isClickable() && target.getParent() != null) target = target.getParent();
    if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
      throw new AssertionError("Could not click: " + text);
    }
    waitForIdleSync();
    SystemClock.sleep(150);
  }

  private void cancelTouch() {
    if (automation == null) return;
    long time = SystemClock.uptimeMillis();
    MotionEvent event = MotionEvent.obtain(time, time, MotionEvent.ACTION_CANCEL, 0, 0, 0);
    event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    automation.injectInputEvent(event, true);
    event.recycle();
  }

  private void tap(float x, float y) {
    long time = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
    MotionEvent up = MotionEvent.obtain(time, time + 30, MotionEvent.ACTION_UP, x, y, 0);
    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    automation.injectInputEvent(down, true);
    automation.injectInputEvent(up, true);
    down.recycle();
    up.recycle();
    waitForIdleSync();
    SystemClock.sleep(150);
  }

  private void hold(float x, float y) {
    hold(x, y, x);
  }

  private void swipe(float x, float y, float endX, float endY) {
    long time = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
    MotionEvent move = MotionEvent.obtain(time, time + 40, MotionEvent.ACTION_MOVE, endX, endY, 0);
    MotionEvent up = MotionEvent.obtain(time, time + 80, MotionEvent.ACTION_UP, endX, endY, 0);
    for (MotionEvent event : new MotionEvent[] {down, move, up}) {
      event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
      automation.injectInputEvent(event, true);
      event.recycle();
    }
    waitForIdleSync();
    SystemClock.sleep(150);
  }

  private void hold(float x, float y, float targetX) {
    hold(x, y, targetX, y);
  }

  private void hold(float x, float y, float targetX, float targetY) {
    hold(x, y, targetX, targetY, 650);
  }

  private void hold(float x, float y, float targetX, float targetY, int delay) {
    long time = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    automation.injectInputEvent(down, true);
    SystemClock.sleep(delay);
    MotionEvent move =
        MotionEvent.obtain(
            time, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, targetX, targetY, 0);
    move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    automation.injectInputEvent(move, true);
    move.recycle();
    MotionEvent up =
        MotionEvent.obtain(
            time, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, targetX, targetY, 0);
    up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    automation.injectInputEvent(up, true);
    down.recycle();
    up.recycle();
    waitForIdleSync();
    SystemClock.sleep(150);
  }

  private void holdAccent(float x, float y, String character, boolean cancel) {
    long time = SystemClock.uptimeMillis();
    MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
    down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    automation.injectInputEvent(down, true);
    down.recycle();
    SystemClock.sleep(650);
    try {
      screenshot("accent-popup.png");
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
    AccessibilityNodeInfo accent = findImeNode("Accent " + character, true);
    check(accent != null, "Stationary hold opens accent popup " + character);
    Rect choice = new Rect();
    accent.getBoundsInScreen(choice);
    check(choice.bottom < y, "Accent popup appears above the held letter");
    Rect stable = new Rect();
    if (findStrip() != null) findStrip().getBoundsInScreen(stable);
    MotionEvent move =
        MotionEvent.obtain(
            time,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_MOVE,
            choice.exactCenterX(),
            cancel ? y + 200 : choice.exactCenterY(),
            0);
    move.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    automation.injectInputEvent(move, true);
    move.recycle();
    SystemClock.sleep(100);
    if (!cancel)
      check(
          findImeNode("Accent " + character, true).isSelected(),
          "Dragged accent is highlighted " + character);
    MotionEvent up =
        MotionEvent.obtain(
            time,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            choice.exactCenterX(),
            cancel ? y + 200 : choice.exactCenterY(),
            0);
    up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
    automation.injectInputEvent(up, true);
    up.recycle();
    waitForIdleSync();
    SystemClock.sleep(150);
  }

  private void shell(String command) throws Exception {
    try (android.os.ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
        java.io.FileInputStream input =
            new java.io.FileInputStream(descriptor.getFileDescriptor())) {
      input.readAllBytes();
    }
  }

  private void screenshot(String name) throws Exception {
    Bitmap image = automation.takeScreenshot();
    try (FileOutputStream output =
        new FileOutputStream(new File(getTargetContext().getCacheDir(), name))) {
      image.compress(Bitmap.CompressFormat.PNG, 100, output);
    }
    image.recycle();
  }

  private void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    results.append("PASS: ").append(message).append('\n');
  }
}
