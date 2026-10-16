// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import org.linekey.core.Accents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Non-focusable popup: the original key owns the complete hold/drag/release gesture. */
final class AccentPopup {
    private final Context context;
    private final KeyboardColors colors;
    private final Consumer<String> commit;
    private final List<Button> keys = new ArrayList<>();
    private PopupWindow window;
    private String choices;
    private int selected;
    private float originY;

    AccentPopup(final Context context, final KeyboardColors colors, final Consumer<String> commit) {
        this.context = context;
        this.colors = colors;
        this.commit = commit;
    }

    void show(
            final View anchor,
            final char letter,
            final boolean uppercase,
            final float fingerX,
            final float fingerY) {
        dismiss();
        choices = Accents.choices(letter, uppercase);
        originY = fingerY;
        final int available = context.getResources().getDisplayMetrics().widthPixels - dp(16);
        final int columns = Math.min(choices.length(), Math.max(1, available / dp(36)));
        final int width = columns * dp(36);
        final int rows = (choices.length() + columns - 1) / columns;

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), dp(4));
        content.setBackground(background(colors.action));
        for (int offset = 0; offset < choices.length(); offset += columns) {
            final LinearLayout row = new LinearLayout(context);
            content.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
            for (int index = offset;
                    index < Math.min(offset + columns, choices.length());
                    index++) {
                final String character = String.valueOf(choices.charAt(index));
                final Button key = new Button(context);
                key.setText(character);
                key.setAllCaps(false);
                key.setTextSize(22);
                key.setTypeface(KeyboardFont.get(context));
                key.setPadding(0, 0, 0, 0);
                key.setMinWidth(0);
                key.setMinimumWidth(0);
                key.setMinHeight(0);
                key.setMinimumHeight(0);
                key.setContentDescription("Accent " + character);
                key.setOnClickListener(
                        (final View view) -> {
                            dismiss();
                            commit.accept(character);
                        });
                keys.add(key);
                row.addView(key, new LinearLayout.LayoutParams(0, -1, 1));
            }
        }
        selected = 0;
        highlight();
        window = new PopupWindow(content, width + dp(8), rows * dp(48) + dp(8));
        window.setClippingEnabled(false);
        window.setFocusable(false);
        window.setTouchable(true);
        window.setElevation(dp(8));
        final int[] position = new int[2];
        anchor.getLocationOnScreen(position);
        final int x =
                Math.max(
                        dp(8),
                        Math.min(available + dp(8) - window.getWidth(), (int) fingerX - dp(18)));
        final int y = Math.max(dp(24), position[1] - window.getHeight() - dp(8));
        // Attached popup coordinates are relative to the IME window, not the display.
        final int[] windowOrigin = new int[2];
        anchor.getRootView().getLocationOnScreen(windowOrigin);
        window.showAtLocation(
                anchor, Gravity.TOP | Gravity.LEFT, x - windowOrigin[0], y - windowOrigin[1]);
    }

    void drag(final float x, final float y) {
        if (window == null) {
            return;
        }
        if (y > originY + dp(64)) {
            selected = -1;
            highlight();
            return;
        }
        double nearest = Double.MAX_VALUE;
        int index = -1;
        for (int i = 0; i < keys.size(); i++) {
            final int[] position = new int[2];
            keys.get(i).getLocationOnScreen(position);
            final Rect bounds =
                    new Rect(
                            position[0],
                            position[1],
                            position[0] + keys.get(i).getWidth(),
                            position[1] + keys.get(i).getHeight());
            final double distance =
                    Math.pow(x - bounds.exactCenterX(), 2) + Math.pow(y - bounds.exactCenterY(), 2);
            if (distance < nearest) {
                nearest = distance;
                index = i;
            }
        }
        if (nearest > Math.pow(dp(100), 2)) {
            index = -1;
        }
        if (selected != index) {
            selected = index;
            highlight();
        }
    }

    void release() {
        final String character = selected < 0 ? null : String.valueOf(choices.charAt(selected));
        dismiss();
        if (character != null) {
            commit.accept(character);
        }
    }

    void dismiss() {
        if (window != null) {
            window.dismiss();
            window = null;
        }
        keys.clear();
    }

    private void highlight() {
        for (int i = 0; i < keys.size(); i++) {
            keys.get(i).setBackground(background(i == selected ? colors.accent : colors.key));
            keys.get(i).setTextColor(i == selected ? colors.accentText : colors.text);
            keys.get(i).setSelected(i == selected);
        }
    }

    private GradientDrawable background(final int color) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private int dp(final int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
