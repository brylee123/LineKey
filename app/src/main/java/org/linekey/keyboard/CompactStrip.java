// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import org.linekey.core.LetterLayout;

/** Original compact touch surface. Touch samples are normalized to its own width. */
final class CompactStrip extends View {
  interface Listener {
    void tap(double x);

    void swipe(int direction);

    void precision(double x, boolean secondary);

    void accents(double x, float screenX, float screenY);

    void dragAccents(float screenX, float screenY);

    void finishAccents(boolean cancelled);

    void precise(double x, boolean secondary);
  }

  private final LetterLayout layout;
  private final Listener listener;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final boolean gestures;
  private final float threshold;
  private final int holdDelay;
  private float downX, downY, activeX = -1;
  private boolean held, secondary, movedAfterHold, uppercase, accentHold;
  private long pressedAt;
  private final KeyboardColors colors;
  private float downRawY, downRawX;
  private final Runnable hold;

  CompactStrip(Context context, LetterLayout layout, Listener listener, Preferences prefs) {
    super(context);
    this.layout = layout;
    colors = new KeyboardColors(prefs.isDark());
    this.listener = listener;
    hold =
        () -> {
          held = true;
          secondary = downY < getHeight() * .25f;
          accentHold = !secondary;
          if (secondary) {
            movedAfterHold = true;
            this.listener.precision(normalized(downX), true);
          } else this.listener.accents(normalized(downX), downRawX, downRawY);
        };
    gestures = prefs.bool("gestures", true);
    paint.setTypeface(KeyboardFont.get(context));
    threshold = prefs.number("gesture_distance", 48) * getResources().getDisplayMetrics().density;
    holdDelay = prefs.number("hold_delay", 450);
    setContentDescription(
        "Compact letter strip. Hold for precise letters. Use full mode for accessible individual"
            + " keys.");
    setFocusable(true);
  }

  void setUppercase(boolean uppercase) {
    this.uppercase = uppercase;
    setContentDescription(
        "Compact letter strip. "
            + (uppercase ? "Uppercase" : "Lowercase")
            + " letters. Hold for accents; hold and drag upward for symbols.");
    invalidate();
  }

  private double normalized(float x) {
    return Math.max(0, Math.min(.99999, x / Math.max(1, getWidth())));
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.drawColor(colors.key);
    String letters = layout.compact();
    float cell = (float) getWidth() / letters.length();
    if (activeX >= 0) {
      paint.setColor(Color.parseColor("#247D72"));
      c.drawRect(
          (int) (activeX / cell) * cell,
          0,
          ((int) (activeX / cell) + 1) * cell,
          getHeight(),
          paint);
    }
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTextSize(Math.min(cell * .84f, 20 * getResources().getDisplayMetrics().scaledDensity));
    paint.setColor(colors.text);
    float offset = -(paint.ascent() + paint.descent()) / 2;
    for (int i = 0; i < letters.length(); i++)
      c.drawText(
          String.valueOf(uppercase ? Character.toUpperCase(letters.charAt(i)) : letters.charAt(i)),
          (i + .5f) * cell,
          (float) layout.vertical(letters.charAt(i)) * getHeight() + offset,
          paint);
    paint.setTextSize(9 * getResources().getDisplayMetrics().scaledDensity);
    paint.setColor(colors.muted);
    String secondary = "1234567890@#()!?/";
    for (int i = 0; i < secondary.length(); i++)
      c.drawText(
          String.valueOf(secondary.charAt(i)),
          (i + .5f) * getWidth() / secondary.length(),
          getHeight() * .15f,
          paint);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    switch (e.getActionMasked()) {
      case MotionEvent.ACTION_DOWN -> {
        downX = e.getX();
        downY = e.getY();
        activeX = downX;
        held = false;
        accentHold = false;
        pressedAt = android.os.SystemClock.uptimeMillis();
        movedAfterHold = false;
        secondary = false;
        downRawY = e.getRawY();
        downRawX = e.getRawX();
        postDelayed(hold, holdDelay);
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_MOVE -> {
        activeX = e.getX();
        float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
        boolean moved = Math.hypot(dx, dy) > 12 * getResources().getDisplayMetrics().density;
        if (!held && moved && android.os.SystemClock.uptimeMillis() - pressedAt >= 120) {
          held = true;
          secondary = dy < -16 * getResources().getDisplayMetrics().density;
          movedAfterHold = true;
          removeCallbacks(hold);
        }
        if (held && accentHold) listener.dragAccents(e.getRawX(), e.getRawY());
        else if (held && movedAfterHold) listener.precision(normalized(e.getX()), secondary);
        if (moved) removeCallbacks(hold);
        invalidate();
        return true;
      }
      case MotionEvent.ACTION_UP -> {
        removeCallbacks(hold);
        activeX = -1;
        invalidate();
        if (held) {
          if (accentHold) listener.finishAccents(false);
          else if (movedAfterHold) listener.precise(normalized(e.getX()), secondary);
        } else {
          float dx = e.getX() - downX, dy = e.getY() - downY;
          if (gestures && Math.max(Math.abs(dx), Math.abs(dy)) >= threshold) {
            listener.swipe(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : -1) : (dy < 0 ? 2 : -2));
          } else {
            listener.tap(normalized(e.getX()));
            performClick();
          }
        }
        return true;
      }
      case MotionEvent.ACTION_CANCEL -> {
        listener.finishAccents(true);
        removeCallbacks(hold);
        activeX = -1;
        invalidate();
        return true;
      }
      default -> {
        return true;
      }
    }
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  @Override
  protected void onDetachedFromWindow() {
    removeCallbacks(hold);
    listener.finishAccents(true);
    super.onDetachedFromWindow();
  }
}
