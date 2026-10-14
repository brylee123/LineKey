// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.widget.TextViewCompat;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Bundled Markdown is rendered as native styled text, including tables and clickable links. */
final class MarkdownDocuments {
  static TextView render(Context context, String name, String source) {
    TextView body = new TextView(context);
    TextViewCompat.setTextAppearance(
        body, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
    body.setTextColor(
        MaterialColors.getColor(body, com.google.android.material.R.attr.colorOnSurface));
    body.setPadding(
        MaterialUi.dp(context, 24),
        MaterialUi.dp(context, 8),
        MaterialUi.dp(context, 24),
        MaterialUi.dp(context, 24));
    if (name.endsWith(".md")) {
      Markwon.builder(context)
          .usePlugin(TablePlugin.create(context))
          .build()
          .setMarkdown(body, source);
      body.setMovementMethod(LinkMovementMethod.getInstance());
    } else body.setText(source);
    body.setTextIsSelectable(true);
    body.setTag("rendered-document");
    return body;
  }

  static void show(Context context, String title, String name) {
    String source;
    try (var input = context.getAssets().open("licenses/" + name)) {
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      for (int count; (count = input.read(buffer)) != -1; ) bytes.write(buffer, 0, count);
      source = bytes.toString(StandardCharsets.UTF_8.name());
    } catch (IOException failure) {
      source = "This notice could not be opened.";
    }
    ScrollView scroll = new ScrollView(context);
    scroll.addView(render(context, name, source));
    new MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(scroll)
        .setPositiveButton("Close", null)
        .show();
  }

  private MarkdownDocuments() {}
}
