// SPDX-License-Identifier: Apache-2.0
package org.linekey.keyboard;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.widget.Toast;
import java.util.ArrayList;

/** Delegates microphone handling to the user's installed recognition provider. */
public final class VoiceInputActivity extends Activity {
  static Intent recognitionIntent() {
    return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate a message");
  }

  static boolean available(Context context) {
    return recognitionIntent().resolveActivity(context.getPackageManager()) != null;
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    if (saved != null) return;
    try {
      startActivityForResult(recognitionIntent(), 1);
    } catch (ActivityNotFoundException failure) {
      Toast.makeText(this, "No speech recognition app is installed or enabled.", Toast.LENGTH_LONG)
          .show();
      finish();
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 1 && result == RESULT_OK && data != null) {
      ArrayList<String> alternatives = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      if (alternatives != null && !alternatives.isEmpty()) VoiceDraft.set(alternatives.get(0));
    }
    finish();
  }
}
