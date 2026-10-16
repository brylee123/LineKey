// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

/** Native image control: icon size and weight do not depend on the keyboard text font. */
// Input controls deliberately use the isolated framework KeyboardTheme, not AppCompat widgets.
@SuppressLint("AppCompatCustomView")
final class IconButton extends ImageButton {
    IconButton(final Context context) {
        super(context);
        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setIcon(final int resource, final int color, final boolean capsLock) {
        final Drawable drawable =
                getContext()
                        .getDrawable(capsLock ? R.drawable.ic_control_shift_caps : resource)
                        .mutate();
        drawable.setTint(color);
        setImageDrawable(drawable);
    }
}
