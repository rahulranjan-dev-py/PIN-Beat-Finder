package com.pinbeatfinder.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pinbeatfinder.R
import java.util.Locale

/**
 * Microphone button that hands the system speech recogniser a prompt and returns the best
 * transcription through [onResult]. Shown only when the phone has a recogniser; the search
 * field then works exactly as if the words had been typed.
 */
@Composable
fun VoiceSearchButton(onResult: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val available = remember(context) { hasSpeechRecognizer(context) }
    if (!available) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onResult)
        }
    }
    val prompt = stringResource(R.string.voice_search_prompt)
    IconButton(
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechTag(locale))
                .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            runCatching { launcher.launch(intent) }.onFailure { Toast.makeText(context, R.string.voice_search_unavailable, Toast.LENGTH_SHORT).show() }
        },
        modifier = modifier,
    ) {
        Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.voice_search), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Village names are Indian whichever UI language is set; ask for Indian English unless the app is in Hindi. */
private fun speechTag(locale: Locale): String = if (locale.language == "hi") "hi-IN" else "en-IN"

@Suppress("DEPRECATION")
private fun hasSpeechRecognizer(context: android.content.Context): Boolean =
    runCatching { context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty() }.getOrDefault(false)
