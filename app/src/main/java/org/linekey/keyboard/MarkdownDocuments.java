// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import com.google.android.material.R;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Bundled Markdown is rendered as native styled text, including tables and clickable links. */
final class MarkdownDocuments {
    static TextView render(final Context context, final String name, final String source) {
        final TextView body = new TextView(context);
        TextViewCompat.setTextAppearance(body, R.style.TextAppearance_Material3_BodyMedium);
        body.setTextColor(MaterialColors.getColor(body, R.attr.colorOnSurface));
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
        } else {
            body.setText(source);
        }
        body.setTextIsSelectable(true);
        body.setTag("rendered-document");
        return body;
    }

    static void show(final Context context, final String title, final String name) {
        final String source = loadSource(context, name);
        final ScrollView scroll = new ScrollView(context);
        scroll.addView(render(context, name, source));
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private static String loadSource(final Context context, final String name) {
        try (final InputStream input = context.getAssets().open("licenses/" + name)) {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            while (true) {
                final int count = input.read(buffer);
                if (count == -1) {
                    break;
                }
                bytes.write(buffer, 0, count);
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        } catch (final IOException failure) {
            return "This notice could not be opened.";
        }
    }

    private MarkdownDocuments() {}
}
