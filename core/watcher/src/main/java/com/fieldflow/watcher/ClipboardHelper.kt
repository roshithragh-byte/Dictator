package com.fieldflow.watcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * S2-4: Auto-Copy to Clipboard helper on completion of a voice-note transcription.
 */
object ClipboardHelper {
    suspend fun copyText(context: Context, label: String, text: String) {
        if (text.isBlank()) return
        withContext(Dispatchers.Main.immediate) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }
}
