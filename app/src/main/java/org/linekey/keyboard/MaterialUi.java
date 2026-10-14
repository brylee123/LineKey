// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/** Material components for menus and auxiliary panels, separate from keyboard key styling. */
final class MaterialUi {
  static Context themed(Context context, boolean dark) {
    return new ContextThemeWrapper(context, dark ? R.style.MenuThemeDark : R.style.MenuThemeLight);
  }

  static int dp(Context context, int value) {
    return Math.round(value * context.getResources().getDisplayMetrics().density);
  }

  static LinearLayout column(Context context) {
    LinearLayout column = new LinearLayout(context);
    column.setOrientation(LinearLayout.VERTICAL);
    return column;
  }

  static MaterialCardView card(Context context) {
    MaterialCardView card = new MaterialCardView(context);
    card.setCardBackgroundColor(
        MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainerLow));
    card.setCardElevation(0);
    card.setRadius(dp(context, 16));
    card.setStrokeWidth(0);
    return card;
  }

  static MaterialButton addButton(Context context, String caption, Runnable action) {
    MaterialButton button =
        new MaterialButton(
            context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
    button.setText(caption);
    button.setAllCaps(false);
    button.setTextSize(14);
    button.setMinWidth(0);
    button.setMinimumWidth(0);
    button.setMinHeight(dp(context, 48));
    button.setSingleLine(true);
    button.setEllipsize(android.text.TextUtils.TruncateAt.END);
    button.setPadding(dp(context, 8), 0, dp(context, 8), 0);
    button.setOnClickListener(view -> action.run());
    return button;
  }

  private MaterialUi() {}
}
