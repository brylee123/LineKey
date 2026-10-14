// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.content.*;
import java.util.*;

/** Explicitly requested clipboard snapshots; no background listener and no disk history. */
final class ClipboardSession {
  private final List<String> history = new ArrayList<>();

  String read(Context context) {
    ClipboardManager manager =
        (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip = manager.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0 || clip.getItemAt(0).getText() == null) return "";
    String text = clip.getItemAt(0).getText().toString();
    boolean sensitive =
        clip.getDescription().getExtras() != null
            && clip.getDescription()
                .getExtras()
                .getBoolean("android.content.extra.IS_SENSITIVE", false);
    if (!sensitive && !text.isEmpty() && text.length() <= 8192) {
      history.remove(text);
      history.add(0, text);
      while (history.size() > 10) history.remove(history.size() - 1);
    }
    return text;
  }

  List<String> history() {
    return new ArrayList<>(history);
  }

  void clear() {
    history.clear();
  }
}
