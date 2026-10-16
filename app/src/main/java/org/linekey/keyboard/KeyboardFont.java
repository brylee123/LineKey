// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.content.Context;
import android.graphics.Typeface;

/** Official Google Fonts distribution; bundled locally under SIL OFL 1.1. */
final class KeyboardFont {
    private static Typeface regular;

    static synchronized Typeface get(final Context context) {
        if (regular == null) {
            regular =
                    new Typeface.Builder(context.getAssets(), "fonts/GoogleSans.ttf")
                            .setWeight(400)
                            .setItalic(false)
                            .build();
        }
        return regular;
    }
}
