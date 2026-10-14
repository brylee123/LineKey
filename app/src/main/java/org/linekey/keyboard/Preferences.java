// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 LineKey contributors

package org.linekey.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

final class Preferences {
  final SharedPreferences store;
  private final Context context;

  Preferences(Context context) {
    this.context = context;
    store = context.getSharedPreferences("linekey_settings", Context.MODE_PRIVATE);
    if (!store.contains("theme"))
      store.edit().putInt("theme", store.contains("dark") ? bool("dark", true) ? 1 : 0 : 2).apply();
  }

  boolean isDark() {
    int theme = number("theme", store.contains("dark") ? bool("dark", true) ? 1 : 0 : 2);
    if (theme != 2) return theme == 1;
    android.app.UiModeManager manager =
        (android.app.UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    if (manager != null) {
      if (manager.getNightMode() == android.app.UiModeManager.MODE_NIGHT_YES) return true;
      if (manager.getNightMode() == android.app.UiModeManager.MODE_NIGHT_NO) return false;
    }
    return (android.content.res.Resources.getSystem().getConfiguration().uiMode
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
  }

  boolean bool(String key, boolean fallback) {
    return store.getBoolean(key, fallback);
  }

  int number(String key, int fallback) {
    return store.getInt(key, fallback);
  }

  void set(String key, boolean value) {
    store.edit().putBoolean(key, value).apply();
  }

  void set(String key, int value) {
    store.edit().putInt(key, value).apply();
  }
}
