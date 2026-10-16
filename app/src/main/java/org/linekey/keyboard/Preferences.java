// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.keyboard;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

final class Preferences {
    final SharedPreferences store;
    private final Context context;

    Preferences(final Context context) {
        this.context = context;
        store = context.getSharedPreferences("linekey_settings", Context.MODE_PRIVATE);
        if (!store.contains("theme")) {
            store.edit()
                    .putInt("theme", store.contains("dark") ? bool("dark", true) ? 1 : 0 : 2)
                    .apply();
        }
    }

    boolean isDark() {
        final int theme = number("theme", store.contains("dark") ? bool("dark", true) ? 1 : 0 : 2);
        if (theme != 2) {
            return theme == 1;
        }
        final UiModeManager manager =
                (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (manager != null) {
            if (manager.getNightMode() == UiModeManager.MODE_NIGHT_YES) {
                return true;
            }
            if (manager.getNightMode() == UiModeManager.MODE_NIGHT_NO) {
                return false;
            }
        }
        return (Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    boolean bool(final String key, final boolean fallback) {
        return store.getBoolean(key, fallback);
    }

    int number(final String key, final int fallback) {
        return store.getInt(key, fallback);
    }

    void set(final String key, final boolean value) {
        store.edit().putBoolean(key, value).apply();
    }

    void set(final String key, final int value) {
        store.edit().putInt(key, value).apply();
    }
}
